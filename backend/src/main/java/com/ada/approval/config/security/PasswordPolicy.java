package com.ada.approval.config.security;

import com.ada.approval.utils.AssertUtil;

import java.nio.charset.StandardCharsets;

/**
 * Validates the minimum password length and BCrypt-compatible UTF-8 byte limit. The byte limit
 * avoids silently truncating different passwords to the same BCrypt input.
 */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        AssertUtil.isTrue(
                password == null || password.trim().isEmpty() || password.length() < 12,
                "Password must contain at least 12 characters");
        AssertUtil.isTrue(
                password.getBytes(StandardCharsets.UTF_8).length > 72,
                "Password must not exceed 72 entriesUTF-8 bytes");
    }
}
