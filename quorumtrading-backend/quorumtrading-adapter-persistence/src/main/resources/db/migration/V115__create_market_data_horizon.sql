-- Measured per-(provider, symbol, dataType, granularity) earliest-available date.
--
-- This is a FLOOR DETECTOR, not a coverage map: it records the earliest date a
-- provider has data for, measured by a month-window probe and self-corrected at
-- runtime. earliest_available is NULL when a probe ran but found no data (we still
-- cache the row, with has_data=false, so we don't re-probe constantly).
--
-- investment_type is stored so the domain Symbol can be reconstructed exactly
-- (ticker alone is ambiguous between CRYPTO and STOCK).

CREATE TABLE market_data_horizon (
    provider           TEXT        NOT NULL,
    symbol             TEXT        NOT NULL,
    investment_type    TEXT        NOT NULL,
    event_type         TEXT        NOT NULL,
    granularity_bucket TEXT        NOT NULL,
    earliest_available DATE,
    has_data           BOOLEAN     NOT NULL,
    probed_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    source             TEXT        NOT NULL,
    PRIMARY KEY (provider, symbol, event_type, granularity_bucket)
);

-- Bulk lookup by (symbol, event_type) for REST/UI annotation.
CREATE INDEX idx_market_data_horizon_symbol_type
    ON market_data_horizon (symbol, event_type);
