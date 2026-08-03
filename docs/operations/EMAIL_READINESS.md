# Email Readiness Checks

Pick-up guide for enabling outgoing notification emails in any environment. Email delivery ships **disabled by default** — every step here is required before flipping the production flag.

The system uses the [Quarkus Mailer extension](https://quarkus.io/guides/mailer) over SMTP. The intended production back-end is **AWS SES via its SMTP interface** (not the SES native API), so any SMTP-compatible mail server also works for staging or local testing.

---

## Architecture recap

```
NotificationService
   └─ fans out → per-channel dispatcher
        ├─ InAppNotificationDispatcher  (always wired)
        └─ EmailNotificationDispatcher
              ├─ resolves recipient via UserRepository.findById
              ├─ renders template via EmailTemplateRegistry
              ├─ mints HMAC-signed unsubscribe token via UnsubscribeTokenService
              └─ delegates to EmailSender port
                    └─ MailerEmailSenderAdapter (Quarkus Mailer → SMTP)
```

**Key files:**
- `quorumtrading-application/.../port/out/EmailSender.java` — output port
- `quorumtrading-application/.../service/EmailTemplateRegistry.java` — per-type templates
- `quorumtrading-application/.../service/UnsubscribeTokenService.java` — HMAC-SHA256 token mint/verify
- `quorumtrading-adapter-persistence/.../dispatcher/EmailNotificationDispatcher.java` — channel dispatcher
- `quorumtrading-bootstrap/.../notification/MailerEmailSenderAdapter.java` — SMTP-backed impl
- `quorumtrading-adapter-rest/.../resource/UnsubscribeResource.java` — public unsubscribe endpoint

---

## 1. AWS SES — domain + identity setup

These steps are one-time per environment. Do them in the AWS console (or via Terraform — see the IaC repo).

1. **Verify the sender domain.**
   - In SES → **Identities** → **Create identity** → choose **Domain** → enter `quorumtrading.com` (or staging subdomain).
   - SES emits three CNAME records for DKIM signing. Add them to the domain's DNS.
   - Verify the SPF record includes `include:amazonses.com`.
   - Wait for status → **Verified** (usually <15 min).
2. **Leave the sandbox.** New SES accounts are sandboxed (can only send to verified addresses). For production, request production access — AWS reviews the use case and typically approves within 24 h.
3. **Set up a DMARC policy** at `_dmarc.quorumtrading.com` once DKIM passes. Start with `p=none` for monitoring, escalate to `p=quarantine` after a week of clean reports.
4. **Generate SES SMTP credentials.**
   - SES → **SMTP settings** → **Create SMTP credentials**. **These are different from your IAM access keys.**
   - The console creates an IAM user with the right `ses:SendRawEmail` permission and emits an SMTP username + password.
   - Store both in your secret manager — they are not retrievable later.

The SMTP endpoint depends on the region: e.g. `email-smtp.eu-west-1.amazonaws.com:587` (STARTTLS) or `:465` (TLS).

---

## 2. Environment variables

| Variable | Purpose | Example value |
|---|---|---|
| `QUARKUS_MAILER_HOST` | SES SMTP endpoint | `email-smtp.eu-west-1.amazonaws.com` |
| `QUARKUS_MAILER_PORT` | SMTP port | `587` |
| `QUARKUS_MAILER_USERNAME` | SES SMTP username | `AKIAxxxxxxxxxxxx` |
| `QUARKUS_MAILER_PASSWORD` | SES SMTP password | (secret) |
| `QUARKUS_MAILER_START_TLS` | TLS mode | `REQUIRED` |
| `QUARKUS_MAILER_FROM` | Verified sender address | `no-reply@quorumtrading.com` |
| `UNSUBSCRIBE_SECRET` | HMAC key for unsubscribe links | 32+ random chars (`openssl rand -base64 48`) |

In `application.properties` the app-level email flag is off by default — flip it per environment,
and set the two link hosts:

```
quorumtrading.notifications.email.enabled=true   # required before any email goes out
quarkus.mailer.mock=false                        # send for real (default true)
```

| Env var | Purpose | Production value |
|---------|---------|------------------|
| `EMAIL_API_BASE_URL` | Host for the **unsubscribe** link (endpoint lives under `/api/v1`, served by the backend) | `https://api.quorumtrading.com` |
| `FRONTEND_URL` | Host for **CTA** deep-links in email bodies (`/investment-plans/<id>`, `/simulations/<id>`, …) | `https://quorumtrading.com` |

> **Why two hosts:** production ALB routing is host-header based (`quorumtrading.com` → frontend,
> `api.quorumtrading.com` → backend) and the Next.js `/api/v1` proxy only runs in development —
> one shared base URL can never serve both link kinds. The config was split accordingly
> (`quorumtrading.notifications.email.api-base-url` for unsubscribe; the existing
> `quorumtrading.frontend.base-url` for CTAs). Defaults are dev-correct
> (`localhost:8085` / `localhost:3000`); production must set both env vars above.

**`UNSUBSCRIBE_SECRET` rotation note:** rotating this invalidates every outstanding unsubscribe link in inboxes. Users can still toggle email off in their preferences page, so this is acceptable for incident response, but avoid rotating casually.

---

## 3. Local development (no real email)

Default config short-circuits all sends — no SMTP server is needed:

```
quarkus.mailer.mock=true                          # logs the message body, doesn't send
quorumtrading.notifications.email.enabled=false   # dispatcher returns FAILED with "email-sender-disabled"
```

If you want to *see* rendered emails locally, run MailHog and point Quarkus at it:

```bash
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Then in `application.properties` (or `%dev.` overrides):

```
quarkus.mailer.mock=false
quarkus.mailer.host=localhost
quarkus.mailer.port=1025
quarkus.mailer.start-tls=DISABLED
quarkus.mailer.from=no-reply@quorumtrading.local
quorumtrading.notifications.email.enabled=true
quorumtrading.notifications.email.unsubscribe-secret=local-dev-only-not-a-real-secret
```

Captured emails are visible at http://localhost:8025.

---

## 4. Pre-flight checklist

Before flipping `quorumtrading.notifications.email.enabled=true` in a non-dev environment, confirm every box:

### Domain + DNS

- [ ] Sender domain identity is **Verified** in SES (DKIM CNAMEs propagated)
- [ ] SPF record contains `include:amazonses.com` (or a chain that ends there)
- [ ] DMARC record exists at `_dmarc.<domain>` (start `p=none`)
- [ ] SES account is **out of sandbox** for production

### SMTP credentials

- [ ] SMTP username + password generated via SES console (not raw IAM keys)
- [ ] Credentials stored in secret manager (AWS Secrets Manager / SSM Parameter Store)
- [ ] ECS task definition (or equivalent) references the secret as `QUARKUS_MAILER_USERNAME` / `QUARKUS_MAILER_PASSWORD`

### Application config

- [ ] Every variable from the section 2 table is set in the environment
- [ ] `QUARKUS_MAILER_FROM` matches a verified address on the SES identity
- [ ] `UNSUBSCRIBE_SECRET` is NOT the dev default
- [ ] `EMAIL_API_BASE_URL=https://api.quorumtrading.com` and `FRONTEND_URL=https://quorumtrading.com` are both set (section 2 — unsubscribe vs CTA hosts)
- [ ] `quarkus.mailer.mock=false` (or omitted — `%prod.` profile already sets it)
- [ ] `quorumtrading.notifications.email.enabled=true`

### Functional smoke test

- [ ] Create a test investment plan against an ALPACA_PAPER broker
- [ ] Trigger `POST /investment-plans/{id}/execute`
- [ ] Verify in the user's inbox: **both** an in-app notification (DB row + bell badge) **and** an email arrive
- [ ] Open the email — confirm:
  - [ ] Subject line is correct for the type (e.g. "QuorumTrading: your investment plan funded ...")
  - [ ] Plaintext body includes amount, allocation deep-link, and unsubscribe URL
  - [ ] HTML body renders without raw `<script>` or HTML escape leakage
  - [ ] CTA button link points at `https://quorumtrading.com/investment-plans/<planId>` (matches `FRONTEND_URL`)
  - [ ] Unsubscribe link points at `https://api.quorumtrading.com/api/v1/notification-preferences/unsubscribe?...` (matches `EMAIL_API_BASE_URL`)
- [ ] Click the unsubscribe link — confirm:
  - [ ] Landing page says "Unsubscribed" with the right notification type
  - [ ] `GET /api/v1/notification-preferences` shows EMAIL removed from that type's channels
  - [ ] In-app channel is still enabled for the same type
- [ ] Repeat the trigger — confirm the same user no longer receives email for that type but still gets the in-app notification
- [ ] Manually re-enable EMAIL via the preferences page (`/notifications/preferences`) and confirm a fresh execution emails them again

### Monitoring

- [ ] CloudWatch alarms set on SES bounce rate (`>5%`) and complaint rate (`>0.1%`) — both will get the SES account suspended if breached
- [ ] `EmailNotificationDispatcher` logs failed dispatches at WARN (no email on file, sender rejection) — confirm log queries can find those by user ID. Successful sends log only at DEBUG, which is invisible at the production INFO level — verify successful delivery via the `notifications` table (`channel='EMAIL' AND status='DELIVERED'`), not the logs
- [ ] (Optional) Wire an alert on the `notifications` table when `channel='EMAIL' AND status='FAILED'` rate spikes — early warning for SMTP regressions

---

## 5. Rollback

If something goes wrong after enabling:

1. Set `quorumtrading.notifications.email.enabled=false` and redeploy (or trigger a config refresh if hot-reload is wired). The dispatcher will immediately start writing `FAILED` status with reason `email-sender-disabled` instead of sending — in-app notifications continue normally.
2. To stop a specific user from receiving emails without disabling globally: have them visit `/notifications/preferences` and toggle off email for the affected types, OR (admin tool) call `PUT /api/v1/notification-preferences/{type}` with `channels=["IN_APP"]` on their behalf.
3. If the `UNSUBSCRIBE_SECRET` is suspected of leaking: rotate it. All outstanding unsubscribe links die immediately. No data loss — users can still manage preferences in-app.

---

## 6. Test coverage

The following unit tests guard the email path:

- `UnsubscribeTokenServiceTest` — token roundtrip, tampered signature rejection, cross-secret rejection, short-secret rejection, malformed-token rejection, distinct tokens per type
- `EmailTemplateRegistryTest` — every type renders, unsubscribe link appears in both plaintext and HTML bodies, HTML escape prevents user-content XSS, payload substitution surfaces amount + failure reason + simulation deep-link

There is no integration test against a real SMTP server — that requires a credentialed network call. The smoke-test steps in section 4 cover this manually.

---

## 7. Future work

- **Bounce + complaint handling** — currently SES events aren't ingested. A possible follow-up: subscribe an SNS topic to SES, lambda/webhook hits the backend, the matching `Notification` row moves to `FAILED` with the bounce reason. Hard-bounced addresses should be flagged on the user so subsequent emails skip the send.
- **Per-user email frequency cap** — no rate limit today. If a stuck plan starts emitting `INVESTMENT_FAILED` notifications every hour, the user gets every one. Aggregate within a sliding window when the same `(userId, type, failureCategory)` repeats.
- **Multi-language templates** — templates are English-only. When localisation matters, add a `Locale` field on `User` and switch the registry on it.
- **Native API instead of SMTP** — Quarkus also has an `amazon-ses` extension that uses the SES native API directly. Marginally faster + simpler IAM, but SMTP is fine for the volumes we expect.
