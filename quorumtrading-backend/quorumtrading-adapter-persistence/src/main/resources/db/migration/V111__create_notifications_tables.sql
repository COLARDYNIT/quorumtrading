-- User-facing notification system: in-app inbox + per-channel preferences.
-- One row per (user × type × channel) fan-out so delivery state is tracked
-- independently per channel (in-app read state can't be dragged by email failure).

CREATE TABLE notifications (
    id                  UUID PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                VARCHAR(40)  NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    body                TEXT,
    payload             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    related_entity_id   UUID,
    status              VARCHAR(20)  NOT NULL,
    failure_reason      TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    delivered_at        TIMESTAMP WITH TIME ZONE,
    read_at             TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notifications_user_created
    ON notifications(user_id, created_at DESC);

-- Partial index drives the unread badge: cheap to count, cheap to list.
CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id, created_at DESC)
    WHERE channel = 'IN_APP' AND status = 'DELIVERED';

CREATE INDEX idx_notifications_related_entity
    ON notifications(related_entity_id)
    WHERE related_entity_id IS NOT NULL;


-- Per-user channel selection for each notification type. Only explicit user
-- choices are stored; missing rows fall back to system defaults applied in
-- the application layer.
CREATE TABLE notification_preferences (
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL,
    -- Channels stored as comma-separated string ("IN_APP,EMAIL"). Empty string
    -- means the user explicitly opted out of this type.
    channels    VARCHAR(120) NOT NULL DEFAULT '',
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, type)
);
