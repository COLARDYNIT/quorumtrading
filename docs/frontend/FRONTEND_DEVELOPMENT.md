# Frontend Development Guide

## Technology Stack

- **Framework:** Next.js 16 App Router with TypeScript
- **UI:** Retro-futuristic terminal theme using styled-components with custom v2 primitives (see [Frontend Style Guide](../../quorumtrading-frontend/app/styles/STYLE_GUIDE.md))
- **State:** TanStack Query (API state) + Zustand (client state)
- **Real-time:** Native WebSocket client
- **Testing:** Jest + React Testing Library + Playwright E2E

## Project Structure

```
quorumtrading-frontend/app/
├── components/
│   ├── dashboard/                # WelcomeModal, WelcomeBackModal (onboarding modals)
│   ├── layouts/                  # DashboardLayout, AppHeader, Sidebar, NotificationBell
│   ├── shared/v2/                # UI primitives (Panel, StatusPill, ...) — see Style Guide
│   └── terminal/                 # TerminalPane, TerminalButton (shared UI)
├── dashboard/                    # Dashboard page (authenticated home); panels in components/v2/
├── lib/
│   ├── api/                      # API client functions (TanStack Query hooks)
│   ├── types/                    # TypeScript types (Strategy, Simulation, Portfolio)
│   ├── utils/                    # Utility functions (colors, leaders, chartData)
│   ├── websocket/                # WebSocket client with reconnection logic
│   └── ...                       # plus hooks/, constants/, validation/, test-utils/, ... — list lib/ for the full set
├── providers/
│   └── QueryProvider.tsx         # TanStack QueryClientProvider setup
├── simulations/[id]/             # Dynamic simulation detail route
├── styles/
│   └── theme/                    # Terminal theme (colors, fonts, animations)
├── layout.tsx                    # Root layout with providers
└── page.tsx                      # Runtime-adaptive landing page (LandingV2 or LocalHome)
```

There are many more top-level routes (admin, assistant, benchmarks, blog, challenges, faq, investment-plans, notifications, research, settings, strategies, ...) following the same page/components pattern — list `app/` for the authoritative set.

## API Development Workflow

**CRITICAL:** Before implementing any frontend API integration, you MUST verify the API contract from the backend OpenAPI specification. Never assume API structure — zero assumptions allowed.

Automated enforcement: `npm run validate:api` checks frontend TypeScript types against the live backend's OpenAPI spec; `npm run validate:api:file` checks against the committed spec (and runs as a Jest pretest hook).

The dev backend listens on port **8085** (8080 is the production-container port) — see [docs/RUN_MODES.md](../RUN_MODES.md) for ports, profiles, and databases per run mode.

### Mandatory Workflow

#### 1. Check Backend Availability
```bash
curl -s http://localhost:8085/q/health || echo "Backend not running"
```

#### 2. Fetch OpenAPI Spec

**Primary (backend running):**
```bash
curl -s http://localhost:8085/q/openapi > /tmp/openapi-live.yaml
```

**Fallback (backend down):**
```bash
# Use committed spec
cat quorumtrading-backend/quorumtrading-bootstrap/openapi-spec/openapi.yaml
```

#### 3. Review API Contract
- Identify endpoint paths, HTTP methods, parameters
- Review request DTO schemas (what frontend sends)
- Review response DTO schemas (what frontend receives)
- Note enum values, required fields, nullable fields, types

#### 4. Generate TypeScript Types

**Manually translate OpenAPI `components.schemas` to TypeScript interfaces:**
- Match exact field names, types, and optionality from spec
- Include JSDoc comments referencing OpenAPI operation IDs
- Place in `quorumtrading-frontend/app/lib/types/api.ts` or domain-specific type files

**Example:**

OpenAPI schema:
```yaml
components:
  schemas:
    SimulationResponse:
      type: object
      required: [id, status, progress]
      properties:
        id:
          type: string
          format: uuid
        status:
          type: string
          enum: [PENDING, RUNNING, COMPLETED, FAILED]
        progress:
          type: number
          minimum: 0
          maximum: 100
```

Generated TypeScript:
```typescript
/**
 * Simulation response from GET /api/v1/simulations/{id}
 */
interface SimulationResponse {
  /** Unique simulation identifier */
  id: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  /** Completion percentage (0-100) */
  progress: number;
}
```

#### 5. Implement API Client Code
- Use generated types for all request/response handling, based strictly on the spec
- If endpoint is missing from spec, STOP and ask human

### Error Handling

**Spec/Runtime Mismatch:** If API responses don't match expected types during testing:
1. Re-fetch spec to verify freshness
2. Check backend logs for version/commit
3. Alert human about potential spec staleness

**Missing Endpoint:** If required endpoint not in spec:
1. Stop frontend implementation
2. Ask human: "Endpoint X not in OpenAPI spec. Should I implement backend-first, or is spec outdated?"

**No Spec Available:** If neither live backend nor committed spec is accessible:
1. Block API integration work
2. Request human to start backend or verify committed spec exists

## Testing

### Unit Tests (Jest)
```bash
npm test             # Run all tests
npm run test:watch   # Watch mode
npm run test:coverage # Coverage report
```

- **Component tests:** Jest + React Testing Library
- **Location:** `__tests__/` directories next to components

### E2E Tests (Playwright)
```bash
npm run test:e2e     # Run E2E tests
npm run test:e2e:ui  # Interactive UI mode
```

- Current suites (`quorumtrading-frontend/e2e/`): landing page, dashboard display + navigation, benchmarks display (`backtest.spec.ts`), market research create + execute (`research.spec.ts`), settings, strategy flows, and the core loop (`core-loop.spec.ts`: login → create strategy → run backtest → see results; requires the backend on `QUARKUS_PROFILE=e2e`)
- Check `e2e/` for the authoritative list before claiming coverage

> **Note:** The standalone `/leaderboard` page was removed. The only leaderboard UI today is challenge-scoped: `app/challenges/[id]/components/v2/LeaderboardRail.tsx`.
