package com.quorumtrading.application.port.out;

import java.time.Instant;

/**
 * Port for persisting and querying SHA-256 hashes of deleted user emails.
 * Used for GDPR-compliant anti-abuse detection (prevents welcome bonus re-claim).
 */
public interface DeletedAccountHashRepository {

    /**
     * Stores the hash of a deleted user's email along with their original signup date.
     *
     * @param emailHash SHA-256 hex digest of the lowercase email
     * @param originalSignupDate the user's original account creation timestamp
     */
    void save(String emailHash, Instant originalSignupDate);

    /**
     * Checks whether a given email hash exists in the deleted accounts table.
     *
     * @param emailHash SHA-256 hex digest of the lowercase email
     * @return true if the hash was previously stored (user deleted their account before)
     */
    boolean existsByEmailHash(String emailHash);
}
