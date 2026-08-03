package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.ScheduleFrequency;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Duration;
import java.time.Instant;

/**
 * Domain entity representing a trading strategy.
 * A strategy contains markdown configuration defining trading rules and agent behavior.
 *
 * <p><b>Aggregate Root</b> - Standalone aggregate with no child entities.</p>
 *
 * <p><b>Aggregate Boundary:</b></p>
 * <ul>
 *   <li><b>Root:</b> Strategy</li>
 *   <li><b>Child Entities:</b> None (standalone aggregate)</li>
 *   <li><b>Referenced By:</b> Simulation (by StrategyId reference)</li>
 * </ul>
 *
 * <p><b>Invariants Enforced:</b></p>
 * <ul>
 *   <li>Strategy content must have valid YAML frontmatter or legacy markdown format</li>
 *   <li>ACTIVE strategies can be updated but must remain valid</li>
 *   <li>ARCHIVED strategies cannot be modified (immutable for historical reference)</li>
 * </ul>
 *
 * <p><b>Lifecycle States:</b></p>
 * <ul>
 *   <li><b>ACTIVE:</b> Validated and ready for use in simulations</li>
 *   <li><b>ARCHIVED:</b> No longer in use, kept for historical reference</li>
 * </ul>
 *
 * <p>Version field is managed by Hibernate @Version for optimistic locking.</p>
 *
 * @see StrategyStatus
 * @see StrategyId
 */
public class Strategy implements AggregateRoot {

    private final StrategyId id;
    private String name;
    private String description;  // Human-readable summary for the strategy library (max 500 chars)
    private Integer version;  // Managed by Hibernate @Version, starts at 1
    private String content;  // Markdown configuration
    private StrategyStatus status;  // Lifecycle state
    private final Instant createdAt;
    private Instant updatedAt;

    // Lineage tracking fields
    private StrategyId parentStrategyId;  // Reference to parent strategy for derived strategies
    private int generation;  // 0 for original, increments for each derived generation
    private Instant lastTestedAt;  // When strategy was last tested via simulation
    private ScheduleFrequency testFrequency;  // How often to auto-test this strategy

    // Phase 4: Auto-disable support
    private boolean enabled = true;  // Whether strategy is enabled for scheduled simulations

    // Ownership field
    private UserId ownerId;  // User who owns this strategy

    // Public visibility (admin-curated)
    private boolean publicVisible;

    /**
     * Strategy lifecycle states.
     */
    public enum StrategyStatus {
        ACTIVE,     // Ready for simulations
        ARCHIVED    // No longer in use, kept for historical reference
    }

    public Strategy(
        StrategyId id,
        String name,
        String content,
        UserId ownerId
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("OwnerId cannot be null");
        }
        this.id = id;
        this.name = name;
        this.content = content;
        this.ownerId = ownerId;
        this.status = StrategyStatus.ACTIVE;  // New strategies are active immediately
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        // Lineage defaults for original strategies
        this.parentStrategyId = null;
        this.generation = 0;
        this.lastTestedAt = null;
        this.testFrequency = ScheduleFrequency.MANUAL_ONLY;
        this.publicVisible = false;
    }

    // Constructor for repository use (with version and status)
    public Strategy(
        StrategyId id,
        String name,
        String description,
        Integer version,
        String content,
        StrategyStatus status,
        Instant createdAt,
        Instant updatedAt,
        StrategyId parentStrategyId,
        int generation,
        Instant lastTestedAt,
        ScheduleFrequency testFrequency,
        boolean enabled,
        UserId ownerId,
        boolean publicVisible
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.content = content;
        this.status = status != null ? status : StrategyStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.parentStrategyId = parentStrategyId;
        this.generation = generation;
        this.lastTestedAt = lastTestedAt;
        this.testFrequency = testFrequency != null ? testFrequency : ScheduleFrequency.MANUAL_ONLY;
        this.enabled = enabled;
        this.ownerId = ownerId;
        this.publicVisible = publicVisible;
    }

    // Getters
    public StrategyId getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Integer getVersion() { return version; }
    public String getContent() { return content; }
    public StrategyStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public StrategyId getParentStrategyId() { return parentStrategyId; }
    public int getGeneration() { return generation; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public ScheduleFrequency getTestFrequency() { return testFrequency; }
    public boolean isEnabled() { return enabled; }
    public UserId getOwnerId() { return ownerId; }
    public boolean isPublicVisible() { return publicVisible; }

    // Setter for Hibernate version management
    public void setVersion(Integer version) {
        this.version = version;
    }

    // Setters for lineage fields (used by repository)
    public void setParentStrategyId(StrategyId parentStrategyId) {
        this.parentStrategyId = parentStrategyId;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }

    public void setLastTestedAt(Instant lastTestedAt) {
        this.lastTestedAt = lastTestedAt;
    }

    public void setTestFrequency(ScheduleFrequency testFrequency) {
        this.testFrequency = testFrequency;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // Setter for ownership field (used by repository)
    public void setOwnerId(UserId ownerId) {
        this.ownerId = ownerId;
    }

    // Phase 4: Auto-disable support methods

    /**
     * Disables the strategy, preventing it from being used in scheduled simulations.
     * Called when strategy performance drops below thresholds.
     *
     * @param reason The reason for disabling (logged for audit purposes)
     */
    public void disable(String reason) {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Re-enables the strategy for scheduled simulations.
     * Only administrators can manually re-enable disabled strategies.
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    // Domain Business Methods

    /**
     * Validates the strategy content structure.
     * Checks for YAML frontmatter format or legacy markdown headers.
     *
     * @throws InvalidStrategyException if content structure is invalid
     */
    public void validate() {
        if (content == null || content.trim().isEmpty()) {
            throw new InvalidStrategyException("Strategy content cannot be empty");
        }

        String trimmed = content.trim();

        // Check for valid format: YAML frontmatter OR legacy markdown
        boolean hasYamlFrontmatter = trimmed.startsWith("---");
        boolean hasMarkdownHeaders = trimmed.contains("## Consensus Method")
                                   || trimmed.contains("## Evaluation Interval");

        if (!hasYamlFrontmatter && !hasMarkdownHeaders) {
            throw new InvalidStrategyException(
                "Strategy content must have either YAML frontmatter (---...---) " +
                "or legacy markdown headers (## Consensus Method, etc.)"
            );
        }

        // If YAML frontmatter, validate structure
        if (hasYamlFrontmatter) {
            String[] parts = trimmed.split("---", 3);
            if (parts.length < 3) {
                throw new InvalidStrategyException(
                    "Invalid YAML frontmatter structure. Expected: ---\\n...yaml...\\n---\\n...markdown..."
                );
            }

            String frontmatter = parts[1].trim();
            if (frontmatter.isEmpty()) {
                throw new InvalidStrategyException("YAML frontmatter cannot be empty");
            }
        }
    }

    /**
     * Updates the strategy content with new markdown configuration.
     * Validates the new content before updating.
     * Only allowed for ACTIVE strategies; archived strategies cannot be modified.
     *
     * @param newContent The new markdown content
     * @throws InvalidStrategyException if content is invalid
     * @throws IllegalStateException if strategy is archived
     */
    public void updateContent(String newContent) {
        if (status == StrategyStatus.ARCHIVED) {
            throw new IllegalStateException(
                "Cannot update archived strategy. Create a new version instead."
            );
        }

        // Create temporary strategy to validate new content
        Strategy temp = new Strategy(this.id, this.name, newContent, this.ownerId);
        temp.validate();

        this.content = newContent;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the strategy name.
     * Only allowed for ACTIVE strategies; archived strategies cannot be modified.
     *
     * @param newName The new strategy name
     * @throws IllegalArgumentException if name is null or blank
     * @throws IllegalStateException if strategy is archived
     */
    public void updateName(String newName) {
        if (status == StrategyStatus.ARCHIVED) {
            throw new IllegalStateException(
                "Cannot update archived strategy. Create a new version instead."
            );
        }
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the strategy description.
     * Only allowed for ACTIVE strategies; archived strategies cannot be modified.
     *
     * @param description The new description (max 500 characters, null to clear)
     * @throws IllegalArgumentException if description exceeds 500 characters
     * @throws IllegalStateException if strategy is archived
     */
    public void updateDescription(String description) {
        if (status == StrategyStatus.ARCHIVED) {
            throw new IllegalStateException(
                "Cannot update archived strategy. Create a new version instead."
            );
        }
        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("Description must not exceed 500 characters");
        }
        this.description = description;
        this.updatedAt = Instant.now();
    }

    /**
     * Archives the strategy, marking it as no longer in use.
     * Archived strategies cannot be updated.
     */
    public void archive() {
        if (status == StrategyStatus.ARCHIVED) {
            return; // Already archived, idempotent
        }

        this.status = StrategyStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if this strategy is newer than another strategy.
     * Comparison based on version number.
     *
     * @param other The other strategy to compare against
     * @return true if this strategy has a higher version number
     */
    public boolean isNewerThan(Strategy other) {
        if (other == null) {
            return true;
        }

        int thisVersion = this.version != null ? this.version : 0;
        int otherVersion = other.version != null ? other.version : 0;

        return thisVersion > otherVersion;
    }

    /**
     * Checks if the strategy is ready to be used in simulations.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == StrategyStatus.ACTIVE;
    }

    /**
     * Checks if the strategy is archived.
     *
     * @return true if status is ARCHIVED
     */
    public boolean isArchived() {
        return status == StrategyStatus.ARCHIVED;
    }

    // Lineage and scheduling business methods

    /**
     * Records that a test run (simulation) was executed for this strategy.
     * Updates lastTestedAt timestamp.
     *
     * @param testedAt The instant when the test was run
     */
    public void recordTestRun(Instant testedAt) {
        this.lastTestedAt = testedAt;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if this strategy needs to be retested based on its test frequency.
     *
     * @param now The current instant to check against
     * @return true if the strategy should be retested
     */
    public boolean needsRetest(Instant now) {
        if (testFrequency == null || testFrequency == ScheduleFrequency.MANUAL_ONLY) {
            return false;
        }
        if (lastTestedAt == null) {
            return true;  // Never tested, needs test
        }

        Instant nextTestDue = testFrequency.calculateNextRun(lastTestedAt);
        return nextTestDue != null && !now.isBefore(nextTestDue);
    }

    /**
     * Makes this strategy publicly visible in the strategy library.
     * Only ACTIVE strategies can be made public.
     */
    public void makePublic() {
        if (status != StrategyStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE strategies can be made public");
        }
        this.publicVisible = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Makes this strategy private (only visible to owner).
     */
    public void makePrivate() {
        this.publicVisible = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Creates a derived child strategy with this strategy as parent.
     * The child will have incremented generation and inherit test frequency.
     *
     * @param childId The ID for the new child strategy
     * @param childName The name for the child strategy
     * @param childContent The content for the child strategy
     * @return A new Strategy instance derived from this one
     */
    public Strategy deriveChild(StrategyId childId, String childName, String childContent) {
        Strategy child = new Strategy(childId, childName, childContent, this.ownerId);
        child.description = this.description;
        child.parentStrategyId = this.id;
        child.generation = this.generation + 1;
        child.testFrequency = this.testFrequency;
        return child;
    }

    /**
     * Creates a derived child strategy owned by a different user (clone).
     * Used when a user clones a public strategy into their own account.
     */
    public Strategy deriveChildForUser(StrategyId childId, UserId newOwner) {
        Strategy child = new Strategy(childId, this.name, this.content, newOwner);
        child.description = this.description;
        child.parentStrategyId = this.id;
        child.generation = this.generation + 1;
        child.testFrequency = this.testFrequency;
        child.publicVisible = false;
        return child;
    }

    /**
     * Checks if this strategy has a parent (is a derived strategy).
     *
     * @return true if this strategy was derived from another
     */
    public boolean hasParent() {
        return parentStrategyId != null;
    }

    /**
     * Checks if this strategy is an original (not derived from another).
     *
     * @return true if generation is 0
     */
    public boolean isOriginal() {
        return generation == 0;
    }

    /**
     * Updates the test frequency for this strategy.
     *
     * @param frequency The new test frequency
     */
    public void updateTestFrequency(ScheduleFrequency frequency) {
        this.testFrequency = frequency != null ? frequency : ScheduleFrequency.MANUAL_ONLY;
        this.updatedAt = Instant.now();
    }

    /**
     * Returns a display-friendly name with version information.
     *
     * @return formatted name like "My Strategy (v3) [ACTIVE]"
     */
    public String getDisplayName() {
        return name + " (v" + (version != null ? version : "0") + ") [" + status + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Strategy)) return false;
        Strategy strategy = (Strategy) o;
        return id.equals(strategy.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Strategy{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", version=" + version +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
