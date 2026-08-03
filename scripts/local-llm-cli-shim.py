#!/usr/bin/env python3
"""
local-llm-cli-shim.py — expose host LLM *CLIs* (Claude Code, Codex, Gemini, Qwen) as an
OpenAI-compatible HTTP endpoint, so QuorumTrading's LOCAL_HTTP provider can drive them from
*inside* the Docker container (which cannot invoke a host CLI directly).

Why: the `LOCAL` provider shells out to `claude`/`codex`/… on the machine the backend runs on.
In Docker that machine is the container, which has no CLI and no host auth — so `LOCAL` is
correctly unavailable there. This shim bridges the gap: run it on the HOST, and the container
reaches it over `host.docker.internal` exactly like an Ollama/LM Studio server.

Endpoints (the subset LOCAL_HTTP uses):
  GET  /v1/models            -> the CLIs found on PATH, as OpenAI "models"
  POST /v1/chat/completions  -> runs the mapped CLI with the prompt, returns an OpenAI response

Usage (on the host, NOT in a container):
  python3 scripts/local-llm-cli-shim.py                 # binds 0.0.0.0:11435
Then point the stack at it and restart the backend:
  QT_LLM_LOCALHTTP_URL=http://host.docker.internal:11435/v1
  QT_LLM_LOCALHTTP_MODEL=claude        # or codex / gemini / qwen
  docker compose -f docker-compose.local.yml up -d backend

No third-party dependencies — Python 3.8+ standard library only.

Env knobs: SHIM_HOST (0.0.0.0), SHIM_PORT (11435), SHIM_TIMEOUT (180s),
           SHIM_CLAUDE_MODEL (haiku), SHIM_GEMINI_MODEL (gemini-2.5-flash), SHIM_DEFAULT (first found).
"""

import json
import os
import shutil
import subprocess
import tempfile
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.environ.get("SHIM_HOST", "0.0.0.0")
PORT = int(os.environ.get("SHIM_PORT", "11435"))
TIMEOUT = int(os.environ.get("SHIM_TIMEOUT", "180"))
CLAUDE_MODEL = os.environ.get("SHIM_CLAUDE_MODEL", "haiku")
GEMINI_MODEL = os.environ.get("SHIM_GEMINI_MODEL", "gemini-2.5-flash")

# Coding CLIs default to being a project-aware *agent* (they load CLAUDE.md/GEMINI.md, refuse
# off-task requests, add commentary). For use as a generic inference engine we (a) run them from a
# neutral working dir so no project context is picked up, and (b) replace the system prompt.
CWD = os.environ.get("SHIM_CWD", tempfile.gettempdir())
ENGINE_SYSTEM_PROMPT = os.environ.get(
    "SHIM_SYSTEM_PROMPT",
    "You are a headless text-generation engine. Answer the user's message directly and follow its "
    "output-format instructions exactly (for example, 'respond only with JSON'). Output only what is "
    "requested — no preamble, no commentary, no markdown code fences.")


def _claude_argv(prompt):
    # Mirrors the backend's ClaudeExecutor (print mode, JSON envelope, fast model), plus a neutral
    # system prompt so Claude Code follows the prompt instead of acting as a coding agent.
    return ["claude", "-p", prompt, "--strict-mcp-config", "--output-format", "json",
            "--model", CLAUDE_MODEL, "--system-prompt", ENGINE_SYSTEM_PROMPT]


def _claude_extract(raw):
    # `claude --output-format json` -> {"type":"result","result":"<text>","usage":{...}}
    try:
        obj = json.loads(raw)
        if isinstance(obj, dict) and isinstance(obj.get("result"), str):
            usage = obj.get("usage") or {}
            return obj["result"], {
                "prompt_tokens": int(usage.get("input_tokens", 0) or 0),
                "completion_tokens": int(usage.get("output_tokens", 0) or 0),
            }
    except Exception:
        pass
    return raw.strip(), {}


def _gemini_argv(prompt):
    return ["gemini", "-m", GEMINI_MODEL, "-p", prompt, "--output-format", "json"]


def _gemini_extract(raw):
    # gemini --output-format json -> {"response":"<text>", ...} (best-effort; adjust for your CLI).
    try:
        obj = json.loads(raw)
        if isinstance(obj, dict):
            for key in ("response", "text", "result", "output"):
                if isinstance(obj.get(key), str):
                    return obj[key], {}
    except Exception:
        pass
    return raw.strip(), {}


def _codex_argv(prompt):
    # `codex exec` runs one non-interactive task. read-only sandbox = pure text generation, no file
    # writes (--full-auto is deprecated in newer codex). Tune --model to what your codex auth allows.
    return ["codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "--config",
            'model_reasoning_effort="low"', "--model", "gpt-5", prompt]


def _passthrough(raw):
    return raw.strip(), {}


# name -> {bin, argv(prompt)->[...], extract(stdout)->(text, usage_dict)}
# `bin` is what we probe on PATH; only claude is verified here. codex/gemini/qwen mirror the
# backend's own invocations — tune the *_extract for your CLI's output if the content looks wrong.
CLIS = {
    "claude": {"bin": "claude", "argv": _claude_argv, "extract": _claude_extract},
    "codex":  {"bin": "codex",  "argv": _codex_argv,  "extract": _passthrough},
    "gemini": {"bin": "gemini", "argv": _gemini_argv, "extract": _gemini_extract},
    "qwen":   {"bin": "qwen",   "argv": lambda p: ["qwen", "-p", p], "extract": _passthrough},
}


def available_models():
    return [name for name, spec in CLIS.items() if shutil.which(spec["bin"])]


def resolve_cli(requested):
    """Pick a CLI: exact match, else prefix (e.g. 'claude-haiku' -> claude), else first available."""
    found = available_models()
    if requested:
        r = requested.strip().lower()
        if r in CLIS and CLIS[r]["bin"] in [CLIS[m]["bin"] for m in found]:
            return r
        for name in found:
            if r.startswith(name):
                return name
    default = os.environ.get("SHIM_DEFAULT")
    if default in found:
        return default
    return found[0] if found else None


def build_prompt(messages):
    parts = []
    for m in messages or []:
        content = m.get("content")
        if isinstance(content, list):  # OpenAI array-content form
            content = "".join(p.get("text", "") for p in content if isinstance(p, dict))
        if content:
            role = m.get("role", "user")
            parts.append(content if role == "user" else f"[{role}]\n{content}")
    return "\n\n".join(parts).strip()


def _strip_code_fence(text):
    """If the whole answer is one ```...``` block (common for CLIs), return its inner content."""
    t = text.strip()
    if t.startswith("```"):
        lines = t.split("\n")
        lines = lines[1:]  # drop opening ``` / ```json
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        return "\n".join(lines).strip()
    return t


def run_cli(name, prompt):
    spec = CLIS[name]
    # stdin=DEVNULL so a CLI that also reads stdin (e.g. codex) gets immediate EOF instead of hanging.
    proc = subprocess.run(spec["argv"](prompt), stdin=subprocess.DEVNULL, capture_output=True,
                          text=True, timeout=TIMEOUT, cwd=CWD)
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise RuntimeError(f"{name} exited {proc.returncode}: {err[:500]}")
    text, usage = spec["extract"](proc.stdout)
    text = _strip_code_fence(text)
    if not text:
        raise RuntimeError(f"{name} returned empty output")
    return text, usage


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):  # concise access log (flush so `tail -f` is live)
        print(f"[shim] {self.address_string()} {fmt % args}", flush=True)

    def do_GET(self):
        if self.path.rstrip("/").endswith("/v1/models"):
            data = [{"id": name, "object": "model", "owned_by": "local-cli"}
                    for name in available_models()]
            return self._send(200, {"object": "list", "data": data})
        return self._send(404, {"error": {"message": "not found"}})

    def do_POST(self):
        if not self.path.rstrip("/").endswith("/v1/chat/completions"):
            return self._send(404, {"error": {"message": "not found"}})
        try:
            length = int(self.headers.get("Content-Length", 0))
            req = json.loads(self.rfile.read(length) or b"{}")
        except Exception as e:
            return self._send(400, {"error": {"message": f"bad JSON: {e}"}})

        name = resolve_cli(req.get("model"))
        if not name:
            return self._send(503, {"error": {"message":
                "No supported CLI (claude/codex/gemini/qwen) found on PATH where the shim runs."}})
        prompt = build_prompt(req.get("messages"))
        if not prompt:
            return self._send(400, {"error": {"message": "no prompt in messages"}})

        try:
            text, usage = run_cli(name, prompt)
        except subprocess.TimeoutExpired:
            return self._send(504, {"error": {"message": f"{name} timed out after {TIMEOUT}s"}})
        except Exception as e:
            return self._send(502, {"error": {"message": str(e)}})

        pt = int(usage.get("prompt_tokens", 0) or 0)
        ct = int(usage.get("completion_tokens", 0) or 0)
        return self._send(200, {
            "id": "chatcmpl-shim",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": name,
            "choices": [{"index": 0, "finish_reason": "stop",
                         "message": {"role": "assistant", "content": text}}],
            "usage": {"prompt_tokens": pt, "completion_tokens": ct, "total_tokens": pt + ct},
        })


class ShimServer(ThreadingHTTPServer):
    # Consensus rounds fire many concurrent requests, each held open for the CLI's full runtime
    # (~30s). The socketserver default backlog of 5 makes fresh connects time out under that load
    # (the backend gives up after its 5s connect timeout and records the agent as failed).
    request_queue_size = 128
    daemon_threads = True


def main():
    models = available_models()
    print(f"[shim] local-llm-cli-shim on http://{HOST}:{PORT}  (CLIs on PATH: {models or 'NONE'})", flush=True)
    if not models:
        print("[shim] WARNING: no supported CLI found on PATH — chat completions will 503.", flush=True)
    ShimServer((HOST, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
