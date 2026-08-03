package com.quorumtrading.adapter.persistence.crypto;

import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Encrypts and decrypts broker credentials using AES-256-GCM.
 *
 * <p>Storage format: {@code IV (12 bytes) || ciphertext || GCM tag (16 bytes)}
 * stored in the BYTEA column of the broker_accounts table.</p>
 *
 * <p>The encryption key is provided as a hex-encoded 32-byte string (64 hex chars)
 * via the {@code quorumtrading.credentials.encryption-key} config property.</p>
 *
 * <h2>Tagged-envelope plaintext scheme</h2>
 *
 * <p>The plaintext placed inside the GCM ciphertext carries a leading <em>type tag</em>
 * so {@link #decrypt(byte[])} can reconstruct the correct sealed {@link BrokerCredentials}
 * subtype. The envelope shape is:</p>
 *
 * <pre>{@code
 *   <TAG> "\n" <body>
 * }</pre>
 *
 * <p>where {@code <TAG>} is one of:</p>
 * <ul>
 *   <li>{@code AK}   — {@link ApiKeyCredentials}; body is {@code apiKey "\n" apiSecret}
 *       (apiKey is non-blank and cannot contain {@code \n}, so a single split is safe).</li>
 *   <li>{@code SAXO} — {@link SaxoOAuthCredentials}; body is a length-prefixed sequence of
 *       fields (see {@link #encodeSaxo}) so arbitrary token content — including {@code \n}
 *       and {@code :} — round-trips losslessly.</li>
 * </ul>
 *
 * <h2>Legacy fallback</h2>
 *
 * <p>Pre-tag blobs were a bare {@code apiKey ":" apiSecret} string with no tag prefix.
 * When the decrypted plaintext does not begin with a recognised {@code <TAG> "\n"} prefix,
 * decryption falls back to the legacy {@code key:secret} parse and yields an
 * {@link ApiKeyCredentials}. AES-256-GCM and the BYTEA storage layout are unchanged, so
 * legacy ciphertext decrypts without re-encryption.</p>
 */
@ApplicationScoped
public class CredentialEncryptor {

    private static final Logger log = LoggerFactory.getLogger(CredentialEncryptor.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_KEY_LENGTH_BYTES = 32;

    /**
     * Well-known dev fallback key from application.properties. Must never be used in production —
     * anyone with the source could decrypt broker credentials at rest.
     */
    static final String DEV_DEFAULT_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /** Type tag for {@link ApiKeyCredentials} envelopes. */
    private static final String TAG_API_KEY = "AK";
    /** Type tag for {@link SaxoOAuthCredentials} envelopes. */
    private static final String TAG_SAXO = "SAXO";
    /** Separator between the type tag and the envelope body. */
    private static final char TAG_SEPARATOR = '\n';

    @ConfigProperty(name = "quorumtrading.credentials.encryption-key")
    String encryptionKeyHex;

    private SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void init() {
        if (encryptionKeyHex == null || encryptionKeyHex.isBlank()) {
            throw new IllegalStateException("quorumtrading.credentials.encryption-key must be configured");
        }
        // Defense in depth: application.properties already omits the fallback default for %prod,
        // but refuse the well-known dev key outright if it somehow reaches a prod deployment.
        if (DEV_DEFAULT_KEY_HEX.equalsIgnoreCase(encryptionKeyHex) && isProdProfileActive()) {
            throw new IllegalStateException(
                    "quorumtrading.credentials.encryption-key is set to the well-known dev default while the "
                            + "'prod' profile is active. Set BROKER_CREDENTIALS_ENCRYPTION_KEY to a unique key "
                            + "(generate with: openssl rand -hex 32).");
        }
        byte[] keyBytes = hexToBytes(encryptionKeyHex);
        if (keyBytes.length != AES_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "Encryption key must be exactly 32 bytes (64 hex chars), got " + keyBytes.length + " bytes");
        }
        secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("Credential encryption initialized with AES-256-GCM");
    }

    /**
     * True when the Quarkus 'prod' profile is among the active profiles.
     * Overridable in tests (no Quarkus runtime available in plain unit tests).
     */
    boolean isProdProfileActive() {
        return io.quarkus.runtime.configuration.ConfigUtils.getProfiles().contains("prod");
    }

    /**
     * Encrypts broker credentials into a tagged envelope.
     *
     * <p>Both sealed kinds are supported: {@link ApiKeyCredentials} (Alpaca) and
     * {@link SaxoOAuthCredentials} (Saxo OAuth). The type tag is embedded in the
     * plaintext so {@link #decrypt(byte[])} can rebuild the exact subtype.</p>
     *
     * @param credentials the credentials to encrypt
     * @return encrypted bytes: IV (12) || ciphertext || GCM tag (16)
     */
    public byte[] encrypt(BrokerCredentials credentials) {
        try {
            String plaintext = serialize(credentials);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext (GCM tag is appended by the cipher)
            byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt credentials", e);
        }
    }

    /**
     * Decrypts broker credentials, reconstructing the correct sealed subtype.
     *
     * <p>Recognises tagged {@code AK} / {@code SAXO} envelopes and falls back to the
     * legacy untagged {@code key:secret} blob (decoded as {@link ApiKeyCredentials}).</p>
     *
     * @param encrypted the encrypted bytes: IV (12) || ciphertext || GCM tag (16)
     * @return the decrypted credentials, or {@code null} if decryption fails
     */
    public BrokerCredentials decrypt(byte[] encrypted) {
        try {
            if (encrypted.length < IV_LENGTH_BYTES + 1) {
                log.warn("Encrypted credentials too short ({} bytes)", encrypted.length);
                return null;
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH_BYTES);

            byte[] ciphertext = new byte[encrypted.length - IV_LENGTH_BYTES];
            System.arraycopy(encrypted, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            String decoded = new String(plaintext, StandardCharsets.UTF_8);

            return deserialize(decoded);
        } catch (AEADBadTagException e) {
            // Handled, recoverable condition — NOT an error: the stored ciphertext does not match
            // the active encryption key (key rotated after this row was written, or a corrupt blob).
            // We return null; callers treat the account as credential-unavailable and fail closed.
            // Logged at WARN (not ERROR) so a single un-decryptable row can't spam CloudWatch on
            // every scheduler tick and drown genuine errors. Stack trace at DEBUG for diagnosis.
            log.warn("Could not decrypt broker credentials (ciphertext does not match the active "
                + "encryption key — key rotation or corrupt row); treating account as "
                + "credential-unavailable");
            log.debug("Credential decrypt failure detail", e);
            return null;
        } catch (Exception e) {
            log.error("Failed to decrypt credentials", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Raw string secrets (no type-tag envelope)
    // ------------------------------------------------------------------

    /**
     * Encrypts an arbitrary secret string — not broker credentials, so no type-tag envelope.
     * Same AES-256-GCM layout as {@link #encrypt}: {@code IV (12) || ciphertext || GCM tag (16)}.
     * Used for runtime-stored platform secrets such as the user-submitted OpenRouter API key.
     */
    public byte[] encryptString(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    /**
     * Decrypts a blob produced by {@link #encryptString(String)}.
     *
     * @return the plaintext, or {@code null} when decryption fails (wrong key, corrupt blob)
     */
    public String decryptString(byte[] encrypted) {
        try {
            if (encrypted == null || encrypted.length < IV_LENGTH_BYTES + 1) {
                log.warn("Encrypted secret too short ({} bytes)", encrypted == null ? 0 : encrypted.length);
                return null;
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH_BYTES);

            byte[] ciphertext = new byte[encrypted.length - IV_LENGTH_BYTES];
            System.arraycopy(encrypted, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt secret", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Serialization (plaintext envelope)
    // ------------------------------------------------------------------

    private String serialize(BrokerCredentials credentials) {
        return switch (credentials) {
            case ApiKeyCredentials ak ->
                    TAG_API_KEY + TAG_SEPARATOR + ak.apiKey() + TAG_SEPARATOR + ak.apiSecret();
            case SaxoOAuthCredentials saxo ->
                    TAG_SAXO + TAG_SEPARATOR + encodeSaxo(saxo);
        };
    }

    private BrokerCredentials deserialize(String decoded) {
        if (decoded.startsWith(TAG_API_KEY + TAG_SEPARATOR)) {
            return parseApiKey(decoded.substring((TAG_API_KEY + TAG_SEPARATOR).length()));
        }
        if (decoded.startsWith(TAG_SAXO + TAG_SEPARATOR)) {
            return decodeSaxo(decoded.substring((TAG_SAXO + TAG_SEPARATOR).length()));
        }
        // Legacy fallback: untagged "key:secret" blob → ApiKeyCredentials.
        return parseLegacyApiKey(decoded);
    }

    /**
     * Parses the {@code AK} envelope body: {@code apiKey "\n" apiSecret}. The apiKey is
     * non-blank and cannot contain {@code \n}, so the first newline cleanly delimits the
     * secret (which may itself contain {@code \n} or {@code :}).
     */
    private BrokerCredentials parseApiKey(String body) {
        int sep = body.indexOf(TAG_SEPARATOR);
        if (sep < 0) {
            log.warn("Invalid AK envelope: missing field separator");
            return null;
        }
        String apiKey = body.substring(0, sep);
        String apiSecret = body.substring(sep + 1);
        return new ApiKeyCredentials(apiKey, apiSecret);
    }

    /**
     * Parses the legacy untagged {@code apiKey ":" apiSecret} blob. Split on the first
     * colon only — API secrets may contain colons.
     */
    private BrokerCredentials parseLegacyApiKey(String decoded) {
        String[] parts = decoded.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid legacy credentials format after decryption");
            return null;
        }
        return new ApiKeyCredentials(parts[0], parts[1]);
    }

    /**
     * Encodes a {@link SaxoOAuthCredentials} body as a length-prefixed field sequence:
     *
     * <pre>{@code
     *   for each field f:  <utf8-byte-length> "\n" <f>
     * }</pre>
     *
     * <p>Length-prefixing makes the encoding robust against arbitrary content in any field
     * — refresh/access tokens may contain {@code \n}, {@code :} or any other byte. A
     * {@code null} string field (accessToken) is encoded with a sentinel length of
     * {@code -1}; the expiry instant is encoded as its epoch-milli decimal string, or
     * {@code -1} length when absent.</p>
     *
     * <p>Field order: appKey, appSecret, refreshToken, accessToken, accessTokenExpiry,
     * environment.</p>
     */
    private String encodeSaxo(SaxoOAuthCredentials saxo) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, saxo.appKey());
        appendField(sb, saxo.appSecret());
        appendField(sb, saxo.refreshToken());
        appendField(sb, saxo.accessToken());
        appendField(sb, saxo.accessTokenExpiry() == null
                ? null
                : Long.toString(saxo.accessTokenExpiry().toEpochMilli()));
        appendField(sb, saxo.environment().name());
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("-1").append(TAG_SEPARATOR);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        sb.append(bytes.length).append(TAG_SEPARATOR).append(value);
    }

    private BrokerCredentials decodeSaxo(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        FieldReader reader = new FieldReader(bytes);

        String appKey = reader.next();
        String appSecret = reader.next();
        String refreshToken = reader.next();
        String accessToken = reader.next();
        String expiryMillis = reader.next();
        String environment = reader.next();

        Instant accessTokenExpiry = expiryMillis == null
                ? null
                : Instant.ofEpochMilli(Long.parseLong(expiryMillis));

        return new SaxoOAuthCredentials(
                appKey,
                appSecret,
                refreshToken,
                accessToken,
                accessTokenExpiry,
                SaxoEnvironment.valueOf(environment));
    }

    /**
     * Reads length-prefixed UTF-8 fields from the Saxo envelope body. Each field is
     * {@code <decimal-byte-length> "\n" <bytes>}; a length of {@code -1} denotes a
     * {@code null} field.
     */
    private static final class FieldReader {
        private final byte[] bytes;
        private int pos;

        FieldReader(byte[] bytes) {
            this.bytes = bytes;
        }

        String next() {
            int sep = indexOfSeparator();
            if (sep < 0) {
                throw new IllegalStateException("Malformed Saxo envelope: missing length separator");
            }
            int length = Integer.parseInt(new String(bytes, pos, sep - pos, StandardCharsets.UTF_8));
            pos = sep + 1; // skip the '\n'
            if (length < 0) {
                return null;
            }
            if (pos + length > bytes.length) {
                throw new IllegalStateException("Malformed Saxo envelope: field length out of bounds");
            }
            String value = new String(bytes, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return value;
        }

        private int indexOfSeparator() {
            for (int i = pos; i < bytes.length; i++) {
                if (bytes[i] == (byte) TAG_SEPARATOR) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
