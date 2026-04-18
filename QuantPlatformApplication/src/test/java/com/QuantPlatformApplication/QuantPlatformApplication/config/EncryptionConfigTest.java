package com.QuantPlatformApplication.QuantPlatformApplication.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncryptionConfigTest {

    @Test
    void encryptDecryptRoundTrip() {
        EncryptionConfig config = new EncryptionConfig("test-secret-key-32-chars-long!!");
        String plaintext = "my-super-secret-api-key-123";
        String encrypted = config.encrypt(plaintext);
        String decrypted = config.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void differentPlaintextsProduceDifferentCiphertexts() {
        EncryptionConfig config = new EncryptionConfig("test-secret-key-32-chars-long!!");
        String enc1 = config.encrypt("key-one");
        String enc2 = config.encrypt("key-two");
        assertNotEquals(enc1, enc2);
    }

    @Test
    void sameInputProducesDifferentCiphertextsDueToIV() {
        EncryptionConfig config = new EncryptionConfig("test-secret-key-32-chars-long!!");
        String enc1 = config.encrypt("same-input");
        String enc2 = config.encrypt("same-input");
        assertNotEquals(enc1, enc2);
        assertEquals("same-input", config.decrypt(enc1));
        assertEquals("same-input", config.decrypt(enc2));
    }

    @Test
    void decryptWithWrongKeyFails() {
        EncryptionConfig config1 = new EncryptionConfig("test-secret-key-32-chars-long!!");
        EncryptionConfig config2 = new EncryptionConfig("different-key-32-characters!!!!!");
        String encrypted = config1.encrypt("sensitive-data");
        assertThrows(RuntimeException.class, () -> config2.decrypt(encrypted));
    }
}
