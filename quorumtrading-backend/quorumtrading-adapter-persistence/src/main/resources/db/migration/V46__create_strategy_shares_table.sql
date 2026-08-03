-- V46: Create strategy_shares table for strategy sharing invitations

CREATE TABLE strategy_shares (
    id UUID PRIMARY KEY,
    strategy_id UUID NOT NULL REFERENCES orchestrator_strategies(id) ON DELETE CASCADE,
    invited_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(strategy_id, invited_user_id)
);

-- Index for finding shares by strategy
CREATE INDEX idx_strategy_shares_strategy_id ON strategy_shares(strategy_id);

-- Index for finding shares by invited user (for notifications/inbox)
CREATE INDEX idx_strategy_shares_invited_user_id ON strategy_shares(invited_user_id);

-- Index for pending shares
CREATE INDEX idx_strategy_shares_status ON strategy_shares(status);
