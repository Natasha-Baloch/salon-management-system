package com.salon;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Salted PBKDF2-WithHmac-SHA256 password hashing (JDK built in, no extra libraries). */
public final class Passwords {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] h = derive(password.toCharArray(), salt, ITERATIONS);
        Base64.Encoder e = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + e.encodeToString(salt) + "$" + e.encodeToString(h);
    }

    public static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            byte[] actual = derive(password.toCharArray(), salt, Integer.parseInt(p[1]));
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    /** @return null when the password is acceptable, else a message. */
    public static String policyError(String pw) {
        if (pw == null || pw.length() < 8) return "Password must be at least 8 characters.";
        boolean letter = false, digit = false;
        for (char c : pw.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        return letter && digit ? null : "Password must contain letters and numbers.";
    }

    private static byte[] derive(char[] pw, byte[] salt, int iterations) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(pw, salt, iterations, KEY_BITS)).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }

    /** Helper used when generating the demo hashes for the SQL script. */
    public static void main(String[] a) {
        for (String s : a) System.out.println(hash(s));
    }
}
