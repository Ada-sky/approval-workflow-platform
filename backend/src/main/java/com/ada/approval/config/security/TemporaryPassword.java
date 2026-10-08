package com.ada.approval.config.security;

import java.security.SecureRandom;

/**
 * Generates a random initial account password within the BCrypt input limit. The employee creation
 * response is its only application disclosure; only the encoded value is persisted.
 */
public final class TemporaryPassword {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private TemporaryPassword() {}

    public static String generate() {
        char[] password = new char[24];
        for (int i = 0; i < password.length; i++)
            password[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        return new String(password);
    }
}
