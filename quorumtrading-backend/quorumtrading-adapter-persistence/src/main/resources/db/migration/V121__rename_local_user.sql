-- The fixed dev-login identity is renamed from the legacy E2E-test name to "local" — it is the
-- single user of the standalone self-host edition, shared with E2E tests and dev mode (one flow).
-- Idempotent and safe in every environment: no-op when the legacy row is absent (production) or
-- when a local@ row already exists (the unique email constraint must never trip here).
UPDATE users
SET email = 'local@quorumtrading.local',
    display_name = 'Local'
WHERE email = 'e2e-test@quorumtrading.local'
  AND NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'local@quorumtrading.local'
  );
