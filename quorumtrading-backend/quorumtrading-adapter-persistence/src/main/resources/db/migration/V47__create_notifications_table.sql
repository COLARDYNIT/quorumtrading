-- V47: Create notifications table for in-app notifications

-- Notifications table for user notifications (share invites, etc.)
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    reference_id UUID,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(500),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for finding notifications by user
CREATE INDEX idx_notifications_user_id ON notifications(user_id);

-- Index for finding unread notifications by user (partial index for efficiency)
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read) WHERE read = FALSE;

-- Index for recent notifications (for sorting by creation time)
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
