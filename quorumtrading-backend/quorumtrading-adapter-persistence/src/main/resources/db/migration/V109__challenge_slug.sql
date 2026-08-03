ALTER TABLE challenges ADD COLUMN slug VARCHAR(255);
UPDATE challenges SET slug = LOWER(REGEXP_REPLACE(REGEXP_REPLACE(name, '[^a-zA-Z0-9\s-]', '', 'g'), '\s+', '-', 'g'));
ALTER TABLE challenges ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX idx_challenges_slug ON challenges(slug);
