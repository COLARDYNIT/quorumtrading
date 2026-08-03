This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load IBM Plex Sans and IBM Plex Mono.

## Configuration

Copy `.env.local.example` to `.env.local`. The defaults point at the dev backend on `http://localhost:8085` (API at `/api/v1`, WebSocket at `/ws/stream`).

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Testing

### Unit Tests (Jest)

- `npm test` (or `npm run test:unit`) - Run Jest unit tests
- `npm run test:watch` - Watch mode
- `npm run test:coverage` - Coverage report

`npm test` first runs `validate:api:file` (a `pretest` hook) to check frontend TypeScript types against the committed backend OpenAPI spec. Run `npm run validate:api` to validate against a live backend instead.

### E2E Tests (Playwright)

E2E tests run against a **real backend**, which must be running first:

```bash
# Terminal 1: Start backend (port 8085)
cd quorumtrading-backend
./mvnw quarkus:dev

# Terminal 2: Run E2E tests (Playwright starts the frontend itself)
cd quorumtrading-frontend
npm run test:e2e
```

- `npm run test:e2e` - Run all E2E tests
- `npm run test:e2e:ui` - Run with Playwright UI (debugger)
- `npm run test:all` - Run unit + E2E tests

The `core-loop` project additionally requires the backend to run with `QUARKUS_PROFILE=e2e` (dummy LLM provider + seeded market data) — see `e2e/core-loop.spec.ts`.

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
