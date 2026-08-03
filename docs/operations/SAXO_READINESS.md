# Saxo (EU) Broker Readiness Checks

Pick-up guide for promoting the Saxo Bank (EU) broker through environments. Every Saxo feature ships **disabled by default in every profile** — including `%local`/standalone — because the integration is experimental and untested against a live account. The master switch `SAXO_ENABLED=false` keeps both the Saxo broker adapter and the Saxo market-data adapter dark until you walk this runbook; a self-hoster who wants to try the EU broker opts in explicitly with `SAXO_ENABLED=true`. (Historically `%local` defaulted these ON; that was reverted once the WIP state was made explicit.) Nothing here moves real money until the live sign-off step at the end.

Saxo differs from Alpaca in four ways that this runbook exists to manage: **OAuth2 with rotating refresh tokens** (no static API key/secret), **per-account credentials** (each user links their own Saxo account through a consent flow — there is no global broker key), **per-exchange market-data entitlements**, and **EUR settlement** (no fractional shares on equities/ETFs).

> **Operator doc.** Provider names are fine here. They must NOT appear in any user-facing copy (FAQ, UI labels, notification bodies).

---

## Architecture recap

```
Onboarding (user links their Saxo account)
  StartSaxoOnboardingUseCase  → SaxoOAuthOnboardingService builds the authorize URL
                                  (confidential authorization-code flow — client secret, NO PKCE)
                                  → OAuthStateStorePort (single-use state)
  CompleteSaxoOnboardingUseCase → SaxoOAuthClientPort (code → tokens) → CreateBrokerAccountService.createFromOAuth
                                  → BrokerAccount (encrypted SaxoOAuthCredentials, broker_environment = SIM|LIVE)

Runtime (every authenticated Saxo request, broker + market-data)
  Saxo*Adapter → BrokerTokenProviderPort.getValidAccessToken(brokerAccountId)
                   ├─ cached access token still fresh → return it
                   └─ near/at expiry → BrokerTokenLeasePort.acquire (atomic UPDATE-WHERE on broker_accounts)
                        → SaxoOAuthClientPort refresh (grant_type=refresh_token, ROTATES refresh token)
                        → persist rotated refresh token + access_token_expires_at (same save)
                        → BrokerTokenLeasePort.release
                   └─ refresh chain broken → RefreshChainBrokenException
                        → BrokerAccount.markNeedsReauth() (AccountStatus.NEEDS_REAUTH)
                        → NotificationType.BROKER_REAUTH_REQUIRED (IN_APP + EMAIL)

Routing
  BrokerRouter dispatches SAXO_SIM / SAXO_LIVE accounts to @Named("saxoBroker")
  SaxoMarketDataAdapter (@Named("saxo")) prices Saxo EU instruments (InfoPrices live + Charts history)
```

**Key files:**
- `quorumtrading-application/.../port/out/SaxoOAuthClientPort.java` — OAuth exchange/refresh out-port (`exchangeAuthorizationCode`, `refreshAccessToken`, plus the deferred `exchangeCertificate(SaxoEnvironment)` stub)
- `quorumtrading-application/.../service/SaxoOAuthOnboardingService.java` — builds the authorize URL (confidential client, no PKCE), mints/consumes the single-use `state`, drives code→tokens
- `quorumtrading-application/.../port/out/BrokerTokenProviderPort.java` — `getValidAccessToken(brokerAccountId)`, the single seam adapters call
- `quorumtrading-application/.../port/out/BrokerTokenLeasePort.java` — per-account refresh lease (`acquire` / `release` / `forceReleaseStale`)
- `quorumtrading-application/.../port/out/OAuthStateStorePort.java` — single-use OAuth state (CSRF protection — the flow does not use PKCE)
- `quorumtrading-application/.../usecase/saxo/StartSaxoOnboardingUseCase.java` / `CompleteSaxoOnboardingUseCase.java` — onboarding input-ports
- `quorumtrading-application/.../exception/RefreshChainBrokenException.java` — thrown when the rotating chain breaks
- `quorumtrading-model/.../enums/SaxoEnvironment.java` — `SIM` / `LIVE`
- `quorumtrading-model/.../enums/BrokerType.java` — `SAXO_SIM` / `SAXO_LIVE` (+ `isSaxo()`, `isLive()`, `usesOAuth()`, `supportsFractionalShares()`)
- `quorumtrading-model/.../valueobject/SaxoOAuthCredentials.java` — `appKey/appSecret/refreshToken/accessToken?/accessTokenExpiry?/environment` (+ `withRotatedTokens`, `isAccessTokenExpired`)
- `quorumtrading-bootstrap/src/main/resources/application.properties` — "Saxo Bank (EU) Configuration" block
- Hosted deploys: task env + secrets are wired in the private `quorumtrading-infra` repo
- DB: `broker_accounts` columns `broker_environment`, `access_token_expires_at`, `refresh_generation`, `refresh_locked_by`, `refresh_locked_at` (migration `V117__saxo_broker_accounts_oauth_lease.sql`)

---

## 1. Saxo OAuth app registration (SIM vs LIVE)

Each environment needs **its own** Saxo OAuth application. The SIM (developer simulation / paper) app and the LIVE (real-money) app are registered separately and have distinct app key/secret pairs and distinct allowed redirect URIs. They are NOT interchangeable.

1. **Register the SIM app** on the Saxo Developer Portal against the **SIM** gateway. Set the redirect/callback URI to the backend OAuth callback on the target host (e.g. `https://api.quorumtrading.com/api/v1/...` for prod, `http://localhost:8085/api/v1/...` for local — the callback is served by `SaxoOnboardingResource` and defaults to `/api/v1/broker-accounts/saxo/onboard/callback`, configurable via `quorumtrading.broker.saxo.redirect-uri` / `SAXO_REDIRECT_URI`).
2. **Register the LIVE app** against the **LIVE** gateway only when you are ready for the live sign-off step. Use the production callback URI.
3. Record each app's **AppKey** and **AppSecret**. These map to `quorumtrading.broker.saxo.app-key` / `.app-secret` (env `SAXO_APP_KEY` / `SAXO_APP_SECRET`). Store both in the secret manager (section 3).
4. The gateway base URLs are non-sensitive and already defaulted:
   - SIM: `https://gateway.saxobank.com/sim/openapi` (`SAXO_BASE_URL_SIM`)
   - LIVE: `https://gateway.saxobank.com/openapi` (`SAXO_BASE_URL_LIVE`)
   - Both match the documented Saxo OpenAPI gateways ([SAXO_ANSWERS.md](SAXO_ANSWERS.md) §1.5). What still needs reconfirming before live promotion are the **LIVE logon-validation hosts** (`live.logonvalidation.net/{authorize,token}`) — documented best-effort, verify against the LIVE credentials packet.
5. The per-account environment (`broker_environment` = `SIM` | `LIVE`) is selected at onboarding from the account's `BrokerType`; the adapter picks the SIM vs LIVE base URL per account at runtime.

> The app key/secret authorize the **OAuth flow**. They are NOT per-user credentials. Each user's rotating refresh/access token is minted during onboarding and stored encrypted on their `BrokerAccount` row — never in config or secrets.

---

## 2. Application configuration

All Saxo runtime keys live in the **"Saxo Bank (EU) Configuration"** block of
`quorumtrading-bootstrap/src/main/resources/application.properties`. Every value uses a **non-empty placeholder** (empty `${ENV:}` defaults break Quarkus string-config validation).

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `quorumtrading.broker.saxo.enabled` | `SAXO_ENABLED` | `false` (`%local`: `true`) | Master switch — enables the Saxo **broker** adapter |
| `quorumtrading.marketdata.saxo.enabled` | `SAXO_ENABLED` | `false` (`%local`: `true`) | Enables the Saxo **market-data** adapter (toggles with the broker off the same env switch) |
| `quorumtrading.broker.saxo.cert.enabled` | `SAXO_CERT_ENABLED` | `false` | Certificate-grant unattended auth — **deferred**, stays OFF (see section 5) |
| `quorumtrading.broker.saxo.base-url-sim` | `SAXO_BASE_URL_SIM` | `https://gateway.saxobank.com/sim/openapi` | SIM OpenAPI gateway |
| `quorumtrading.broker.saxo.base-url-live` | `SAXO_BASE_URL_LIVE` | `https://gateway.saxobank.com/openapi` | LIVE OpenAPI gateway |
| `quorumtrading.broker.saxo.app-key` | `SAXO_APP_KEY` | `not-configured` | OAuth app key (per-environment) |
| `quorumtrading.broker.saxo.app-secret` | `SAXO_APP_SECRET` | `not-configured` | OAuth app secret (per-environment) |
| `quorumtrading.broker.saxo.authorize-url` (+ `-sim` / `-live`) | `SAXO_AUTHORIZE_URL` (+ `_SIM` / `_LIVE`) | `https://sim.logonvalidation.net/authorize` (LIVE: `live.logonvalidation.net`) | OAuth authorize endpoint — logon-validation host, separate from the gateway |
| `quorumtrading.broker.saxo.token-url` (+ `-sim` / `-live`) | `SAXO_TOKEN_URL` (+ `_SIM` / `_LIVE`) | `https://sim.logonvalidation.net/token` (LIVE: `live.logonvalidation.net`) | OAuth token endpoint — the `saxo-oauth` REST client binds this |
| `quorumtrading.broker.saxo.onboarding.state-ttl-seconds` | `SAXO_ONBOARDING_STATE_TTL_SECONDS` | `600` | How long the single-use onboarding `state` stays valid between begin and callback |
| `quorumtrading.broker.saxo.redirect-uri` | `SAXO_REDIRECT_URI` | local callback on port 8085 | OAuth callback — must exactly match the URI registered on the Saxo app (section 1) |

Market-data base URLs (`quorumtrading.marketdata.saxo.base-url-sim` / `.base-url-live`) and the four REST clients all derive from keys above — you do not set them separately: `quarkus.rest-client.saxo-trading.url`, `quarkus.rest-client.saxo-ref.url` and `quarkus.rest-client.saxo-openapi.url` derive from the shared gateway base URLs, and `quarkus.rest-client.saxo-oauth.url` binds the token URL.

Under the `%test` profile Saxo is forced OFF with valid non-empty stubs (`%test.quorumtrading.broker.saxo.app-key=test-saxo-app-key`, REST client URLs `http://localhost:1`) so the app boots in tests without making live calls.

> **No proactive-refresh scheduler exists.** There is no autonomous token-refresh scheduler in the tree and no flag for one. The refresh lifecycle works entirely on-demand — every authenticated request refreshes lazily through `BrokerTokenProviderPort` when the access token is near expiry. If a proactive scheduler is ever added, ship it disabled by default behind its own flag and do not enable it before the paper-smoke step.

---

## 3. Secrets

The OAuth app key/secret are sensitive. On a self-hosted install, set `SAXO_APP_KEY` /
`SAXO_APP_SECRET` in your environment (e.g. `quorumtrading-backend/.env` or the docker-compose
env) — never commit them. Per-account user tokens are **never** configuration — they live
encrypted (AES-256-GCM, `BROKER_CREDENTIALS_ENCRYPTION_KEY`) on the `broker_accounts` row.

For the hosted deployment, the secret wiring (AWS Secrets Manager → ECS task definition) is
documented in the private `quorumtrading-infra` repo.

---

## 4. Token refresh lifecycle & the HA double-refresh problem

This is the heart of the Saxo difference and the reason for the per-account DB lease.

### What rotates, and why it's fragile

Saxo issues a short-lived **access token (~20 min)** plus a **rotating refresh token**: every `grant_type=refresh_token` call returns a *new* refresh token and invalidates the old one. The access token is cached; when it nears expiry, `BrokerTokenProviderPort.getValidAccessToken(...)` refreshes it and persists the rotated refresh token alongside the new `access_token_expires_at` **in the same save**.

The danger: with multiple ECS tasks behind the load balancer, two tasks can decide to refresh the **same account** at the same moment. The first rotation invalidates the second task's refresh token — the second refresh then fails and the **refresh chain breaks**, locking the user out until they re-authorize.

### The correctness boundary: per-account DB lease

`BrokerTokenLeasePort` is the fix. Before refreshing, a task must `acquire(brokerAccountId, holderId, leaseDuration)` — backed by an **atomic `UPDATE ... WHERE`** on the `broker_accounts` lease columns (`refresh_locked_by` / `refresh_locked_at`, migration `V117`), the same pattern as the existing simulation lock. Exactly one task wins; the loser does NOT refresh and instead waits/retries against the freshly persisted token. `release` runs in a `finally`; `forceReleaseStale(brokerAccountId, maxAge)` recovers a lease orphaned by a crashed task. `refresh_generation` is a monotonic watermark bumped per rotation as a lost-update guard.

This lease is the boundary that makes HA/multi-instance safe. A proactive-refresh scheduler (not built — section 2) would only trigger refresh *earlier*; correctness holds without one because every authenticated request refreshes lazily through the same lease-protected path.

### When the chain breaks

If a refresh fails irrecoverably (expired/invalidated refresh token, e.g. after a long outage that outlived the refresh-token TTL), the provider raises `RefreshChainBrokenException`. The account is moved to `AccountStatus.NEEDS_REAUTH` (`BrokerAccount.markNeedsReauth()`) and a `BROKER_REAUTH_REQUIRED` notification (IN_APP + EMAIL) tells the user to re-link. No silent failures; no automatic retry past the broken chain.

> **Confirmed from Saxo's documentation ([SAXO_ANSWERS.md](SAXO_ANSWERS.md) §1.3–1.4):** the access-token TTL is **~20 min** (`expires_in: 1200`), and `grant_type=refresh_token` **always rotates** — the new refresh token replaces and invalidates the previous one. The refresh-token TTL is documented as **~40 min** (`refresh_token_expires_in: 2400`), but only as an example value. Still to confirm with Saxo: whether 2400 s is the fixed LIVE retail refresh TTL, and the exact LIVE logon-validation host strings (§1.5). A ~40-min refresh TTL does **not** survive a multi-hour outage or deploy gap: an idle account past the window breaks its chain and needs an interactive re-login — treat that as the expected recovery path, not a bug.

---

## 5. Certificate-based unattended auth (deferred)

For the unattended DCA cron on `SAXO_LIVE`, certificate / service-account auth is the belt-and-braces alternative to the rotating-refresh chain. It is **deferred and gated OFF** (`quorumtrading.broker.saxo.cert.enabled=false` / `SAXO_CERT_ENABLED=false`). While off, `SaxoOAuthClientPort.exchangeCertificate(SaxoEnvironment)` throws a clear disabled error and exchanges nothing. Do not enable it until Saxo confirms our app is eligible for cert-based / service-account auth (per [SAXO_ANSWERS.md](SAXO_ANSWERS.md) §1.2, CBA is institutional-only — retail eligibility is not expected). Until then, the rotating-refresh chain + HA lease posture is the accepted production model.

---

## 6. Per-exchange market-data subscription

Saxo prices its own EU instruments (`InfoPrices` live + `Charts` history) so the decision price comes from the execution venue. This requires **per-exchange market-data entitlements on the Saxo account** — they are not automatic.

1. Confirm which exchanges are entitled at launch (e.g. Xetra `XETR`, Euronext Brussels `XBRU`, Euronext Amsterdam `XAMS`) — per-account vs platform-level, and the monthly cost (see [SAXO_ANSWERS.md](SAXO_ANSWERS.md) §3.1).
2. Subscribe the entitlements on the Saxo account before expecting live quotes for those venues. Without the subscription, `InfoPrices` may return delayed or empty data for that exchange.
3. Quotes carry a **delayed** flag in their raw data — surface that, don't treat a delayed quote as real-time.
4. `SaxoMarketDataAdapter` (`@Named("saxo")`) overrides `coversInstrument(Symbol)` to gate on `MarketDataFeature.EU_INSTRUMENT` + the symbol's MIC, so EU-MIC price gaps prefer Saxo while US gaps stay on the existing providers (additive; US routing is byte-identical).
5. **Currency scope:** only `Money.Currency` `{USD, EUR, BTC}` exist. EUR venues are covered; non-EUR European venues (e.g. `XLON`/GBP, `XSWX`/CHF, Nordic) currently fall back to USD and are out of scope until `Money.Currency` is widened. Crypto pricing stays on the existing provider in v1.

---

## 7. Feature-flag flip order (promotion sequence)

Promote Saxo through environments in this exact order. Each step gates the next.

### Step 0 — pre-flight (everything still OFF)

- [ ] SIM OAuth app registered; the SIM key/secret pair configured in the deployment's secret store (hosted wiring: private infra repo)
- [ ] `SAXO_BASE_URL_SIM` / `SAXO_BASE_URL_LIVE` match the documented gateways ([SAXO_ANSWERS.md](SAXO_ANSWERS.md) §1.5); LIVE logon hosts reconfirmed from the LIVE credentials packet
- [ ] CORS allow-list includes the EU web origin `eu.quorumtrading.com` (the current `quarkus.http.cors.origins` does **not** yet list it — confirm it is present before EU users hit the API)
- [ ] `broker_accounts` has the V117 columns on the target DB; migration applied clean
- [ ] App boots with `SAXO_ENABLED=false` (no `AmbiguousResolutionException` — the broker-credential-validation selector and the Saxo validator beans must coexist)

### Step 1 — enable the broker (SIM only)

- [ ] Set `SAXO_ENABLED=true` (both broker + market-data adapters wake). Keep `SAXO_CERT_ENABLED=false`.
- [ ] Onboard a **SIM** account through the OAuth consent flow (`POST` initiate → Saxo consent → callback → `createFromOAuth`). Confirm a `broker_accounts` row with `broker_type='SAXO_SIM'`, `broker_environment='SIM'`, encrypted credentials, and `access_token_expires_at` set after the first refresh.
- [ ] Subscribe the needed exchange entitlements on the SIM account (section 6).

### Step 2 — paper smoke test (SIM)

> ⚠️ **The first two items below are not reachable today, and that is a code gap, not an oversight
> in your setup.** `TickerValidationHelper` refuses exchange-qualified tickers (`ASML:XAMS`,
> `BMW.DE`) unconditionally at strategy creation, whatever broker is attached — so there is no way
> to build a strategy that would trade an EU venue, and therefore no way to run the EU simulation
> this step asks for. Connecting Saxo does not change that. Lifting the refusal is the work that
> has to land before this checklist can be completed; until then Saxo is experimental connectivity
> on self-hosted instances only, and European equity trading is unavailable everywhere. Leave these
> two boxes unticked rather than inventing a way around them.

- [ ] *(blocked — see above)* Resolve an EU instrument through the account-aware `find_ticker` / instrument-search path — confirm it returns `tradableOnBroker=true` with the right MIC + currency (e.g. a Xetra name → `mic=XETR`, `currency=EUR`).
- [ ] *(blocked — see above)* Run a SIM simulation against the SAXO_SIM account: confirm the **EUR** SimulationPortfolio (no same-currency `Money` throw), **whole-share** rounding with cash residue carried (no fractional equities), and a recorded position.
- [ ] Exercise the token lifecycle: let an access token approach expiry and confirm a single lease-protected refresh persists the rotated refresh token + new `access_token_expires_at` (check `refresh_locked_by` clears after release). With two instances, confirm exactly one refreshes.
- [ ] Force a broken chain (e.g. invalidate the refresh token) and confirm the account moves to `NEEDS_REAUTH` and a `BROKER_REAUTH_REQUIRED` notification fires.

### Step 3 — live sign-off (LIVE)

Only after weeks of clean SIM/paper execution:

- [ ] Register the **LIVE** OAuth app; add the LIVE `saxo_app_key` / `saxo_app_secret` pair to Secrets Manager; confirm `SAXO_BASE_URL_LIVE`.
- [ ] Subscribe the required exchange entitlements on the live account.
- [ ] Confirm the server-side live guard: creating a `SAXO_LIVE` account **without** `confirmLive=true` returns `400` (the guard keys off `BrokerType.isLive()` in `CreateBrokerAccountService.requireLiveConfirmation()`, covering `ALPACA_LIVE` too). This guard is enforced in the application layer in **every** profile including `%prod`; there is no "allow live without confirmation" switch.
- [ ] Confirm Saxo has signed off on automated/algo trading for the live account if the DCA scheduler will drive it (vendor question #1 in [SAXO_ANSWERS.md](SAXO_ANSWERS.md)). The DCA scheduler itself stays OFF by default (`quorumtrading.scheduler.investments.enabled=false`) — see [docs/operations/EMAIL_READINESS.md](EMAIL_READINESS.md) and the Go-Live checklist in [CLAUDE.md](../../CLAUDE.md) for the DCA promotion sequence.
- [ ] First live trade is supervised: confirm the order, the recorded position, EUR settlement, and the transaction surfaces in the export. (Tax is broker-side — we surface a non-advisory disclaimer + export only, never compute or file.)

---

## 8. Rollback

If something goes wrong after enabling:

1. **Kill switch:** set `SAXO_ENABLED=false` and redeploy. Both Saxo adapters go dark; existing `broker_accounts` rows are untouched (credentials stay encrypted in place). Alpaca / mock routing is unchanged. No data loss.
2. **Single stuck account:** if one account's chain is broken, leave the rest running — it is already in `NEEDS_REAUTH` and the user is notified to re-link; no global action needed.
3. **Suspected app-secret leak:** rotate `saxo_app_key` / `saxo_app_secret` in Secrets Manager and re-register the OAuth app. All existing per-account refresh tokens minted under the old app become invalid → affected accounts move to `NEEDS_REAUTH` and re-onboard. High blast radius — treat as incident response, not routine.

---

## 9. Test coverage

The Saxo OAuth + lease path is guarded by:

- `OAuthTokensTest` — token value-object roundtrip
- `SaxoOAuthCredentialsTest` / `ApiKeyCredentialsTest` — credential VOs, redacted `toString`, `withRotatedTokens`, `isAccessTokenExpired`
- `SaxoOAuthOnboardingServiceTest` — authorize-URL construction (confidential flow, asserts **no** PKCE parameters), single-use state mint/consume, code→tokens completion
- `BrokerTokenRefreshServiceTest` — fresh→cached, near-expiry→lease+refresh+persist+release, lease-held→no refresh, broken-chain→`markNeedsReauth` + `BROKER_REAUTH_REQUIRED`, batch isolation
- `BrokerTokenLeaseAdapterIntegrationTest` — concurrent acquire: exactly one wins; `forceReleaseStale` works (`quorumtrading_test`)
- Broker WireMock tests (`SaxoBrokerHappyPathWireMockTest`, `SaxoBrokerAuthAccountKeyWireMockTest`, `SaxoBrokerTradeExecutionWireMockTest`, `SaxoBrokerPortfolioBalanceWireMockTest`, `SaxoBrokerTradabilityWireMockTest`) + `SaxoBrokerSizingUnitTest` — auth headers, AccountKey caching, trade execution, whole-share rounding, EUR balance; the gated `SaxoBrokerIntegrationTest` skips without `SAXO_INTEGRATION_TESTS=true`
- Market-data tests (`SaxoMarketDataAdapterWireMockTest`, `SaxoMarketDataErrorMappingWireMockTest`, `SaxoMarketDataDiscoveryTest`) — delayed flag, entitlement/error mapping, `"saxo"` discovery; the gated `SaxoMarketDataIntegrationTest` skips without `SAXO_INTEGRATION_TESTS=true`

There is no test against the real Saxo gateway — that needs credentialed network calls. The paper-smoke steps in section 7 cover that manually.

---

## 9b. Self-host edition hardening

Verified for the headless `docker-compose.local.yml` stack:

- **Boots clean with `SAXO_ENABLED=true` + placeholder app key/secret.** The Saxo broker and
  market-data adapters gate on a *runtime* `@ConfigProperty`, so their beans are always present in
  CDI regardless of the flag — flipping it on adds no beans and raises no `AmbiguousResolutionException`.
  Confirmed by a `local`-profile boot against live Postgres/Redis: "application started successfully",
  zero exceptions, MCP + Saxo market-data adapter up.
- **Headless footer link fixed.** The account-linking result page no longer links to
  `/settings/broker-accounts` (a web-app route that 404s with no UI). New flag
  `quorumtrading.frontend.enabled` (`QT_FRONTEND_ENABLED`, default `true`; set `false` in the local
  stack) switches the success-page footer to agent-centric copy. Covered by `SaxoOnboardingHeadlessTest`.
- **Redis stays mandatory — by decision, not omission.** `RedisOAuthStateStore` is the only
  implementation of `OAuthStateStorePort`; the single-use `state` that secures the anonymous Saxo
  callback lives there, and Redis also backs the event bridge, rate limiting, and caches. An
  in-memory fallback would mean re-implementing and security-reviewing a parallel path for the OAuth
  state store to drop one ~5 MB container that's already in the one-command stack. Not worth it.

---

## 10. Cross-references

- [docs/operations/EMAIL_READINESS.md](EMAIL_READINESS.md) — notification email readiness (BROKER_REAUTH_REQUIRED delivers over both channels)
- `quorumtrading-bootstrap/src/main/resources/application.properties` — "Saxo Bank (EU) Configuration" block
- `quorumtrading-adapter-persistence/.../db/migration/V117__saxo_broker_accounts_oauth_lease.sql` — OAuth/lease columns
</content>
</invoke>
