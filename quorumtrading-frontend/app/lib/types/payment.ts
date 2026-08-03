/**
 * Payment Types
 * Matches backend DTOs: CreateCheckoutRequest, CheckoutSessionResponse
 */

export interface CreateCheckoutRequest {
  /** Amount to top up in USD (minimum $1.00) */
  amount: number;
  /** URL to redirect to after successful payment */
  successUrl: string;
  /** URL to redirect to if payment is cancelled */
  cancelUrl: string;
}

export interface CheckoutSessionResponse {
  sessionId: string;
  checkoutUrl: string;
  clientSecret: string;
  amountCents: number;
  currency: string;
}

export interface PaymentConfig {
  configured: boolean;
  provider: string;
}
