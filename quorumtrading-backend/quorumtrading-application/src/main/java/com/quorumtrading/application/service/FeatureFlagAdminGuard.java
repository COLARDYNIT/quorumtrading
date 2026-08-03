package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.UserId;

import java.util.NoSuchElementException;

/**
 * Decides who may change feature flags: {@code users.role = 'ADMIN'}, the same check
 * {@code CreateChallengeService} and {@code AdminResource} use.
 *
 * <p>One rule, on every edition. This used to carry a second one — "or the instance is
 * standalone" — so that a self-hoster could switch features on without hand-editing their own
 * row. That worked at this layer and nowhere else: the sidebar still gated the admin section on
 * the raw role, so the very user the exemption existed for could not find the screen. The
 * exemption is gone and the self-host bootstrap now promotes its owner to ADMIN instead, which
 * makes the role the single source of truth that every layer already agreed on.</p>
 *
 * <p>Anonymous callers never qualify.</p>
 */
public class FeatureFlagAdminGuard {

    private static final String RESOURCE_TYPE = "FeatureFlag";

    private final UserRepository userRepository;

    public FeatureFlagAdminGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @throws AccessDeniedException when the caller may not manage flags
     */
    public void requireFlagAdmin(UserId requestingUserId, String flagName) {
        if (requestingUserId == null) {
            throw new AccessDeniedException(null, RESOURCE_TYPE, flagName);
        }
        User user = userRepository.findById(requestingUserId)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + requestingUserId));
        if (!user.isAdmin()) {
            throw new AccessDeniedException(requestingUserId, RESOURCE_TYPE, flagName);
        }
    }
}
