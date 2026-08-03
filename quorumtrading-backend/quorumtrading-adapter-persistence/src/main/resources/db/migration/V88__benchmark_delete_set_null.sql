-- Change benchmark FK from CASCADE to SET NULL so deleting a benchmark
-- keeps its simulations as standalone instead of deleting them.
ALTER TABLE simulations
    DROP CONSTRAINT simulations_benchmark_id_fkey;

ALTER TABLE simulations
    ADD CONSTRAINT simulations_benchmark_id_fkey
    FOREIGN KEY (benchmark_id)
    REFERENCES benchmarks(id)
    ON DELETE SET NULL;
