package com.netbanking.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final int ITERATIONS = 310_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static PasswordHash create(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new PasswordHash(derive(password, salt, ITERATIONS), HexFormat.of().formatHex(salt), ITERATIONS);
    }

    public static boolean verify(char[] password, String expectedHash, String saltHex, int iterations) {
        if (iterations < 100_000 || iterations > 1_000_000) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(expectedHash);
            byte[] salt = HexFormat.of().parseHex(saltHex);
            byte[] actual = HexFormat.of().parseHex(derive(password, salt, iterations));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable on this Java runtime.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    public record PasswordHash(String hashHex, String saltHex, int iterations) {
    }
}
