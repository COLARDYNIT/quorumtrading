# OIDC Setup Guide for QuorumTrading

This guide walks you through setting up OAuth 2.0 / OpenID Connect (OIDC) authentication with Google and Apple for QuorumTrading.

## Prerequisites

- Domain configured: `quorumtrading.com` and `api.quorumtrading.com`
- HTTPS certificates in place
- Access to Google Cloud Console and Apple Developer Account

## Overview

QuorumTrading uses Quarkus OIDC for multi-tenant authentication. Tenants are configured in `application.properties` for Google, Apple, GitHub, Microsoft, GitLab, and Amazon Cognito; each is switched on per environment via `%prod.quarkus.oidc.<provider>.tenant-enabled` (currently `true` for Google and GitLab in prod). This guide covers:
- **Google OAuth**: Social login via Google accounts
- **Apple Sign In**: Social login via Apple ID (configured but disabled in prod until this setup is completed)

## 1. Google OAuth Setup

### Step 1: Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing: "QuorumTrading"
3. Navigate to **APIs & Services** → **Credentials**

### Step 2: Configure OAuth Consent Screen

1. Click **OAuth consent screen** in the left menu
2. Select **External** user type
3. Fill in the required information:
   - **App name**: QuorumTrading
   - **User support email**: your-email@domain.com
   - **App logo**: (optional) Upload your logo
   - **Application home page**: https://quorumtrading.com
   - **Application privacy policy**: https://quorumtrading.com/privacy (create this page)
   - **Application terms of service**: https://quorumtrading.com/terms (create this page)
   - **Authorized domains**:
     - `quorumtrading.com`
   - **Developer contact email**: your-email@domain.com
4. Click **Save and Continue**
5. **Scopes**: Add the following scopes:
   - `openid`
   - `email`
   - `profile`
6. Click **Save and Continue**
7. **Test users**: Add your email for testing
8. Click **Save and Continue**

### Step 3: Create OAuth 2.0 Client ID

1. Go to **Credentials** → **Create Credentials** → **OAuth client ID**
2. Select **Web application**
3. Configure:
   - **Name**: QuorumTrading Web Client
   - **Authorized JavaScript origins**:
     - `https://quorumtrading.com`
   - **Authorized redirect URIs**:
     - `https://api.quorumtrading.com/api/v1/auth/callback/google`
4. Click **Create**
5. **Save the credentials**:
   - Client ID: `YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com`
   - Client Secret: `YOUR_GOOGLE_CLIENT_SECRET`

### Step 4: Store Google Credentials in AWS Secrets Manager

```bash
# Store Google Client ID
aws secretsmanager create-secret \
  --name /quorumtrading/prod/google-client-id \
  --description "Google OAuth Client ID" \
  --secret-string "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com" \
  --region eu-central-1

# Store Google Client Secret
aws secretsmanager create-secret \
  --name /quorumtrading/prod/google-client-secret \
  --description "Google OAuth Client Secret" \
  --secret-string "YOUR_GOOGLE_CLIENT_SECRET" \
  --region eu-central-1
```

## 2. Apple Sign In Setup

### Step 1: Create Apple App ID

1. Go to [Apple Developer Portal](https://developer.apple.com/)
2. Navigate to **Certificates, Identifiers & Profiles**
3. Select **Identifiers** → Click **+** to create new
4. Select **App IDs** → **Continue**
5. Configure:
   - **Description**: QuorumTrading
   - **Bundle ID**: `com.quorumtrading.app` (Explicit)
   - **Capabilities**: Enable **Sign In with Apple**
6. Click **Continue** → **Register**

### Step 2: Create Services ID (for Web)

1. In **Identifiers**, click **+** to create new
2. Select **Services IDs** → **Continue**
3. Configure:
   - **Description**: QuorumTrading Web
   - **Identifier**: `com.quorumtrading.web`
4. Enable **Sign In with Apple**
5. Click **Configure** next to Sign In with Apple:
   - **Primary App ID**: Select `com.quorumtrading.app`
   - **Domains and Subdomains**: `quorumtrading.com`
   - **Return URLs**: `https://api.quorumtrading.com/api/v1/auth/callback/apple`
6. Click **Save** → **Continue** → **Register**

### Step 3: Create Private Key for Apple

1. Navigate to **Keys** → Click **+**
2. Configure:
   - **Key Name**: QuorumTrading Sign In Key
   - **Enable**: Sign In with Apple
3. Click **Configure** next to Sign In with Apple:
   - **Primary App ID**: Select `com.quorumtrading.app`
4. Click **Save** → **Continue** → **Register**
5. **Download the key** (.p8 file) - **THIS CAN ONLY BE DOWNLOADED ONCE**
6. Note the **Key ID** (e.g., `ABC123XYZ`)

### Step 4: Generate Apple Client Secret (JWT)

Apple requires a JWT as the client secret. You need to generate this using your private key:

```bash
# Install jwt-cli if not already installed
# macOS: brew install mike-engel/jwt-cli/jwt-cli
# Linux: cargo install jwt-cli

# Create a JWT valid for 6 months
# macOS: EXP=$(date -u -v+6m +%s)
# Linux: EXP=$(date -u -d '+6 months' +%s)
jwt encode \
  --alg ES256 \
  --kid "YOUR_KEY_ID" \
  --iss "YOUR_TEAM_ID" \
  --sub "com.quorumtrading.web" \
  --aud "https://appleid.apple.com" \
  --exp $EXP \
  --secret @/path/to/AuthKey_ABC123XYZ.p8 \
  '{}'
```

**Note**:
- `YOUR_KEY_ID`: The Key ID from Step 3
- `YOUR_TEAM_ID`: Your Apple Developer Team ID (found in top right of developer portal)
- `/path/to/AuthKey_ABC123XYZ.p8`: Path to your downloaded private key

### Step 5: Store Apple Credentials in AWS Secrets Manager

```bash
# Store Apple Client ID (Services ID)
aws secretsmanager create-secret \
  --name /quorumtrading/prod/apple-client-id \
  --description "Apple Sign In Client ID (Services ID)" \
  --secret-string "com.quorumtrading.web" \
  --region eu-central-1

# Store Apple Client Secret (JWT from previous step)
aws secretsmanager create-secret \
  --name /quorumtrading/prod/apple-client-secret \
  --description "Apple Sign In Client Secret (JWT)" \
  --secret-string "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9..." \
  --region eu-central-1
```

**Important**: Apple JWT expires after 6 months. Set a reminder to regenerate it before expiration.

## 3. Update ECS Task Definition

### Step 1: Update Task Definition to Use Secrets

You need to add the secrets to your ECS task definition. Create a new task definition revision:

```bash
# Download current task definition
aws ecs describe-task-definition \
  --task-definition quorumtrading-backend-prod \
  --region eu-central-1 \
  --query taskDefinition > /tmp/task-def.json

# Edit the task definition to add secrets (see example below)
# Then register new version
aws ecs register-task-definition \
  --cli-input-json file:///tmp/task-def-updated.json \
  --region eu-central-1
```

**Task Definition Secrets Section** (add to `containerDefinitions[0].secrets`):

```json
{
  "secrets": [
    {
      "name": "GOOGLE_CLIENT_ID",
      "valueFrom": "/quorumtrading/prod/google-client-id"
    },
    {
      "name": "GOOGLE_CLIENT_SECRET",
      "valueFrom": "/quorumtrading/prod/google-client-secret"
    },
    {
      "name": "APPLE_CLIENT_ID",
      "valueFrom": "/quorumtrading/prod/apple-client-id"
    },
    {
      "name": "APPLE_CLIENT_SECRET",
      "valueFrom": "/quorumtrading/prod/apple-client-secret"
    }
  ]
}
```

### Step 2: Grant ECS Task Role Access to Secrets

```bash
# Get the task execution role ARN
TASK_ROLE_ARN=$(aws ecs describe-task-definition \
  --task-definition quorumtrading-backend-prod \
  --region eu-central-1 \
  --query 'taskDefinition.executionRoleArn' \
  --output text)

# Create IAM policy for secrets access
cat > /tmp/secrets-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:eu-central-1:*:secret:/quorumtrading/prod/google-*",
        "arn:aws:secretsmanager:eu-central-1:*:secret:/quorumtrading/prod/apple-*"
      ]
    }
  ]
}
EOF

# Attach policy to task execution role
aws iam put-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name QuorumTradingSecretsAccess \
  --policy-document file:///tmp/secrets-policy.json
```

## 4. Enable OIDC in Application

### Step 1: Update application.properties

The prod profile enables each OIDC tenant explicitly. Set the provider's flag to `true`:

```properties
# Already enabled in prod:
%prod.quarkus.oidc.google.tenant-enabled=true
%prod.quarkus.oidc.gitlab.tenant-enabled=true

# Flip to true once the provider's credentials are in place:
%prod.quarkus.oidc.apple.tenant-enabled=false
```

(The base `quarkus.oidc.tenant-enabled=false` line disables only the *default* tenant and must stay — the named tenants above are what serve logins.)

### Step 2: Commit and Deploy

```bash
git add quorumtrading-backend/quorumtrading-bootstrap/src/main/resources/application.properties
git commit -m "feat(auth): enable OIDC for Google and Apple authentication"
git push origin main
```

Pushing to this repo does **not** deploy — CI here only builds and tests. To get the change to production, trigger the owner-run `workflow_dispatch` deploy in the private `COLARDYNIT/quorumtrading-infra` repo (it checks out this repo at a pinned ref, builds to ECR, and updates ECS).

## 5. Testing OIDC Authentication

### Test Google OAuth

1. Navigate to https://quorumtrading.com
2. Click "Sign in with Google"
3. You should be redirected to Google login
4. After authentication, you should be redirected back to the app

### Test Apple Sign In

1. Navigate to https://quorumtrading.com
2. Click "Sign in with Apple"
3. You should be redirected to Apple ID login
4. After authentication, you should be redirected back to the app

### Verify Backend Logs

```bash
# Check ECS task logs
aws logs tail /ecs/quorumtrading-backend-prod \
  --region eu-central-1 \
  --follow \
  --format short
```

Look for:
- OIDC initialization messages
- OAuth callback requests
- User authentication events

## 6. Frontend Integration

The frontend login flow is already implemented and is provider-driven — no frontend change is needed when you enable a new tenant:

1. **Provider discovery**: both the sign-in page (`quorumtrading-frontend/app/auth/signin/page.tsx`) and the header component (`app/components/AuthButtons.tsx`) fetch the enabled providers from `GET /api/v1/auth/providers` via `useAuth()`. The sign-in page renders one button per provider, remembering the last-used provider (in `localStorage`) and showing it first; the header component shows a single sign-in button for the first enabled provider.
2. **Login redirect**: clicking a button calls `login(providerId)` in `app/contexts/AuthContext.tsx`, which sets `window.location.href` to `getLoginUrl(provider)` (`app/lib/api/auth.ts`), i.e. `{API_BASE}/auth/login/{provider}`. The backend `AuthResource` starts the OIDC flow from there.
3. **Callback**: after the provider redirects back to `/api/v1/auth/callback/{provider}`, the backend sets the session cookie and sends the browser to the frontend callback page (`app/auth/callback/page.tsx`), which restores any saved `returnTo` redirect.

Once `%prod.quarkus.oidc.<provider>.tenant-enabled=true` is deployed, the provider's button appears automatically.

## Troubleshooting

### Google OAuth Issues

**Error: redirect_uri_mismatch**
- Verify redirect URI in Google Console matches exactly: `https://api.quorumtrading.com/api/v1/auth/callback/google`
- Check authorized domains include `quorumtrading.com`

**Error: invalid_client**
- Verify `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are correct
- Check secrets are properly loaded in ECS task

### Apple Sign In Issues

**Error: invalid_client**
- Regenerate Apple JWT (it expires after 6 months)
- Verify Team ID, Key ID, and Services ID are correct

**Error: invalid_grant**
- Verify return URL in Apple Console matches exactly: `https://api.quorumtrading.com/api/v1/auth/callback/apple`
- Check domain verification in Apple Console

### Common Issues

**OIDC tenant not enabled**
- Set `%prod.quarkus.oidc.<provider>.tenant-enabled=true` for the provider in application.properties

**Secrets not loading**
- Verify ECS task execution role has `secretsmanager:GetSecretValue` permission
- Check secret ARNs in task definition match secret names

**CORS errors**
- Verify CORS settings in `application.properties` include frontend URL
- Check ALB listener rules are routing correctly

## Security Best Practices

1. **Use Secrets Manager**: Never commit OAuth credentials to code
2. **Rotate Secrets**: Regularly rotate client secrets (especially Apple JWT)
3. **HTTPS Only**: Enforce HTTPS for all OAuth callbacks
4. **Validate State Parameter**: Quarkus OIDC handles this automatically
5. **Session Management**: The OIDC tenant cookies deliberately use `SameSite=None` (with `Secure`, so HTTPS is required) — see `quarkus.oidc.<provider>.authentication.cookie-same-site=none` in `application.properties`. This is required for the cross-domain flow between `quorumtrading.com` and `api.quorumtrading.com`; do not tighten it to `Strict` or the OAuth redirect breaks.

## Production Checklist

- [ ] Google OAuth consent screen published
- [ ] Google OAuth credentials stored in Secrets Manager
- [ ] Apple Sign In configured with correct return URLs
- [ ] Apple JWT generated and stored in Secrets Manager
- [ ] ECS task definition updated with secrets
- [ ] IAM role has access to secrets
- [ ] OIDC enabled in application.properties
- [ ] Provider button appears on the sign-in page (automatic via `/api/v1/auth/providers`)
- [ ] Privacy policy and Terms of Service pages created
- [ ] OAuth flow tested end-to-end
- [ ] Set reminder for Apple JWT renewal (6 months)
