---
name: managing-local-servers
description: Start, stop, restart, and check status of the local QuorumTrading stack — either via Docker Compose (self-host / just trying it out) or the native Quarkus backend + Next.js frontend dev servers (contributor mode). Use when the user asks to start/stop/restart the servers or the stack, or check their status.
argument-hint: [docker|native] <start|stop|restart|status|logs> [backend|frontend|all]
---

# Local Server Management

## Two ways to run the stack — pick the right one

There are **two distinct ways** to run QuorumTrading locally, for two different audiences:

| Mode | Who it's for | How | Live code reload? |
|------|--------------|-----|-------------------|
| **Docker Compose (self-host)** | Someone who just wants to **run or try the stack** — not edit the code | `docker-compose.local.yml` (see next section) | No — rebuild to pick up changes |
| **Native dev servers** | A **developer contributing** to the codebase | `./mvnw quarkus:dev` + `npm run dev` (rest of this doc) | Yes — hot reload |

**If the user's intent is not clear from their request, ASK before doing anything:**

> Are you a developer working on the code (native dev servers, with hot reload), or do you just want to run / try the stack locally (Docker Compose)?

Clues that point to **native**: "dev server", "hot reload", "I'm working on `<file>`", starting a single server, `mvnw`/`npm`. Clues that point to **Docker Compose**: "run the stack", "try it out", "spin it up", "self-host", "docker", wanting all services at once. When the wording fits neither cleanly, ask — don't guess.

## Docker Compose (self-host / trying it out)

Runs the whole stack in containers from `docker-compose.local.yml` (the local/standalone profile): PostgreSQL + Redis + the backend (REST + MCP server) + the Next.js web UI. Run every command **from the repository root**. Requires Docker with the Compose plugin (`docker compose`, v2).

| Service | URL | Notes |
|---------|-----|-------|
| Web UI | http://localhost:3000 | Standalone edition greets you with a local home page |
| Backend + API + MCP | http://localhost:8085 | Health: `/q/health` · Swagger: `/q/swagger-ui` |

PostgreSQL and Redis run inside the compose network and are **not** published to host ports.

**Start / restart the full stack** (rebuild images, then block until healthy):
```bash
docker compose -f docker-compose.local.yml up --build -d --wait
```
- `--build` rebuilds the images — **required after pulling code changes**, otherwise the old image runs.
- `-d` detaches; `--wait` blocks until every container's healthcheck passes, then returns (non-zero if any never becomes healthy).
- The first build is slow (several minutes): a cold Next.js production build plus a full backend Maven build. Don't declare failure early.

**Headless / agent-only** (API + MCP server, no web UI — for MCP-driven users):
```bash
docker compose -f docker-compose.local.yml up --build -d --wait backend
```
Naming the `backend` service pulls in `postgres` + `redis` via `depends_on` and leaves the frontend out.

**Stop** (keeps your data + MCP token — the named volumes survive):
```bash
docker compose -f docker-compose.local.yml down
```
Add `-v` to **also wipe** the Postgres data, Redis, and the provisioned MCP token: `down -v`. Only do this when the user explicitly wants a clean slate.

> **`down -v` invalidates an already-registered MCP server.** The next boot mints a *new* token, so
> any `claude mcp add` you ran earlier now points at a dead one — the failure shows up later as
> confusing 401s, not as an error at wipe time. After a `down -v`, re-read the banner (below) and
> re-register.

**Restart** = `down` then the `up --build … --wait` above. Note: `docker compose … restart` only bounces the existing containers and will **not** pick up compose-file or code changes — after an update, always use `down` + `up --build`.

**Status:**
```bash
docker compose -f docker-compose.local.yml ps
```
Confirm every service shows `Up (healthy)` before declaring the stack ready.

**Logs** (all services, or one — follow with `-f`):
```bash
docker compose -f docker-compose.local.yml logs -f
docker compose -f docker-compose.local.yml logs -f backend
```

**First-boot MCP token:** on first start the backend auto-provisions the local owner and prints a copy-paste `claude mcp add …` banner **in the backend logs** — retrieve it with `logs -f backend` (or `logs backend | grep -A3 'claude mcp add'`). The token persists across restarts via the `qt_local_secrets` volume.

> **Rename the server before pasting that command.** The banner hardcodes the name `quorumtrading`,
> which is also the conventional name for a **production** MCP server — pasting it verbatim
> silently overwrites the prod registration with a localhost endpoint. Register it as
> `quorumtrading-local` instead:
> ```bash
> claude mcp add --transport http quorumtrading-local http://localhost:8085/mcp \
>   --header "Authorization: Bearer <token from the banner>"
> ```

**LLM engine note:** there is still no `claude` binary inside the backend container, so the **local** engine — which shells out to the CLI — only works when the backend runs **natively**. A subprocess cannot cross the container boundary, but HTTP can, so under Docker you have two options on the setup page (`http://localhost:8085/api/v1/llm/setup`):
- **Your Claude subscription** — run [`tools/claude-openai-bridge`](../../../tools/claude-openai-bridge/README.md) on the **host** (`npm start`, serves `:8088`, no dependencies). It exposes the CLI as an OpenAI-compatible endpoint, which the container reaches through `host.docker.internal` exactly like Ollama:
  ```bash
  QT_LLM_LOCALHTTP_URL=http://host.docker.internal:8088/v1 QT_LLM_LOCALHTTP_MODEL=sonnet \
    docker compose -f docker-compose.local.yml up -d backend
  ```
  It binds `127.0.0.1`, which Docker Desktop on macOS/Windows can already reach. On Linux, widen it with `CLAUDE_BRIDGE_HOST=0.0.0.0` **and** set `CLAUDE_BRIDGE_API_KEY`, mirroring the same secret into `QT_LLM_LOCALHTTP_API_KEY` — an open port spends your subscription for anyone who finds it.
- **OpenRouter** — zero setup, paste a key on the setup page; it is stored encrypted.

> **The bridge shares your subscription with the Claude Code you are typing into**, and its calls are recorded as **$0** because the adapter hard-codes free local inference — simulation cost figures will read as free when they are not. It runs 2 CLI processes at a time, so a 3-agent consensus round serialises. For long backtests prefer OpenRouter or a local model server.

### Docker Compose action mapping
- `start` / `restart` → `docker compose -f docker-compose.local.yml up --build -d --wait` (append `backend` for headless)
- `stop` → `docker compose -f docker-compose.local.yml down` (`down -v` only when a full data wipe is requested)
- `status` → `docker compose -f docker-compose.local.yml ps`
- `logs` → `docker compose -f docker-compose.local.yml logs -f [service]`

---

# Native dev servers (contributor mode)

Everything below is for **developers editing the code** — the two servers run directly on the host with hot reload. This is **not** the path for someone who just wants to try the stack; use Docker Compose (above) for that.

## Prerequisite: Postgres + Redis must already be running

Unlike the Docker Compose path, the native servers bring **no datastores with them**. Start them
first, from the repository root:

```bash
docker compose up -d postgres redis
```

This uses the plain `docker-compose.yml` (not `.local.yml`) and gives you `quorumtrading-postgres`
on host port **55432** and `quorumtrading-redis` on **6379** — exactly where the dev profile expects
them (`quarkus.datasource.jdbc.url`, `quarkus.redis.hosts`). The `quorumtrading` database is created
by the image on first run.

Name both services explicitly. A bare `docker compose up -d` also starts Prometheus and Grafana,
and **Grafana publishes on 3001**, which is the port `quorumtrading-frontend/start-local.sh` wants
for the local-profile frontend.

**Do not assume Quarkus Dev Services will cover for you.** They only engage when no datasource URL
is configured, and this repo sets an explicit one — so a missing Postgres is a hard startup failure,
not an auto-provisioned container.

Confirm before starting anything else:
```bash
docker compose ps postgres redis   # both should read Up (healthy)
```

## Server Definitions

| Server   | Port | Working Directory                          | Start Command                                   | Log File               | Health Check URL                    |
|----------|------|--------------------------------------------|------------------------------------------------|------------------------|-------------------------------------|
| Backend  | 8085 | `quorumtrading-backend/`                   | `./mvnw quarkus:dev`                           | `/tmp/quarkus-dev.log` | `http://localhost:8085/q/health`    |
| Frontend | 3000 | `quorumtrading-frontend/`                  | `npm run dev`                                  | `/tmp/nextjs-dev.log`  | `http://localhost:3000`             |

All paths are relative to the repository root; run the start commands from there.

Note: the log files above capture Maven/console startup output (created by the `nohup` redirect below). The backend application additionally writes its own rotating log via Quarkus: `/tmp/quorumtrading-dev.log` (dev profile), `/tmp/quorumtrading-local.log` (local profile). Check the nohup file for startup/build failures and the application log for runtime behavior.

## Actions

### `start [backend|frontend|all]`

Default target is `all`. Whenever the target includes the backend, **first** ensure the datastores
are up (see [Prerequisite](#prerequisite-postgres--redis-must-already-be-running)) — one command,
idempotent, and it costs nothing when they are already running:

```bash
docker compose up -d postgres redis
```

Then, for each server:

1. **Check for port conflicts** before doing anything:
   ```bash
   lsof -ti:PORT
   ```
   If a process is already on the port, report it and ask the user whether to kill it or abort.

2. **Remove stale log file**:
   ```bash
   rm -f LOG_FILE
   ```

3. **Start the server** using `nohup` with log capture:

   **Backend** (from the repository root):
   ```bash
   cd quorumtrading-backend && nohup ./mvnw quarkus:dev </dev/null > /tmp/quarkus-dev.log 2>&1 &
   ```
   The `</dev/null` is **mandatory** — without it, Quarkus dev mode tries to read stdin and hangs.

   **Frontend** (from the repository root):
   ```bash
   cd quorumtrading-frontend && nohup npm run dev </dev/null > /tmp/nextjs-dev.log 2>&1 &
   ```

4. **Poll the health endpoint** until the server is ready:

   **Backend** — wait up to **120 seconds** (Flyway migrations can be slow on first start):
   ```bash
   for i in $(seq 1 60); do
     HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8085/q/health 2>/dev/null)
     if echo "$HTTP_CODE" | grep -qE '^(200|401|404)$'; then
       echo "Backend ready (HTTP $HTTP_CODE) after $((i * 2))s"
       break
     fi
     if [ "$i" -eq 60 ]; then
       echo "Backend failed to start within 120s. Check /tmp/quarkus-dev.log"
       echo "First thing to rule out: docker compose ps postgres redis"
     fi
     sleep 2
   done
   ```

   **A backend that never comes up is far more often a missing datastore than slow Flyway.** The two
   look identical from outside — both are just a port that never answers — so check
   `docker compose ps postgres redis` before spending 120 seconds waiting. In the log, the tell is a
   `Connection refused` to `localhost:55432`.

   **Frontend** — wait up to **30 seconds**:
   ```bash
   for i in $(seq 1 15); do
     HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:3000 2>/dev/null)
     if echo "$HTTP_CODE" | grep -qE '^(200|401|404|308)$'; then
       echo "Frontend ready (HTTP $HTTP_CODE) after $((i * 2))s"
       break
     fi
     if [ "$i" -eq 15 ]; then
       echo "Frontend failed to start within 30s. Check /tmp/nextjs-dev.log"
     fi
     sleep 2
   done
   ```

5. **Report result** — confirm each server is actually responding, or show the tail of the log file if startup failed.

When starting `all`, start the backend first since the frontend may depend on it for API calls.

### `stop [backend|frontend|all]`

Default target is `all`. For each server:

1. **Find the process** on the port:
   ```bash
   PID=$(lsof -ti:PORT)
   ```
   If no PID, report the server is already stopped.

2. **Graceful kill**:
   ```bash
   kill $PID
   ```

3. **Wait up to 5 seconds** for the process to exit:
   ```bash
   for i in $(seq 1 5); do
     if ! kill -0 $PID 2>/dev/null; then
       echo "Server stopped"
       break
     fi
     sleep 1
   done
   ```

4. **Force kill** if still alive:
   ```bash
   if kill -0 $PID 2>/dev/null; then
     kill -9 $PID
     echo "Server force-killed"
   fi
   ```

5. **Confirm** the port is free:
   ```bash
   lsof -ti:PORT
   ```

### `restart [backend|frontend|all]`

Run `stop` then `start` for the specified target(s).

### `status`

For each server (backend on 8085, frontend on 3000):

1. **Check if a process is listening**:
   ```bash
   PID=$(lsof -ti:PORT)
   ```

2. **If running**, show:
   - PID
   - Process uptime: `ps -o etime= -p $PID`
   - Health check result: `curl -s -o /dev/null -w '%{http_code}' HEALTH_URL 2>/dev/null`
   - Last 5 lines of the log file

3. **If not running**, report it as stopped.

### `logs [backend|frontend] [N]`

Show the last N lines (default 30) of the server's log file:

```bash
tail -N LOG_FILE
```

If no server is specified, show logs for both (backend first, then frontend), separated by a header line.

## Critical Rules

1. **Postgres and Redis must be up before the backend.** `docker compose up -d postgres redis`.
   There are no Dev Services fallbacks here — skipping this produces a startup failure that
   masquerades as a slow one.
2. **Always check for port conflicts before starting.** Never blindly start a server — a zombie process on the same port will cause a silent failure.
3. **Backend MUST use `</dev/null`** to prevent Quarkus dev mode from trying to read stdin, which causes it to hang indefinitely.
4. **Always verify the server is actually responding** via the health check before declaring it started. A running process does not mean a healthy server.
5. **Use `nohup` + log files.** Do NOT use background Bash tasks (`run_in_background`) — they lose output and cannot be reliably monitored.
6. **Backend startup can take up to 120 seconds** due to Flyway migrations and Quarkus class scanning. Do not declare failure too early.
7. **Frontend startup is typically under 15 seconds** but allow up to 30 seconds to be safe.
8. **When starting `all`**, start backend first, then frontend. When stopping `all`, stop frontend first, then backend.
9. **If a health check fails after max attempts**, always show the tail of the log file so the user can diagnose the issue.
