# Security Policy

## Reporting a vulnerability

Please report suspected vulnerabilities **privately** — do not open a public GitHub issue.

- **Email:** frederikcolardyn@colardyn-it.be
- Or, where available, use GitHub's **[private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)** on this repository ("Security" tab → "Report a vulnerability").

Include what you can: affected component/endpoint, reproduction steps, impact assessment, and any suggested remediation. You'll get an acknowledgement within **72 hours** and a status update at least every **7 days** until resolution.

Please give us reasonable time to ship a fix before any public disclosure. We're happy to credit reporters in the release notes unless you prefer otherwise.

## Scope

In scope:

- This repository's backend (Quarkus API, MCP server), frontend (Next.js), and Docker/self-host stack
- Authentication and session handling, broker-credential storage, the OAuth onboarding flows
- The hosted instance at quorumtrading.com

Out of scope:

- Vulnerabilities in third-party dependencies without a demonstrated impact on this project (report upstream; do tell us if we ship a vulnerable version)
- Denial-of-service, volumetric attacks, or automated scanning against the hosted instance
- Social engineering, phishing, or physical attacks
- Issues requiring a compromised device or already-privileged access

## Supported versions

Security fixes land on `main`. Once releases are published, self-hosters should track the latest release — we do not backport fixes to older tags.

## Handling of sensitive data

A few notes relevant to researchers and self-hosters:

- Broker API credentials are stored encrypted at rest; brokerage OAuth tokens rotate on every refresh.
- The self-host (standalone) edition is designed for a trusted, single-user machine — its convenience login is not hardened for exposure to an untrusted network. Do not port-forward it to the public internet.
- Never include real credentials or personal data in bug reports, reproduction cases, or test fixtures.

## No bug bounty

This is an open-source project without a paid bounty program. We still deeply appreciate responsible reports and will credit you.
