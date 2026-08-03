package com.quorumtrading.adapter.persistence.crypto;

import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CredentialEncryptorTest {

    private static final String KEY_HEX =
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    private CredentialEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new CredentialEncryptor();
        encryptor.encryptionKeyHex = KEY_HEX;
        encryptor.init();
    }

    // ------------------------------------------------------------------
    // Prod-profile guard against the well-known dev default key
    // ------------------------------------------------------------------

    @Test
    void refusesWellKnownDevDefaultKeyWhenProdProfileActive() {
        CredentialEncryptor prodEncryptor = new CredentialEncryptor() {
            @Override
            boolean isProdProfileActive() {
                return true;
            }
        };
        prodEncryptor.encryptionKeyHex = CredentialEncryptor.DEV_DEFAULT_KEY_HEX;

        IllegalStateException ex = assertThrows(IllegalStateException.class, prodEncryptor::init);
        assertTrue(ex.getMessage().contains("BROKER_CREDENTIALS_ENCRYPTION_KEY"));
    }

    @Test
    void allowsWellKnownDevDefaultKeyOutsideProdProfile() {
        CredentialEncryptor devEncryptor = new CredentialEncryptor() {
            @Override
            boolean isProdProfileActive() {
                return false;
            }
        };
        devEncryptor.encryptionKeyHex = CredentialEncryptor.DEV_DEFAULT_KEY_HEX;

        assertDoesNotThrow(devEncryptor::init);
    }

    @Test
    void allowsNonDefaultKeyWhenProdProfileActive() {
        CredentialEncryptor prodEncryptor = new CredentialEncryptor() {
            @Override
            boolean isProdProfileActive() {
                return true;
            }
        };
        prodEncryptor.encryptionKeyHex = KEY_HEX;

        assertDoesNotThrow(prodEncryptor::init);
    }

    // ------------------------------------------------------------------
    // ApiKeyCredentials (Alpaca) — tagged "AK" envelope
    // ------------------------------------------------------------------

    @Test
    void encryptDecryptRoundtrip() {
        ApiKeyCredentials original = new ApiKeyCredentials("PKTEST123", "secretValue456");

        byte[] encrypted = encryptor.encrypt(original);
        BrokerCredentials decrypted = encryptor.decrypt(encrypted);

        assertNotNull(decrypted);
        assertInstanceOf(ApiKeyCredentials.class, decrypted);
        ApiKeyCredentials decryptedKey = (ApiKeyCredentials) decrypted;
        assertEquals(original.apiKey(), decryptedKey.apiKey());
        assertEquals(original.apiSecret(), decryptedKey.apiSecret());
    }

    @Test
    void uniqueIvPerEncryption() {
        ApiKeyCredentials credentials = new ApiKeyCredentials("sameKey", "sameSecret");

        byte[] encrypted1 = encryptor.encrypt(credentials);
        byte[] encrypted2 = encryptor.encrypt(credentials);

        assertFalse(Arrays.equals(encrypted1, encrypted2),
                "Same input must produce different ciphertext due to random IV");
    }

    @Test
    void colonInApiSecretHandledCorrectly() {
        ApiKeyCredentials original = new ApiKeyCredentials("myKey", "secret:with:colons");

        byte[] encrypted = encryptor.encrypt(original);
        BrokerCredentials decrypted = encryptor.decrypt(encrypted);

        assertNotNull(decrypted);
        assertInstanceOf(ApiKeyCredentials.class, decrypted);
        ApiKeyCredentials decryptedKey = (ApiKeyCredentials) decrypted;
        assertEquals("myKey", decryptedKey.apiKey());
        assertEquals("secret:with:colons", decryptedKey.apiSecret());
    }

    @Test
    void newlineInApiSecretHandledCorrectly() {
        // The AK envelope splits the body on the FIRST newline only, so a secret
        // containing newlines must round-trip intact.
        ApiKeyCredentials original = new ApiKeyCredentials("myKey", "secret\nwith\nnewlines");

        byte[] encrypted = encryptor.encrypt(original);
        BrokerCredentials decrypted = encryptor.decrypt(encrypted);

        assertInstanceOf(ApiKeyCredentials.class, decrypted);
        ApiKeyCredentials decryptedKey = (ApiKeyCredentials) decrypted;
        assertEquals("myKey", decryptedKey.apiKey());
        assertEquals("secret\nwith\nnewlines", decryptedKey.apiSecret());
    }

    // ------------------------------------------------------------------
    // SaxoOAuthCredentials — tagged "SAXO" envelope
    // ------------------------------------------------------------------

    @Test
    void saxoRoundtripAllFields() {
        Instant expiry = Instant.parse("2026-06-04T12:34:56.789Z");
        SaxoOAuthCredentials original = new SaxoOAuthCredentials(
                "saxoAppKey",
                "saxoAppSecret",
                "refresh-token-rotating-abc",
                "access-token-short-lived-xyz",
                expiry,
                SaxoEnvironment.LIVE);

        byte[] encrypted = encryptor.encrypt(original);
        BrokerCredentials decrypted = encryptor.decrypt(encrypted);

        assertNotNull(decrypted);
        assertInstanceOf(SaxoOAuthCredentials.class, decrypted);
        SaxoOAuthCredentials saxo = (SaxoOAuthCredentials) decrypted;
        assertEquals("saxoAppKey", saxo.appKey());
        assertEquals("saxoAppSecret", saxo.appSecret());
        assertEquals("refresh-token-rotating-abc", saxo.refreshToken());
        assertEquals("access-token-short-lived-xyz", saxo.accessToken());
        assertEquals(expiry, saxo.accessTokenExpiry());
        assertEquals(SaxoEnvironment.LIVE, saxo.environment());
        // Full structural equality for good measure.
        assertEquals(original, saxo);
    }

    @Test
    void saxoRoundtripWithNullAccessTokenAndExpiry() {
        // A freshly onboarded credential set may hold only a refresh token.
        SaxoOAuthCredentials original = new SaxoOAuthCredentials(
                "appKey",
                "appSecret",
                "refresh-only",
                null,
                null,
                SaxoEnvironment.SIM);

        byte[] encrypted = encryptor.encrypt(original);
        BrokerCredentials decrypted = encryptor.decrypt(encrypted);

        assertInstanceOf(SaxoOAuthCredentials.class, decrypted);
        SaxoOAuthCredentials saxo = (SaxoOAuthCredentials) decrypted;
        assertEquals("appKey", saxo.appKey());
        assertEquals("appSecret", saxo.appSecret());
        assertEquals("refresh-only", saxo.refreshToken());
        assertNull(saxo.accessToken());
        assertNull(saxo.accessTokenExpiry());
        assertEquals(SaxoEnvironment.SIM, saxo.environment());
        assertEquals(original, saxo);
    }

    @Test
    void saxoRoundtripWithDelimiterCharsInTokens() {
        // Tokens containing the envelope separators (newline, colon) must survive
        // the length-prefixed encoding intact.
        Instant expiry = Instant.parse("2027-01-01T00:00:00Z");
        SaxoOAuthCredentials original = new SaxoOAuthCredentials(
                "app\nkey:with",
                "app:secret\nweird",
                "refresh\n123:456",
                "access:789\n012",
                expiry,
                SaxoEnvironment.SIM);

        byte[] encrypted = encryptor.encrypt(original);
        BrokerCredentials decrypted = encryptor.decrypt(encrypted);

        assertInstanceOf(SaxoOAuthCredentials.class, decrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void saxoUniqueIvPerEncryption() {
        SaxoOAuthCredentials credentials = new SaxoOAuthCredentials(
                "k", "s", "r", "a", Instant.parse("2026-06-04T00:00:00Z"), SaxoEnvironment.SIM);

        byte[] e1 = encryptor.encrypt(credentials);
        byte[] e2 = encryptor.encrypt(credentials);

        assertFalse(Arrays.equals(e1, e2),
                "Same Saxo input must produce different ciphertext due to random IV");
    }

    // ------------------------------------------------------------------
    // Legacy untagged "key:secret" blob fallback
    // ------------------------------------------------------------------

    @Test
    void legacyUntaggedBlobDecryptsToApiKeyCredentials() throws Exception {
        // Reproduce a pre-tag ciphertext: plaintext is the bare "apiKey:apiSecret"
        // blob with NO type tag prefix, encrypted with the same AES-256-GCM layout.
        byte[] legacy = legacyEncrypt("PKLEGACY", "legacySecret");

        BrokerCredentials decrypted = encryptor.decrypt(legacy);

        assertNotNull(decrypted);
        assertInstanceOf(ApiKeyCredentials.class, decrypted);
        ApiKeyCredentials key = (ApiKeyCredentials) decrypted;
        assertEquals("PKLEGACY", key.apiKey());
        assertEquals("legacySecret", key.apiSecret());
    }

    @Test
    void legacyBlobWithColonInSecretDecrypts() throws Exception {
        byte[] legacy = legacyEncrypt("PKLEGACY", "legacy:secret:with:colons");

        BrokerCredentials decrypted = encryptor.decrypt(legacy);

        assertInstanceOf(ApiKeyCredentials.class, decrypted);
        ApiKeyCredentials key = (ApiKeyCredentials) decrypted;
        assertEquals("PKLEGACY", key.apiKey());
        assertEquals("legacy:secret:with:colons", key.apiSecret());
    }

    // ------------------------------------------------------------------
    // Failure / robustness paths (unchanged behaviour)
    // ------------------------------------------------------------------

    @Test
    void wrongKeyLengthRejected() {
        CredentialEncryptor bad = new CredentialEncryptor();
        bad.encryptionKeyHex = "abcd1234"; // Only 4 bytes

        IllegalStateException ex = assertThrows(IllegalStateException.class, bad::init);
        assertTrue(ex.getMessage().contains("32 bytes"));
    }

    @Test
    void blankKeyRejected() {
        CredentialEncryptor bad = new CredentialEncryptor();
        bad.encryptionKeyHex = "";

        assertThrows(IllegalStateException.class, bad::init);
    }

    @Test
    void corruptedCiphertextReturnsNull() {
        ApiKeyCredentials original = new ApiKeyCredentials("key", "secret");
        byte[] encrypted = encryptor.encrypt(original);

        // Flip a byte in the ciphertext area (after IV)
        encrypted[15] ^= 0xFF;

        BrokerCredentials result = encryptor.decrypt(encrypted);
        assertNull(result, "Corrupted ciphertext should return null");
    }

    @Test
    void tooShortCiphertextReturnsNull() {
        byte[] tooShort = new byte[5];
        BrokerCredentials result = encryptor.decrypt(tooShort);
        assertNull(result, "Ciphertext shorter than IV should return null");
    }

    @Test
    void differentKeyCannotDecrypt() {
        ApiKeyCredentials original = new ApiKeyCredentials("key", "secret");
        byte[] encrypted = encryptor.encrypt(original);

        CredentialEncryptor otherEncryptor = new CredentialEncryptor();
        otherEncryptor.encryptionKeyHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        otherEncryptor.init();

        BrokerCredentials result = otherEncryptor.decrypt(encrypted);
        assertNull(result, "Wrong key should fail to decrypt");
    }

    @Test
    void saxoCorruptedCiphertextReturnsNull() {
        SaxoOAuthCredentials original = new SaxoOAuthCredentials(
                "k", "s", "r", "a", Instant.parse("2026-06-04T00:00:00Z"), SaxoEnvironment.LIVE);
        byte[] encrypted = encryptor.encrypt(original);

        encrypted[20] ^= 0xFF;

        assertNull(encryptor.decrypt(encrypted), "Corrupted Saxo ciphertext should return null");
    }

    // ------------------------------------------------------------------
    // Raw string secrets (encryptString / decryptString)
    // ------------------------------------------------------------------

    @Test
    void rawStringRoundtrip() {
        byte[] encrypted = encryptor.encryptString("sk-or-v1-super-secret");

        assertEquals("sk-or-v1-super-secret", encryptor.decryptString(encrypted));
    }

    @Test
    void rawStringCiphertextDoesNotContainPlaintext() {
        byte[] encrypted = encryptor.encryptString("sk-or-v1-super-secret");

        assertFalse(new String(encrypted, StandardCharsets.ISO_8859_1).contains("sk-or-v1"),
                "Ciphertext must not leak the plaintext key");
    }

    @Test
    void rawStringUniqueIvPerEncryption() {
        byte[] first = encryptor.encryptString("same-secret");
        byte[] second = encryptor.encryptString("same-secret");

        assertFalse(Arrays.equals(first, second), "Random IV must vary the ciphertext");
    }

    @Test
    void rawStringDecryptReturnsNullOnCorruption() {
        byte[] encrypted = encryptor.encryptString("sk-or-v1-super-secret");
        encrypted[encrypted.length - 1] ^= 0xFF;

        assertNull(encryptor.decryptString(encrypted));
    }

    @Test
    void rawStringDecryptReturnsNullOnNullOrTooShort() {
        assertNull(encryptor.decryptString(null));
        assertNull(encryptor.decryptString(new byte[5]));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Encrypts a bare {@code "apiKey:apiSecret"} plaintext with the legacy (untagged)
     * scheme, reusing the same key + AES-256-GCM IV||ciphertext||tag layout the encryptor
     * has always produced. This emulates a row written before the tagged envelope existed.
     */
    private byte[] legacyEncrypt(String apiKey, String apiSecret) throws Exception {
        String plaintext = apiKey + ":" + apiSecret;

        byte[] keyBytes = hexToBytes(KEY_HEX);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        return result;
    }

    // ------------------------------------------------------------------
    // Wrong-key / corrupt ciphertext — the production scenario (key rotated
    // after a broker_account row was written). Must degrade gracefully:
    // return null (fail-closed), never throw, so a scheduler tick loading the
    // account survives. The AEADBadTagException path logs WARN, not ERROR.
    // ------------------------------------------------------------------

    @Test
    void decryptWithWrongKeyReturnsNullAndDoesNotThrow() {
        ApiKeyCredentials original = new ApiKeyCredentials("PKTEST123", "secretValue456");
        byte[] encrypted = encryptor.encrypt(original);

        CredentialEncryptor otherKey = new CredentialEncryptor();
        otherKey.encryptionKeyHex =
                "0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f";
        otherKey.init();

        BrokerCredentials result = assertDoesNotThrow(() -> otherKey.decrypt(encrypted));
        assertNull(result, "wrong-key decrypt must return null, not throw (fail-closed)");
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
