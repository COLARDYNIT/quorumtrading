# ADR-0001: Manual Admin Role Check Instead of @RolesAllowed

**Status:** Accepted
**Date:** 2026-02-16
**Context:** Admin endpoint authorization in AdminResource

## Decision

Admin endpoints use `@Authenticated` (Quarkus OIDC) combined with a manual `requireAdmin()` method that checks the `users.role` database column, rather than the standard `@RolesAllowed("ADMIN")` JAX-RS annotation.

## Context

The platform uses external OIDC providers (Google, GitLab) for authentication. These providers issue ID tokens with standard claims (email, name, picture) but do not carry application-specific roles. The `ADMIN` role is stored exclusively in the application's `users` database table.

Quarkus `@RolesAllowed` works by inspecting the `SecurityIdentity` object, which is populated from OIDC token claims. Since our OIDC providers never include an `ADMIN` role claim, `@RolesAllowed("ADMIN")` would reject every request, including legitimate admins.

## How It Works

1. `@Authenticated` on the class ensures an authenticated `SecurityIdentity` exists (rejects unauthenticated requests with 401). In production this comes from the OIDC session; in dev/test/local profiles the `DevSessionAuthMechanism` populates it from the `dev_session` cookie
2. `requireAdmin()` reads the email from the `SecurityIdentity` principal via `CurrentUserProvider`
3. Looks up the `User` entity by email in the database
4. Checks `user.isAdmin()` (i.e., `role == UserRole.ADMIN`)
5. Throws `ForbiddenException` (403) if the user is not an admin

## Alternatives Considered

### SecurityIdentityAugmentor

Quarkus supports custom `SecurityIdentityAugmentor` beans that can enrich the `SecurityIdentity` with roles from the database after OIDC authentication. This would allow `@RolesAllowed("ADMIN")` to work natively.

**Rejected because:**
- Adds a DB query to every authenticated request (not just admin endpoints) unless scoped carefully
- Extra infrastructure for a single resource class
- The manual check is explicit, easy to audit, and contained within AdminResource

### Embedding Roles in OIDC Tokens

Some OIDC providers support custom claims or app-level roles (e.g., Auth0, Keycloak). We could migrate to a provider that supports this.

**Rejected because:**
- We use Google and GitLab OAuth, which don't support custom role claims
- Switching providers is a disproportionate effort for one admin endpoint
- Role changes would require token refresh/re-login rather than taking effect immediately

## Consequences

- Admin authorization is handled manually in one place (`requireAdmin()` in AdminResource)
- `@Authenticated` still provides the security boundary: unauthenticated requests never reach the method body
- Admin endpoints work with the `dev_session` cookie in dev/test profiles: `DevSessionAuthMechanism` builds a `SecurityIdentity` from the cookie, so `@Authenticated` passes and `requireAdmin()` still enforces the DB role check
- If more role-protected resources emerge in the future, reconsider the `SecurityIdentityAugmentor` approach to avoid duplicating `requireAdmin()` patterns
