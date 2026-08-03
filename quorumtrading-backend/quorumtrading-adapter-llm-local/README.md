# QuorumTrading LocalLLM Adapter

Local CLI-based LLM adapter for QuorumTrading trading agents. Uses locally-installed CLI tools (Claude, Qwen, Codex, Gemini) as an alternative to cloud-based LLM providers.

## Overview

The LocalLLM adapter (`LocalLLMAdapter`) implements the `LLMPort` interface using local CLI tools, enabling:
- **Privacy**: All LLM interactions stay on your local machine
- **No API keys**: No cloud API keys or costs required
- **Fallback chain**: Configurable tool fallback (`claude,qwen` by default) with exponential-backoff blacklisting of failing tools
- **Opt-in**: Disabled by default (`quorumtrading.llm.local.enabled=false`); enabled in the self-host local profile

## Architecture

The module follows two design patterns:

### Strategy Pattern (CLI Executors)
Pluggable CLI executors for different tools:
- `ClaudeExecutor` - Executes Anthropic Claude CLI (default)
- `QwenExecutor` - Executes Qwen CLI (first fallback)
- `CodexExecutor` - Executes OpenAI Codex CLI
- `GeminiExecutor` - Executes Google Gemini CLI
- `DummyExecutor` - Returns canned responses from `src/main/resources/dummy-responses/` (E2E tests, demos)

Each executor implements the `CLIExecutor` interface:
```java
public interface CLIExecutor {
    String execute(String prompt) throws CLIExecutionException;
    String getToolName();
    boolean isInstalled();
}
```

### Parser Pattern (Response Parsers)
Tool-specific parsers normalize raw CLI output:
- `ClaudeResponseParser` - JSON parsing, strips metadata, extracts from markdown
- `QwenResponseParser` - JSON parsing, extracts response content
- `CodexResponseParser` - Simple trimming (Codex outputs clean responses)
- `GeminiResponseParser` - JSON parsing, extracts "response" field, strips markdown
- `DummyResponseParser` - Pass-through for canned dummy responses

Each parser implements the `ResponseParser` interface:
```java
public interface ResponseParser {
    String parse(String rawOutput);
    String getToolName();
}
```

## Module Structure

```
quorumtrading-adapter-llm-local/
├── src/main/java/com/quorumtrading/adapter/llm/local/
│   ├── CLIExecutor.java              # CLI executor interface
│   ├── CLIExecutionException.java    # Exception for CLI failures
│   ├── ClaudeExecutor.java           # Claude CLI executor
│   ├── QwenExecutor.java             # Qwen CLI executor
│   ├── CodexExecutor.java            # Codex CLI executor
│   ├── GeminiExecutor.java           # Gemini CLI executor
│   ├── DummyExecutor.java            # Canned-response executor (E2E)
│   ├── ResponseParser.java           # Parser interface
│   ├── ClaudeResponseParser.java     # Claude JSON parsing
│   ├── QwenResponseParser.java       # Qwen response normalization
│   ├── CodexResponseParser.java      # Codex response normalization
│   ├── GeminiResponseParser.java     # Gemini JSON parsing
│   ├── DummyResponseParser.java      # Dummy pass-through parser
│   ├── ToolFailureCache.java         # Exponential-backoff blacklisting
│   ├── FailureRecord.java            # Per-tool failure state
│   └── LocalLLMAdapter.java          # Main adapter (LLMPort impl)
├── src/main/resources/dummy-responses/  # Canned markdown responses for DummyExecutor
└── src/test/java/                     # Parser, adapter, cache, and executor tests
```

## Installation

### Prerequisites
- Java 25+
- Node.js (for npm CLI tools)

### Install CLI Tools

**Claude (Recommended - Default):**
```bash
npm install -g @anthropic-ai/claude-code
claude --version  # Verify installation
```

**Qwen (First fallback):**
```bash
npm install -g @qwen-code/qwen-code
qwen --version  # Verify installation
```

**Codex (Optional - not in the default fallback chain):**
```bash
npm install -g @openai/codex
codex --version
```

**Gemini (Optional - not in the default fallback chain):**
```bash
npm install -g @google/gemini-cli
gemini --version
```

## Configuration

### Basic Configuration

Add to `application.properties` (in `quorumtrading-bootstrap/src/main/resources/`):

```properties
# Enable LocalLLM provider (off by default; the local profile enables it)
quorumtrading.llm.local.enabled=true

# Default tool
quorumtrading.llm.local.default-tool=claude

# Tool fallback chain (codex/gemini deliberately excluded: too slow)
# Supported entries: claude, qwen, codex, gemini, opencode, dummy
quorumtrading.llm.local.tool.fallback=claude,qwen

# opencode is opt-in and not in any shipped chain. It is the one CLI here that speaks
# provider/model natively. See docs/configuration/LLM_CONFIGURATION.md for its settings
# (working-dir isolation, --agent persona, --auto).

# Exponential-backoff blacklisting of failing tools
quorumtrading.llm.local.cache.initial-backoff-minutes=30
quorumtrading.llm.local.cache.max-backoff-minutes=720
```

CLI execution timeout is fixed at 120 seconds per tool.

### Per-Agent Tool Selection

Configure different tools for each agent (optional):

```properties
# Technical analysis uses Qwen
quorumtrading.agents.technical.llm-tool=qwen

# Fundamental analysis uses Claude
quorumtrading.agents.fundamental.llm-tool=claude
```

### Configuration Priority

1. **Per-agent configuration**: `quorumtrading.agents.{agentName}.llm-tool`
2. **Default tool**: `quorumtrading.llm.local.default-tool` (if agent not configured)
3. **Fallback chain**: the requested tool is tried first, then each tool from `quorumtrading.llm.local.tool.fallback` (deduplicated, order preserved)
4. **Blacklisting**: tools that fail are blacklisted with exponential backoff (`ToolFailureCache`); if every tool in the chain is blacklisted, not installed, or fails, the query throws `LLMQueryException` listing the attempted tools and failure reasons

## Usage

### Direct Usage (CDI Injection)

```java
@Inject
LocalLLMAdapter llmAdapter;

public void analyzeMarket() {
    LLMConfig config = new LLMConfig("technical", 0.7, 1000);
    String response = llmAdapter.query("Analyze BTC price trend", config);
    // Response is normalized and clean (metadata stripped)
}
```

### Via Application Layer (Recommended)

```java
@Inject
LLMPort llmPort;  // CDI will inject LocalLLMAdapter when configured

public void runSimulation() {
    // LLM provider selection handled by configuration
    String response = llmPort.query(prompt, config);
}
```

## CLI Tool Output Formats

### Codex
**Command:** `codex exec --skip-git-repo-check --full-auto "<prompt>"`

**Output:** Clean response on stdout, metadata on stderr (discarded)
```
BUY - RSI at 28, oversold signal
```

**Parser:** Simple trim (output already clean)

### Claude
**Command:** `claude -p "<prompt>" --output-format json --dangerously-skip-permissions`

**Output:** Structured JSON with multiple fields
```json
{
  "greeting": "hello",
  "message": "BUY - Strong technical indicators",
  "status": "ready"
}
```

**Parser:** Extracts all fields except metadata (`status`, `ready`), handles markdown code blocks

### Gemini
**Command:** `gemini -m gemini-2.5-flash -p "<prompt>" --output-format json`

**Output:** JSON with response field and stats
```json
{
  "response": "SELL - Overbought conditions detected",
  "stats": {
    "models": {},
    "tokens": {}
  }
}
```

**Parser:** Extracts `response` field, strips markdown wrappers if present

### Qwen
**Command:** `qwen -p "<prompt>"`

**Output:** JSON on stdout which may contain markdown code blocks

**Parser:** Extracts response content, strips markdown wrappers

## Error Handling

### Tool Not Installed

If a tool is not installed, the adapter automatically moves to the next tool in the fallback chain:

```
2025-10-27 10:30:00 WARN  codex not installed, skipping to next tool
```

If every tool in the chain is unavailable:
```
LLMQueryException: All LLM tools exhausted for agent technical. Attempted: [claude, qwen]. Blacklisted: []. Failures: [...]
```

### Timeout

If CLI execution exceeds the 120s timeout:
```
CLIExecutionException: Claude execution timed out after 120 seconds
```

### CLI Exit Errors

If CLI exits with non-zero code:
```
CLIExecutionException: Codex failed with exit code: 1
```

## Testing

### Run Module Tests (Fast - Excludes Executor Tests)

**Default behavior:** Executor tests are excluded from regular builds because they invoke real CLI tools and take 2-3 minutes.

```bash
cd quorumtrading-backend
./mvnw test -pl quorumtrading-adapter-llm-local -am
```

**Expected:** Fast execution (~5 seconds)
- Parser tests (Qwen, Codex, Claude, Gemini) - INCLUDED
- Adapter tests (integration, fallback, tool selection) - INCLUDED
- Executor tests (Qwen, Codex, Claude, Gemini) - **EXCLUDED**

### Run ALL Tests (Including Slow Executor Tests)

To run executor tests that actually invoke CLI tools:

```bash
cd quorumtrading-backend
./mvnw test -pl quorumtrading-adapter-llm-local -am -Pexecutor-tests
```

**Expected:** Slow execution (~2-3 minutes)
- All tests including real CLI invocations
- Requires CLI tools to be installed

### Run Integration Tests

Integration tests mock CLI execution and verify:
- Tool selection based on agent configuration
- Fallback to default tool
- Response parsing and normalization
- Error handling

```bash
./mvnw test -pl quorumtrading-adapter-llm-local -am -Dtest=LocalLLMAdapterTest
```

### Manual Testing

Test with real CLI tools:

```bash
# Start application
cd quorumtrading-backend/quorumtrading-bootstrap
../mvnw quarkus:dev

# Create simulation via REST API
# Monitor logs for LLM queries:
# "LocalLLM query: requestedTool=claude, model=technical, promptLength=250"
# "LocalLLM query successful: tool=claude, responseLength=45"
```

## Troubleshooting

### Problem: "tool not installed" error

**Solution:**
1. Verify tool installation: `which claude` or `which codex` or `which gemini`
2. Check PATH includes npm global binaries:
   ```bash
   echo $PATH | grep npm
   npm config get prefix  # Should be in PATH
   ```
3. Reinstall tool:
   ```bash
   npm uninstall -g @anthropic-ai/claude-code
   npm install -g @anthropic-ai/claude-code
   ```
4. Verify again: `claude --version`

### Problem: Timeout errors

**Solution:**
1. Check if CLI tool is responsive:
   ```bash
   echo "test prompt" | claude -p -
   ```
2. Reduce prompt length if too large

(The 120s per-tool timeout is a compile-time constant in the executors, not configurable.)

### Problem: Parse errors

**Solution:**
1. Check CLI tool output format manually:
   ```bash
   claude -p "test" --output-format json
   ```
2. Verify tool version matches expected format
3. Check logs for raw output before parsing
4. Parser will fall back to raw output if parsing fails (logged as WARNING)

### Problem: "No local LLM tools installed"

**Solution:**
Install at least the default tool (Claude):
```bash
npm install -g @anthropic-ai/claude-code
```

## Performance

- **Startup**: Fast (lazy verification, tools checked only on first use)
- **Execution**: seconds per query (depends on CLI tool and prompt)
- **Timeout**: 120 seconds per tool (fixed)
- **Temp files**: Cleaned up automatically after each execution

## Limitations

1. **No streaming**: Batch responses only (wait for full CLI output)
2. **Model selection is per-tool**: `claude` and `opencode` accept a resolved model (and a reasoning
   effort); `gemini` is still pinned to gemini-2.5-flash and `qwen` takes no model flag
3. **No temperature control**: Tool's default temperature settings
4. **Platform dependent**: Requires the CLI tools installed locally, and the backend must run
   **natively** — these are child processes, so the Docker backend cannot spawn them at all
5. **Usage reporting is per-tool**: only `opencode` reports real token counts; for the others the
   recorded usage is `ZERO`, meaning *unknown* rather than free

## Benefits vs Cloud Providers

### Advantages
- **Privacy**: Data never leaves your machine
- **Cost**: No API usage fees
- **Control**: Full control over LLM environment
- **Development**: Great for local development and testing

### Disadvantages
- **Setup**: Requires CLI tool installation
- **Performance**: CLI overhead per request
- **Models**: Limited to tool's supported models
- **Scalability**: Not suitable for high-throughput production

## License

Copyright (c) 2025 QuorumTrading. All rights reserved.
