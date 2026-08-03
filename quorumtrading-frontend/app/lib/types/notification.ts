/**
 * Notification types — match backend DTOs:
 *   NotificationResponse, NotificationPreferenceResponse, UnreadCountResponse
 */

export type NotificationType =
  | 'INVESTMENT_EXECUTED'
  | 'INVESTMENT_SKIPPED'
  | 'INVESTMENT_FAILED'
  | 'PLAN_PAUSED'
  | 'MARKET_RESEARCH_STRATEGY_CREATED'
  | 'GENERIC';

export type NotificationChannel = 'IN_APP' | 'EMAIL';

export type NotificationStatus = 'PENDING' | 'DELIVERED' | 'READ' | 'FAILED';

export interface Notification {
  id: string;
  type: NotificationType;
  channel: NotificationChannel;
  title: string;
  body: string | null;
  payload: Record<string, unknown>;
  relatedEntityId: string | null;
  status: NotificationStatus;
  createdAt: string;
  deliveredAt: string | null;
  readAt: string | null;
  failureReason: string | null;
}

export interface NotificationPreference {
  type: NotificationType;
  channels: NotificationChannel[];
}

export interface UnreadCountResponse {
  unread: number;
}

export interface UpdateNotificationPreferenceRequest {
  channels: NotificationChannel[];
}

/** Human-readable labels for notification types. */
export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  INVESTMENT_EXECUTED: 'Investment executed',
  INVESTMENT_SKIPPED: 'Investment skipped',
  INVESTMENT_FAILED: 'Investment failed',
  PLAN_PAUSED: 'Plan paused',
  MARKET_RESEARCH_STRATEGY_CREATED: 'New strategy',
  GENERIC: 'Other notifications',
};

export const NOTIFICATION_CHANNEL_LABELS: Record<NotificationChannel, string> = {
  IN_APP: 'In-app',
  EMAIL: 'Email',
};
