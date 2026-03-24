package com.dbexplorer.service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility for storing sensitive fields.
 * Uses a machine-derived key (username + home dir + OS) so the
 * encrypted file is only readable on the same machine/user account.
 * All methods are static — do not instantiate.
 */
public final class CryptoUtils {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String PREFIX = "ENC:";

    private CryptoUtils() {}

    /** Encrypt a plain text value. Returns "ENC:<base64>" string. */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGO);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            System.err.println("Encryption failed: " + e.getMessage());
            return plainText; // Fallback to plain text
        }
    }

    /** Decrypt an "ENC:<base64>" string back to plain text. */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || !encryptedText.startsWith(PREFIX)) return encryptedText;
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText.substring(PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Decryption failed: " + e.getMessage());
            return encryptedText; // Return as-is if decryption fails
        }
    }

    /** Check if a value is already encrypted. */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** Derive a 256-bit AES key from machine-specific properties. */
    private static SecretKey deriveKey() throws Exception {
        String seed = System.getProperty("user.name", "dbexplorer")
                + "|" + System.getProperty("user.home", "/tmp")
                + "|" + System.getProperty("os.name", "unknown")
                + "|dbexplorer-v1";
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(seed.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
