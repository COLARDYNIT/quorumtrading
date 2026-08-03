# Saxo OpenAPI — researched answers to the integration questions

> Researched answers to our Saxo integration question list. These answers were mined from Saxo's
> **developer documentation** (`developer.saxo`), the **OpenAPI support centre** (`openapi.help.saxo`),
> Saxo's **client help / commercial** sites (`help.saxo`, `home.saxo`), and — for the Belgian tax
> framework — Saxo Belgium pages plus reputable regulatory sources. Researched **2026-06-06**.
>
> **Reading guide:** ✅ = answered from Saxo docs with a source. ⚠️ = partially answered / indicative /
> needs vendor confirmation. ❓ = docs silent, must ask Saxo or probe empirically. Items still needing
> a vendor reply are collected at the bottom — those are what to actually send to Saxo.

---

## Headline finding that changes a design assumption

**Saxo SIM does not serve market data for equities/ETFs — only Forex.** The original question list was framed
"start on SIM before LIVE", but for *price/quote/chart* work that path does not exist on SIM:

> "certain specific functionality such as reporting and **market data are not available in SIM**."
> — [developer.saxo/openapi/learn/environments](https://www.developer.saxo/openapi/learn/environments)
> "Market data is not available on the Simulation environment… live prices are not offered on the
> simulation environment." — [openapi.help.saxo — How do I enable market data](https://openapi.help.saxo/hc/en-us/articles/4418427366289-How-do-I-enable-market-data)

SIM demo accounts receive **only Forex prices**; stocks/ETFs/CFDs return `NoAccess`. **Order placement,
positions and balances can be exercised on SIM, but live/delayed equity quotes and charts
cannot.** Plan the equity market-data path against **LIVE** from the start. This is the single most
important item to internalise before building the live-quote and historical-backfill paths.

---

## §1 Authentication, automation & token lifecycle

### 1.1 Automated/unattended trading — permitted on retail? ✅ (with caveat)
Yes, in principle — Saxo markets OpenAPI for "fully automated trading strategy" and the standard retail
app type is the **OAuth2 Authorization Code Grant** (confidential client, `client_secret`). The hard
caveat: **fully unattended, no-human-ever auth is not available to retail** (see 1.2). An initial
interactive login is always required, and the refresh chain is short (1.4), so a human re-login is
needed periodically.
- [developer.saxo/openapi/learn/security](https://www.developer.saxo/openapi/learn/security)
- [home.saxo/platforms/api](https://www.home.saxo/platforms/api)
- ⚠️ The formal Developer Terms / API licence text (any contractual restriction on retail algo trading)
  is a click-through at app registration and was not retrievable publicly — **confirm with Saxo.**

### 1.2 Certificate / service-account auth — eligible? ✅ NO
**Retail clients cannot use Certificate-Based Authentication (CBA).** Verbatim:
> "Saxo Bank only offers Certificate-Based Authentication to institutional partners."
> "Individual/Corporate direct retail clients … cannot request CBA access to their accounts."
> Reason: MiFID disclaimers must be shown at login and "cannot be automated, requiring manual
> authentication for retail clients."
> — [openapi.help.saxo — Can CBA be enabled for my app?](https://openapi.help.saxo/hc/en-us/articles/4416637197457-Can-Certificate-Based-Authentication-CBA-be-enabled-for-my-app-)

**Implication: the `SAXO_CERT_ENABLED` path is dead for a retail account.** We must rely on the rotating
refresh-token OAuth model + a resilient always-on refresher. CBA mechanics (institutional only) are
documented at [oauth-certificate-based-authentication](https://www.developer.saxo/openapi/learn/oauth-certificate-based-authentication).

### 1.3 Access-token TTL ✅ ~20 min (1200 s)
Token response shows `"expires_in": 1200`; support docs confirm "expires after 20 minutes."
- [oauth-authorization-code-grant](https://www.developer.saxo/openapi/learn/oauth-authorization-code-grant)
- [openapi.help.saxo — 24H token](https://openapi.help.saxo/hc/en-us/articles/4416637029649-How-do-I-get-an-access-token-that-lasts-longer-than-the-24H-token)

### 1.4 Refresh-token TTL & rotation ✅ / ⚠️
- **(a) TTL:** documented example `"refresh_token_expires_in": 2400` → **40 minutes**. ⚠️ Presented as an
  example value — confirm it is the fixed production value for a LIVE retail app.
- **(b) Rotation:** **Always rotates.** "Every time you use a refresh token … you also get a new refresh
  token, this … replaces and invalidates the previous refresh token." → single-use; persist the newest
  token atomically after every refresh. — [security](https://www.developer.saxo/openapi/learn/security)
- **(c) Survive a multi-hour gap?** **No.** With a ~40-min refresh TTL, idle longer than that breaks the
  chain and forces a fresh interactive login. There is no long-lived retail refresh token; CBA (which
  would avoid this) is institutional-only.
- **SIM-only shortcut:** the 24-hour developer-portal token works on **SIM only**: "One-day tokens …
  are only authorized for the simulation environment." — [environments](https://www.developer.saxo/openapi/learn/environments)

**Architecture consequence:** the per-account refresh lease / token-refresh scheduler must keep refreshing
inside the ~40-min window, atomically persist the rotating token, and treat any multi-hour outage
(deploy/rollback) as requiring a human re-login. HA story must assume re-login is possible, not avoidable.

### 1.5 Token & auth endpoints + client-auth method ✅ / ⚠️
| | SIM | LIVE |
|---|---|---|
| Authorize | `https://sim.logonvalidation.net/authorize` | `https://live.logonvalidation.net/authorize` ⚠️ |
| Token | `https://sim.logonvalidation.net/token` | `https://live.logonvalidation.net/token` ⚠️ |
| REST gateway | `https://gateway.saxobank.com/sim/openapi` | `https://gateway.saxobank.com/openapi` |
| Streaming WS | `https://sim-streaming.saxobank.com/sim/oapi/streaming/ws` | `https://live-streaming.saxobank.com/oapi/streaming/ws` |

- SIM hosts quoted directly; LIVE logon hosts follow the documented same-pattern (`live.`) but were not
  quoted verbatim on a fetched page — ⚠️ confirm exact LIVE strings from the LIVE credentials packet.
- "The application key and secret are **not shared** between simulation and live."
- **Client auth = confidential client with `client_secret` via HTTP Basic Auth** ("authenticated using
  HTTP Basic Auth with your client_id as username and client_secret as password"). **PKCE is a separate
  native-app variant — not used for our server-side flow.**
- [oauth-authorization-code-grant](https://www.developer.saxo/openapi/learn/oauth-authorization-code-grant),
  [environments](https://www.developer.saxo/openapi/learn/environments)

### 1.6 Rate limits ✅
- App-wide: **10,000,000 req/day**. Per session per service group: **120 req/min**. Orders: **1 order/sec**.
- Exceeding → **HTTP 429** with `X-RateLimit-<dimension>-{Limit|Remaining|Reset}` headers
  (`AppDay`, `Session`, `SessionOrders`; `Reset` = seconds to quota reset).
- **Duplicate-order protection:** identical order on the same account within a rolling **15-second** window
  is rejected; send a distinct `x-request-id` per request to intentionally repeat.
- [developer.saxo/openapi/learn/rate-limiting](https://www.developer.saxo/openapi/learn/rate-limiting)
- For our DCA executor the binding limits are 120/min per service group (polling) and 1 order/sec — both
  comfortable. Read `X-RateLimit-*-Remaining`/`-Reset` and back off before 429.

---

## §2 Instruments, fractional shares & order model

### 2.1 Fractional shares ✅ Confirmed: equities/ETFs are NOT fractional
- "Saxo currently offers fractional trading on all Crypto FX pairs and all CFD Indices (except DEN25)."
  Stocks/ETFs are not eligible. — [help.saxo — fractional trading](https://www.help.saxo/hc/en-us/articles/4413408055953-Does-Saxo-offer-fractional-trading)
- Notional/cash ordering (`AmountType=CashAmount`) is "**Currently only supported on MutualFunds**." → no
  "buy €500 of this stock" path. — [POST /trade/v2/orders](https://www.developer.saxo/openapi/referencedocs/trade/v2/orders/post__trade)
- **Confirms our whole-share sizing + residual-cash design.**

### 2.2 Lot size / minimum order ✅ Not always 1 — read per instrument
The instrument-details endpoint exposes per-listing constraints; do **not** assume 1 share:
`MinimumTradeSize`, `LotSize`, `MinimumOrderValue` ("Value of trade (Amount × Price) must be ≥"),
`TickSize`, `AmountDecimals` (0 for non-fractional equities), `OrderDistances`.
- [GET /ref/v1/instruments/details/{Uic}/{AssetType}](https://www.developer.saxo/openapi/referencedocs/ref/v1/instruments/get__ref__details_uic_assettype)
- Saxo gives no venue-specific hard-coded numbers — read the real values per Uic at resolution time and
  round/validate against them in `SaxoUicResolver`/sizing.

### 2.3 Instrument identity (Uic + AssetType) ✅
An instrument = the **(Uic, AssetType)** pair.
- Search `GET /ref/v1/instruments` returns: `Identifier` (the numeric Uic), `AssetType`, `Symbol`,
  `Description`, `ExchangeId`, `PrimaryListing`, `TradableAs`, `GroupId`.
- Details returns: `Uic`, `AssetType`, `Symbol` (format `TICKER:exchangecode`, e.g. `RHM:xetr`),
  `CurrencyCode` (ISO), `Description`, `Exchange`/`ExchangeSummary`, `IsTradable`, `PrimaryListing`, plus
  the 2.2 trading constraints and `SupportedOrderTypes`.
- ⚠️ Saxo publishes **no fixed Uic** for any name (Rheinmetall included) — resolve at runtime via
  `Keywords=Rheinmetall&AssetTypes=Stock&ExchangeId=XETR`. The schema is stable; the Uic is looked up live.
- [instruments search](https://www.developer.saxo/openapi/referencedocs/ref/v1/instruments/get__ref),
  [instrument details](https://www.developer.saxo/openapi/referencedocs/ref/v1/instruments/get__ref__details_uic_assettype)

### 2.4 ETF AssetType ✅ Distinct `Etf` (not `Stock`) — but registration-dependent
Saxo split the legacy `Stock` type into `Stock`, `Etf`, `Etc`, `Etn`, `Fund`, `Rights` (each with CFD/SRD
variants). Example: `IWDG:xlon` → `Etf`. — [extended-assettypes](https://www.developer.saxo/openapi/learn/extended-assettypes)
- ⚠️ **Whether our app sees `Etf` depends on it being registered for Extended AssetTypes.** New OpenAPI
  apps get it by default; legacy apps may still receive ETFs as `Stock`. **Confirm our SIM app's
  registration with Saxo** — it changes the AssetType mapping in code.

### 2.5 Multi-listing disambiguation ✅
Pin the venue with `ExchangeId` at search time; identify the primary venue via `PrimaryListing` (= "the
uic of the primary listing"). Selection is deterministic on `(Uic, AssetType, ExchangeId)`.
- ⚠️ Summaries expose Saxo's `ExchangeId` code (e.g. `XETR`, `XBRU`), not a guaranteed ISO MIC field — map
  via `/ref/v1/exchanges` (not verified here) or confirm with Saxo.
- [instruments search](https://www.developer.saxo/openapi/referencedocs/ref/v1/instruments/get__ref)

### 2.6 UCITS / Irish-domiciled ETF availability ✅
- UCITS ETFs are tradable; **US-domiciled ETFs are blocked for EU retail** (missing PRIIPs KID):
  "Saxo is not able to facilitate trading in instruments with missing KIDs for non-professional
  investors." — [help.saxo — Why can't I trade certain ETFs](https://www.help.saxo/hc/en-us/articles/360048661252-Why-can-t-I-trade-certain-instruments-such-as-ETFs)
- Operationally Saxo runs a KID check and rejects at order time; rely on the per-user **`IsTradable`** flag
  rather than hard-coding domicile rules. ⚠️ There is no documented queryable "UCITS-eligible"/KID field
  to pre-filter before submission — confirm whether one exists.

### 2.7 Order placement shape ✅
`POST /trade/v2/orders`. Market equity order required fields: `Uic`, `AssetType`, `Amount` (share count),
`BuySell`, `OrderType=Market`, `OrderDuration` (e.g. `{DurationType: DayOrder}`), `AccountKey`, and
`ManualOrder` (documented as becoming mandatory — send `false` for an automated app). `AmountType`
defaults to `Quantity`; `CashAmount` is **MutualFunds only** → omit for equities/ETFs.
```json
{ "AccountKey": "<key>", "Uic": 123456, "AssetType": "Stock", "BuySell": "Buy",
  "Amount": 3, "OrderType": "Market", "ManualOrder": false,
  "OrderDuration": { "DurationType": "DayOrder" } }
```
Response: `OrderId`, `Orders[]`, `ExternalReference` (our correlation id, echoed), `ErrorInfo` on failure.
⚠️ Allowed durations are per-instrument (`SupportedOrderTypes` in details) — check per Uic.
- [order-placement](https://www.developer.saxo/openapi/learn/order-placement),
  [POST /trade/v2/orders](https://www.developer.saxo/openapi/referencedocs/trade/v2/orders/post__trade),
  [return codes](https://www.developer.saxo/openapi/learn/order-placement-return-codes)
- **Gates the trade-execution work.**

### 2.8 Account/position endpoints ✅ / ⚠️
Flow: `GET /port/v1/users/me` → `ClientKey` (+ `UserKey`); `GET /port/v1/accounts/me` → `AccountKey`
(opaque, "an encryption of the account id"), `AccountId`, `Currency` (pick the EUR account); then
`GET /port/v1/balances?ClientKey=…&AccountKey=…` for cash and `GET /port/v1/positions` for open positions
(`Uic`, `AssetType`, `Amount`, `OpenPrice` = cost basis, `CurrentPrice`, `ProfitLossOnTrade`, `Exposure`).
- [account-details](https://www.developer.saxo/openapi/learn/account-details),
  [orders-and-positions](https://www.developer.saxo/openapi/learn/orders-and-positions),
  [balances ref](https://www.developer.saxo/openapi/referencedocs/port/v1/balances),
  [positions ref](https://www.developer.saxo/openapi/referencedocs/port/v1/positions)
- ⚠️ Exact balance field names (`CashBalance` vs `CashAvailableForTrading` vs `TotalValue` — which is
  settled investable EUR cash) and the full positions schema (and `/positions` vs `/netpositions` for the
  canonical equity holding view) should be confirmed in the OpenAPI Explorer or with Saxo. **Gates the
  portfolio/balance work.**

---

## §3 Market data & entitlements

> **See the headline finding** — none of this is available for equities/ETFs on SIM. All §3 work targets LIVE.

### 3.1 Exchange subscriptions ⚠️ (commercial)
- Dev docs: real-time vs delayed is an **entitlement/feed-right**, surfaced via `DelayedByMinutes` and
  `PriceType*=NoAccess`; no price list in the docs. — [pricing](https://www.developer.saxo/openapi/learn/pricing)
- Commercial: each equity exchange needs its own subscription. Indicative non-professional Level-1 monthly
  fees ~**€7 Euronext**, ~**€7 Deutsche Börse/Xetra** (figures vary by source/date).
  — [home.saxo/products/market-data-subscriptions](https://www.home.saxo/products/market-data-subscriptions)
- **Per-account, not per-API-app:** once subscribed on a desktop platform, real-time prices propagate to
  all Saxo platforms incl. the OpenAPI session. Subscriptions must be initiated via a **desktop platform**.
  — [help.saxo — subscribe to live prices](https://www.help.saxo/hc/en-us/articles/360001286826-How-do-I-subscribe-to-live-prices)
- **Retail refund:** fee refunded per exchange if the client trades ≥4× in stocks/ETFs/CFDs on that
  exchange in the calendar month (non-pro L1; paid quarterly).
- ❓ Exact Belgian-account fees, and whether **Euronext Brussels vs Amsterdam are bundled or separate MICs**,
  are vendor/commercial questions — get them in writing.

### 3.2 Delayed vs real-time ✅
- Without a subscription, equity/ETF prices are **delayed 15 minutes** on LIVE (not unavailable);
  unavailable on SIM (Forex-only).
- Status field: **`DelayedByMinutes`** in the `Quote` group — "0 indicates real time"; 15 ⇒ 15-min delay.
- `PriceTypeBid`/`PriceTypeAsk` enum: `Indicative, OldIndicative, NoAccess, NoMarket, Pending` —
  `NoAccess` = missing entitlement / SIM / API market data not enabled.
- Session toggle: `PATCH /root/v1/sessions/capabilities` `TradeLevel=FullTradingAndChat` (live) vs
  `OrdersOnly` (delayed).
- ❓ `MarketState`/`PriceSource` are **not** in the public pricing docs — treat as unverified.
- [pricing](https://www.developer.saxo/openapi/learn/pricing),
  [why NoAccess](https://openapi.help.saxo/hc/en-us/articles/4405160773661-Why-do-I-get-NoAccess-instead-of-prices),
  [delayed vs live](https://openapi.help.saxo/hc/en-us/articles/4405160701085-How-do-I-choose-between-receiving-delayed-and-live-prices)

### 3.3 InfoPrices content ✅ Current quote by default; intraday OHL via field groups
`/trade/v1/infoprices` returns a current `Quote` by default. Request `FieldGroups=Quote,PriceInfo,PriceInfoDetails`
to get intraday day stats:
- `Quote` → `Bid`/`Mid`/`Ask`, `DelayedByMinutes`, `PriceTypeBid/Ask` (current quote only — no O/H/L).
- `PriceInfo` → day **High**, **Low**, NetChange, PercentChange.
- `PriceInfoDetails` → day **Open**, Volume, LastTraded, LastClose, bid/ask sizes.
- Not OHLC bar history — use `/chart/v1/charts` for time series. — [pricing](https://www.developer.saxo/openapi/learn/pricing)
- **Gates the live-quote work** (request `Quote,PriceInfo,PriceInfoDetails` for OHL + quote in one call).

### 3.4 Charts pagination & history ✅ / ❓
- **Max 1200 datapoints per request.** Pagination via `Mode` (`From` walks forward, `UpTo` walks
  backward) + `Time` + `Count`. `Horizon` minute enum: `1,5,10,15,30,60,120,240,360,480,1440,10080,43200`.
- **Backfill pattern:** `Mode=UpTo`, `Time=` oldest sample held, `Count=1200`, repeat until the array stops
  growing — that empirically finds the earliest sample.
- ❓ History depth per asset/exchange is **undocumented** (only a loose "some Forex back to 2002"). The
  "earliest available" probe must be **empirical** — exactly as the historical-backfill design anticipates.
  Do it on LIVE.
- [chart](https://www.developer.saxo/openapi/learn/chart),
  [historical prices](https://openapi.help.saxo/hc/en-us/articles/4405260778653-How-can-I-get-historical-prices),
  [horizons](https://openapi.help.saxo/hc/en-us/articles/4417058210961-What-horizons-can-I-use)

---

## §4 Tax handling (Belgium)

The Belgian tax-treatment research (TOB, dividend RV, Reynders/TIS, the 2026 CGT) backs
the platform's non-advisory disclaimer copy and is maintained in the private
`quorumtrading-infra` repo. No code depends on it.

## §5 Multi-tenant feasibility — can we serve many retail Saxo customers via delegated OAuth?

> **Question:** A Saxo customer uses our platform, authenticates in their place (OAuth Authorization Code
> consent), and we refresh their token indefinitely server-side to trade on their behalf. Is that an
> allowed setup, and does it impose a realistic upper bound on how many active customers we can support
> (assuming we scale horizontally + vertically)? Researched 2026-06-06.

### 5.1 Allowed? — three layers, only the first is a clean yes

1. **Holding + auto-refreshing a consented customer's tokens server-side: ✅ explicitly sanctioned.**
   "Make sure you store refresh tokens in a secure long-term storage. You may continue to use them as
   long as they are valid." The Authorization Code grant *is* the act-on-behalf-of-consenting-user model;
   no clause prohibits a third party persisting/refreshing customer tokens.
   — [security](https://www.developer.saxo/openapi/learn/security)

2. **Refreshing *indefinitely* with no human ever re-logging in: ❌ not for retail.** This breaks the
   "refresh forever" premise:
   - Saxo recommends retail-serving apps **re-authenticate at least weekly** ("typically before markets
     open on Monday").
   - Sessions **can be terminated server-side** when disclaimers/MiFID terms change → forces a fresh
     *interactive* login (user must re-see disclaimers). No automated bypass for retail — this is the same
     reason retail can't use CBA (see 1.2).
   - Net: continuous refresh keeps a session alive "for however long you wish" *in principle*, but we
     **must build a forced re-consent flow**; "sustain forever" is not guaranteed.
   - [How can I configure OAuth without manual steps?](https://openapi.help.saxo/hc/en-us/articles/4416637088017-How-can-I-configure-OAuth-without-manual-steps),
     [24H token](https://openapi.help.saxo/hc/en-us/articles/4416637029649-How-do-I-get-an-access-token-that-lasts-longer-than-the-24H-token),
     [CBA article](https://openapi.help.saxo/hc/en-us/articles/4416637197457-Can-Certificate-Based-Authentication-CBA-be-enabled-for-my-app)

3. **Serving *other people's* accounts commercially: ⚠️ gated behind Saxo approval.** Not self-service.
   LIVE access "requires evaluation and approval from Saxo Bank"; a commercial/multi-user app routes
   through a relationship manager / institutional sales. Whether our specific "SaaS holds many retail
   users' tokens and trades for them" model is approved is a contact-Saxo decision. **Open legal question:
   whether *we* must be a MiFID-regulated entity to act on EU-retail brokerage accounts** — potentially a
   bigger gate than any technical limit.
   — [writing-an-openapi-application](https://www.developer.saxo/openapi/learn/writing-an-openapi-application),
     [commercial app?](https://openapi.help.saxo/hc/en-us/articles/4416513424913-Am-I-allowed-to-create-a-commercial-app),
     [partner with Saxo?](https://openapi.help.saxo/hc/en-us/articles/4416513544849-How-can-we-partner-with-Saxo-Bank)

### 5.2 Upper limit on customer count? — the technical ceiling is NOT the binding constraint

- **No documented per-app cap** on number of users / concurrent sessions / active refresh tokens. (The
  only app-level cap is "10 apps per environment" — unrelated to user count.)
  — [How many apps can I register?](https://openapi.help.saxo/hc/en-us/articles/4416527344145-How-many-apps-can-I-register)
- **Per-session limits** (120 req/min, 1 order/sec) are *per user token* — they don't bound the user count.
- Only app-wide ceiling is **10M requests/day**. Staying continuously authenticated needs a refresh
  ~every 15 min ≈ ~96 refreshes/day/customer; refreshes alone → ~100k customers, and with polling/trading
  overhead realistically **~50k active customers per registered app key** before the cap bites. Shardable
  across the 10 keys, but at that scale we'd be in a negotiated partner agreement anyway.

### 5.3 The real ceiling = the forced-re-login treadmill (not compute)

Because indefinite refresh isn't real (5.1.2), the binding constraint is the **~weekly forced re-login**:
- 10k customers ≈ **~1,400 forced re-logins/day** to shepherd; 100k ≈ **~14,000/day**.
- Every missed re-auth window = that customer's automated trades **silently stop** until they log back in.
  For a DCA/scheduled-execution product, silent stoppage is the worst failure mode — the customer believes
  they're invested and they're not.

This is an operational/retention problem (re-consent UX, stale-token monitoring, nudge emails, support
load), not a CPU or rate-limit one. Scaling our infra solves the easy part and does nothing for the
human-in-the-loop cadence.

**Bottom line:** allowed in principle (token storage + refresh: yes; hands-off-forever: no; multi-tenant
retail: only with explicit Saxo partner approval + likely our own regulatory standing). Saxo imposes no
realistic hard customer ceiling (~50k+/app key, negotiable). The practical ceiling is how many weekly
forced re-logins our users tolerate and our ops can chase.

---

## Net effect on our work items

The Saxo broker and market-data adapters have since shipped (`quorumtrading-adapter-broker-saxo`,
`quorumtrading-adapter-marketdata-saxo`, plus `BrokerTokenRefreshService` and `SaxoOnboardingResource`).
The table below records the design constraints this research established and how each shaped the implementation.

| Work item | Constraint / outcome |
|---|---|
| Certificate auth (`SAXO_CERT_ENABLED`) | **Off for retail** — CBA is institutional-only (1.2). The flag defaults to `false`; retail relies on rotating-refresh OAuth + a resilient refresher. |
| Token refresh / per-account lease | Implemented (`BrokerTokenRefreshService`): 20-min access, ~40-min single-use rotating refresh; refreshes inside the 40-min window; long outages require a human re-login (1.3–1.4). |
| Unattended `SAXO_LIVE` | ⚠️ Possible but **not human-free** — periodic interactive re-login is unavoidable for retail (1.1–1.2). |
| Instrument resolution (`SaxoUicResolver`) | Implemented on `(Uic, AssetType, ExchangeId)`; reads lot/min-value per instrument; Extended-AssetTypes registration still to confirm (2.2–2.5). |
| Trade execution (`SaxoBrokerAdapter`) | Implemented against the confirmed `POST /trade/v2/orders` shape; whole-share `Quantity` only (2.1, 2.7). |
| Portfolio/balance (`SaxoBrokerAdapter`) | Implemented per the confirmed flow; ⚠️ exact balance/position field semantics still to confirm with Saxo (2.8). |
| Live quotes (`SaxoMarketDataAdapter`) | Implemented; **LIVE-only** (SIM has no equity data); `Quote,PriceInfo,PriceInfoDetails` for quote+OHL (3.2–3.3). |
| Historical backfill (`SaxoChartsPaginator`) | Implemented; **LIVE-only**; 1200/req, `Mode=UpTo` pagination; history depth is probed empirically (3.4). |
| Tax-surface copy (§4) | Research maintained in the private infra repo. |

## Still needs a vendor reply (the actual shortlist to send Saxo)
1. **Developer Terms / API licence** — any contractual restriction on retail automated/algo trading? (1.1)
2. **Refresh-token TTL** — is `2400 s` (40 min) the fixed LIVE retail value, or can a longer window be granted? (1.4)
3. **Exact LIVE logon host strings** — confirm `live.logonvalidation.net/authorize` & `/token`. (1.5)
4. **Extended AssetTypes** — confirm our SIM (and LIVE) app keys are registered so ETFs arrive as `Etf`. (2.4)
5. **KID/UCITS pre-filter** — is there a queryable field to know an instrument is tradable for the client
   *before* submitting an order, beyond `IsTradable`? (2.6)
6. **Balance/position field semantics** — which field is settled investable EUR cash; `/positions` vs
   `/netpositions` for the canonical equity holding view. (2.8)
7. **Market-data fees & coverage** — exact Belgian-account Euronext + Xetra L1 monthly fees; is Euronext
   Brussels vs Amsterdam one subscription or separate MICs; does the OpenAPI session inherit the
   desktop-purchased subscription without an extra app-level entitlement. (3.1)
8. **Equity history depth** — any committed minimum history per exchange via `/chart/v1/charts`, or is
   empirical probing the only answer. (3.4)
9. **Tax confirmations** — see the tax research in the private infra repo. (§4)
10. **Hidden absolute session cap** — if we refresh every ~15–20 min and never go idle, and disclaimers
    don't change, will a retail session survive weeks/months, or is there an undocumented absolute
    maximum lifetime forcing re-login regardless? (§5.1)
11. **Per-app user/session limit** — is there any cap (documented or not) on the number of distinct
    retail users / concurrent active refresh tokens a single LIVE app key may hold? (§5.2)
12. **Multi-tenant retail sign-off** — explicit approval of the "third-party SaaS stores + auto-refreshes
    many retail customers' tokens and trades on their behalf" model: what partnership tier is required,
    and must we be a MiFID-regulated entity to serve EU retail? (§5.1)
