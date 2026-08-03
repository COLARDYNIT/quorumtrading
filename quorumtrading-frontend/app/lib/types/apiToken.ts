/**
 * API Token Types
 * Matches backend DTOs: ApiTokenResponse, ApiTokenCreatedResponse, CreateApiTokenRequest
 */

export interface ApiToken {
  id: string;
  name: string;
  tokenPrefix: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revoked: boolean;
}

export interface ApiTokenCreated {
  id: string;
  rawToken: string;
  name: string;
  tokenPrefix: string;
  createdAt: string;
  expiresAt: string | null;
}

export interface CreateApiTokenRequest {
  name: string;
  expiresInDays?: number | null;
}
