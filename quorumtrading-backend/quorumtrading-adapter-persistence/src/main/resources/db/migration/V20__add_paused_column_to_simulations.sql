-- Add paused column to simulations table
-- Default value is true - simulations start paused by default

ALTER TABLE simulations
ADD COLUMN paused BOOLEAN NOT NULL DEFAULT TRUE;

-- Add comment for documentation
COMMENT ON COLUMN simulations.paused IS 'Indicates whether the simulation execution is paused. Simulations start paused by default.';
