-- V44: Create users and user_oauth_providers tables for user account management

-- Users table (aggregate root for user identity)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE
);

-- Index for email lookups (canonical identity)
CREATE INDEX idx_users_email ON users(email);

-- OAuth provider links (user can authenticate via multiple providers)
CREATE TABLE user_oauth_providers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_user_id)
);

-- Index for provider lookups
CREATE INDEX idx_user_oauth_providers_user_id ON user_oauth_providers(user_id);

-- System user for legacy content ownership
INSERT INTO users (id, email, display_name, role, created_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'system@yoloquant.ai', 'YoloQuant', 'ADMIN', NOW());

-- Add system provider link
INSERT INTO user_oauth_providers (id, user_id, provider, provider_user_id, created_at)
VALUES (
    'a0000000-0000-0000-0000-000000000000',
    '00000000-0000-0000-0000-000000000000',
    'GOOGLE',
    'system@yoloquant.ai',
    NOW()
);
