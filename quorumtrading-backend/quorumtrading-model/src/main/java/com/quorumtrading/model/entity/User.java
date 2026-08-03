package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.UserRole;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain entity representing a user account.
 *
 * <p><b>Aggregate Root</b> - Controls user identity, authentication providers, and profile.</p>
 *
 * <p><b>Aggregate Boundary:</b></p>
 * <ul>
 *   <li><b>Root:</b> User</li>
 *   <li><b>Child Entities:</b> UserOAuthProvider (OAuth provider links)</li>
 *   <li><b>Referenced By:</b> Strategy, Simulation (by UserId reference)</li>
 * </ul>
 *
 * <p><b>Invariants Enforced:</b></p>
 * <ul>
 *   <li>Email must be unique and valid (non-blank)</li>
 *   <li>Email is stored in lowercase for consistency</li>
 *   <li>User must have at least one OAuth provider linked</li>
 *   <li>Cannot have duplicate OAuth providers</li>
 *   <li>New users start with USER role</li>
 *   <li>Only administrators can promote users to ADMIN role</li>
 * </ul>
 *
 * <p><b>Lifecycle:</b></p>
 * <ul>
 *   <li>Created via OAuth sign-in (Google or Apple)</li>
 *   <li>Can link additional OAuth providers</li>
 *   <li>Profile can be updated (display name, avatar)</li>
 *   <li>Login timestamps tracked for analytics</li>
 *   <li>Role can be elevated to ADMIN</li>
 * </ul>
 *
 * @see UserOAuthProvider
 * @see UserId
 * @see UserRole
 * @see OAuthProvider
 */
public class User implements AggregateRoot {

    private final UserId id;
    private final String email;
    private String displayName;
    private String avatarUrl;
    private UserRole role;
    private final Instant createdAt;
    private Instant lastLoginAt;
    private final List<UserOAuthProvider> oauthProviders;
    private boolean welcomeBudgetCredited;
    private boolean returningUser;

    /**
     * Private constructor for internal use.
     */
    private User(UserId id, String email, String displayName, String avatarUrl,
                 UserRole role, Instant createdAt, Instant lastLoginAt,
                 List<UserOAuthProvider> oauthProviders, boolean welcomeBudgetCredited,
                 boolean returningUser) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.role = role;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
        this.oauthProviders = new ArrayList<>(oauthProviders);
        this.welcomeBudgetCredited = welcomeBudgetCredited;
        this.returningUser = returningUser;
    }

    /**
     * Creates a new user with initial OAuth provider.
     *
     * @param id The unique user identifier
     * @param email The user's email address (will be normalized to lowercase)
     * @param displayName The user's display name
     * @param avatarUrl The user's avatar URL (can be null)
     * @param provider The initial OAuth provider used for sign-up
     * @return A new User instance
     * @throws IllegalArgumentException if email is blank
     */
    public static User create(UserId id, String email, String displayName,
                               String avatarUrl, OAuthProvider provider) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }

        User user = new User(
            id,
            email.toLowerCase().trim(),
            displayName,
            avatarUrl,
            UserRole.USER,
            Instant.now(),
            null,
            new ArrayList<>(),
            false,
            false
        );

        // Add initial provider with email as providerUserId placeholder
        user.addProvider(provider, email);
        return user;
    }

    /**
     * Repository constructor for reconstituting a user from persistence.
     *
     * @param id The user identifier
     * @param email The email address
     * @param displayName The display name
     * @param avatarUrl The avatar URL
     * @param role The user role
     * @param createdAt The creation timestamp
     * @param lastLoginAt The last login timestamp
     * @param providers The linked OAuth providers
     * @return A reconstituted User instance
     */
    public static User reconstitute(UserId id, String email, String displayName,
                                     String avatarUrl, UserRole role, Instant createdAt,
                                     Instant lastLoginAt, List<UserOAuthProvider> providers,
                                     boolean welcomeBudgetCredited, boolean returningUser) {
        return new User(id, email, displayName, avatarUrl, role, createdAt, lastLoginAt, providers, welcomeBudgetCredited, returningUser);
    }

    // Domain Business Methods

    /**
     * Links an additional OAuth provider to this user account.
     * Idempotent - does nothing if provider is already linked.
     *
     * @param provider The OAuth provider to link
     * @param providerUserId The user ID from the provider
     */
    public void addProvider(OAuthProvider provider, String providerUserId) {
        if (!hasProvider(provider)) {
            oauthProviders.add(new UserOAuthProvider(UUID.randomUUID(), provider, providerUserId));
        }
    }

    /**
     * Checks if this user has a specific OAuth provider linked.
     *
     * @param provider The OAuth provider to check
     * @return true if the provider is linked
     */
    public boolean hasProvider(OAuthProvider provider) {
        return oauthProviders.stream()
            .anyMatch(p -> p.getProvider() == provider);
    }

    /**
     * Records a login event, updating the last login timestamp.
     */
    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    /**
     * Promotes this user to administrator role.
     * Only authorized administrators should call this method.
     */
    public void promoteToAdmin() {
        this.role = UserRole.ADMIN;
    }

    /**
     * Updates the user's profile information.
     *
     * @param displayName The new display name
     * @param avatarUrl The new avatar URL
     */
    public void updateProfile(String displayName, String avatarUrl) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    /**
     * Checks if this user has administrator privileges.
     *
     * @return true if the user is an admin
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /**
     * Marks that the welcome budget has been credited to this user.
     */
    public void markWelcomeBudgetCredited() {
        this.welcomeBudgetCredited = true;
    }

    /**
     * Checks if the welcome budget was credited to this user.
     */
    public boolean isWelcomeBudgetCredited() {
        return welcomeBudgetCredited;
    }

    /**
     * Marks this user as a returning user (re-registered after account deletion).
     */
    public void markAsReturningUser() {
        this.returningUser = true;
    }

    /**
     * Checks if this user is a returning user who previously deleted their account.
     */
    public boolean isReturningUser() {
        return returningUser;
    }

    // Getters

    public UserId getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public UserRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public List<UserOAuthProvider> getOauthProviders() { return List.copyOf(oauthProviders); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", role=" + role +
                ", createdAt=" + createdAt +
                ", lastLoginAt=" + lastLoginAt +
                '}';
    }
}
