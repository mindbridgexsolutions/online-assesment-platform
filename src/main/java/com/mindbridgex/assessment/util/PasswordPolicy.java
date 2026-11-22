package com.mindbridgex.assessment.util;

import com.mindbridgex.assessment.exception.AppException;

import java.security.SecureRandom;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    /** Returns an error message, or null when the password is acceptable. */
    public static String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters long.";
        }
        if (password.length() > 72) {
            return "Password must be at most 72 characters long.";
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return "Password must contain at least one letter and one digit.";
        }
        return null;
    }

    public static void require(String password) {
        String err = validate(password);
        if (err != null) {
            throw AppException.bad(err);
        }
    }

    /** Random initial password that always satisfies {@link #validate(String)}. */
    public static String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        sb.append(RANDOM.nextInt(10));
        sb.insert(RANDOM.nextInt(sb.length()), "a");
        return sb.toString();
    }
}
