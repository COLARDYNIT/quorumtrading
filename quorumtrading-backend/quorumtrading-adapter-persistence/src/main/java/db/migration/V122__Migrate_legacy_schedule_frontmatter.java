package db.migration;

import com.quorumtrading.adapter.persistence.migration.LegacyScheduleFrontmatterMigrator;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rewrites stored strategy markdown that still uses the legacy schedule frontmatter
 * ({@code market_hours} / {@code evaluation_interval_seconds}) to the {@code live_schedule}
 * format. The legacy parse path was removed from {@code StrategyConfigParser} in the same
 * change — after this migration the parser rejects legacy input outright.
 *
 * <p>Implemented as a Flyway <b>Java</b> migration because the rewrite needs YAML-aware,
 * mode-aware logic (stock vs crypto) that would be fragile as SQL string-munging.
 * The transformation itself lives in {@link LegacyScheduleFrontmatterMigrator} so it can
 * be unit-tested without a database.</p>
 *
 * <p>Implements {@link JavaMigration} directly (rather than extending
 * {@code BaseJavaMigration}) so the Quarkus Flyway extension discovers it through the
 * Jandex index — indirect implementors via unindexed flyway-core superclasses are not
 * visible to the index scan. Lives in the {@code db.migration} package because Quarkus
 * only registers Java migrations whose package matches a configured Flyway location
 * (default {@code classpath:db/migration}).</p>
 */
public class V122__Migrate_legacy_schedule_frontmatter implements JavaMigration {

    private static final Logger LOG = Logger.getLogger(V122__Migrate_legacy_schedule_frontmatter.class);

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("122");
    }

    @Override
    public String getDescription() {
        return "Migrate legacy schedule frontmatter";
    }

    @Override
    public Integer getChecksum() {
        return null;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        Map<Object, String> rewrites = new LinkedHashMap<>();
        int scanned = 0;
        int skippedWithLegacyKeys = 0;

        try (Statement select = connection.createStatement();
             ResultSet rs = select.executeQuery(
                 "SELECT id, markdown_content FROM orchestrator_strategies")) {
            while (rs.next()) {
                scanned++;
                Object id = rs.getObject("id");
                String content = rs.getString("markdown_content");

                Optional<String> migrated = LegacyScheduleFrontmatterMigrator.migrate(content);
                if (migrated.isPresent()) {
                    rewrites.put(id, migrated.get());
                } else if (LegacyScheduleFrontmatterMigrator.frontmatterContainsLegacyKeys(content)) {
                    // e.g. missing closing --- delimiter or unparseable YAML — needs manual attention
                    skippedWithLegacyKeys++;
                    LOG.warnf("V122: strategy %s still contains legacy schedule keys but could not be "
                        + "rewritten automatically — manual migration to live_schedule required", id);
                }
            }
        }

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE orchestrator_strategies SET markdown_content = ? WHERE id = ?")) {
            for (Map.Entry<Object, String> entry : rewrites.entrySet()) {
                update.setString(1, entry.getValue());
                update.setObject(2, entry.getKey());
                update.executeUpdate();
                LOG.infof("V122: migrated legacy schedule frontmatter to live_schedule for strategy %s",
                    entry.getKey());
            }
        }

        LOG.infof("V122 legacy schedule frontmatter migration complete: %d strategies scanned, "
            + "%d rewritten, %d skipped with legacy keys", scanned, rewrites.size(), skippedWithLegacyKeys);
    }
}
