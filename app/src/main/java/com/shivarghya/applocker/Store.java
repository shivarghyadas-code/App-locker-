package com.shivarghya.applocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Settings and the password. The password itself is never stored: only a salted
 * PBKDF2-SHA256 hash, so reading the phone's files does not reveal it.
 */
final class Store {
    private Store() {}

    private static final int ITERATIONS = 40_000;
    static final int MAX_TRIES = 5;
    private static volatile Set<String> lockedCache;

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("locker", Context.MODE_PRIVATE);
    }

    // ---------- password ----------

    static boolean hasPassword(Context c) { return sp(c).contains("pw_hash"); }

    /** null if OK, otherwise the reason it is too weak. */
    static String weakness(String pw) {
        if (pw.length() < 8) return "Use at least 8 characters.";
        boolean letter = false, digit = false;
        for (char ch : pw.toCharArray()) {
            if (Character.isLetter(ch)) letter = true;
            else if (Character.isDigit(ch)) digit = true;
        }
        if (!letter || !digit) return "Use both letters and numbers.";
        if (pw.toLowerCase().matches(".*(1234|abcd|pass|qwer|0000|1111).*"))
            return "Too easy to guess. Avoid 1234, abcd, password…";
        return null;
    }

    /** "Time password": the clock time (HHMM) followed by secret digits, e.g. 12:41 → 1241 + 48153062. */
    static boolean timeMode(Context c) { return "time".equals(sp(c).getString("pw_mode", "fixed")); }

    static String timeWeakness(String digits) {
        return digits.matches("\\d{4,12}") ? null : "Type 4 to 12 digits (numbers only).";
    }

    static void setTimePassword(Context c, String digits4) {
        setPassword(c, digits4);
        sp(c).edit().putString("pw_mode", "time").putInt("pw_time_len", digits4.length()).commit();
    }

    /** What the clock may show now (or a minute ago, if it changed while typing): 1241, 0105, 105, 1305… */
    static java.util.Set<String> timePrefixes() {
        java.util.Set<String> out = new java.util.HashSet<>();
        long now = System.currentTimeMillis();
        for (long t : new long[]{now, now - 60_000L}) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(t);
            int h24 = cal.get(java.util.Calendar.HOUR_OF_DAY), m = cal.get(java.util.Calendar.MINUTE);
            int h12 = h24 % 12 == 0 ? 12 : h24 % 12;
            for (int h : new int[]{h24, h12}) {
                out.add(String.format(java.util.Locale.US, "%02d%02d", h, m));
                out.add(String.format(java.util.Locale.US, "%d%02d", h, m));
            }
        }
        return out;
    }

    static void setPassword(Context c, String pw) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(pw, salt, ITERATIONS);
        sp(c).edit()
                .putString("pw_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("pw_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                .putInt("pw_iter", ITERATIONS)
                .putString("pw_mode", "fixed")
                .putInt("fails", 0).putLong("lockout_until", 0)
                .commit();
    }

    /** Slow on purpose (~0.2 s). Call off the main thread. */
    static boolean check(Context c, String pw) {
        if (timeMode(c)) {
            String d = pw.trim();
            int len = sp(c).getInt("pw_time_len", 4);            // how many fixed digits come after the time
            if (!d.matches("\\d+") || d.length() < len + 3 || d.length() > len + 4) return false;
            String prefix = d.substring(0, d.length() - len);
            if (!timePrefixes().contains(prefix)) return false;
            return checkSecret(c, d.substring(d.length() - len));
        }
        return checkSecret(c, pw);
    }

    private static boolean checkSecret(Context c, String pw) {
        SharedPreferences s = sp(c);
        String h = s.getString("pw_hash", null), salt = s.getString("pw_salt", null);
        if (h == null || salt == null) return false;
        byte[] test = pbkdf2(pw, Base64.decode(salt, Base64.NO_WRAP), s.getInt("pw_iter", ITERATIONS));
        return MessageDigest.isEqual(test, Base64.decode(h, Base64.NO_WRAP));
    }

    private static byte[] pbkdf2(String pw, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, iter, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- wrong-password wait ----------

    /** Records a wrong try. Returns how many tries are left before a wait (0 = waiting now). */
    static int fail(Context c) {
        SharedPreferences s = sp(c);
        int n = s.getInt("fails", 0) + 1;
        long until = 0;
        if (n >= MAX_TRIES) {
            long secs = Math.min(30L << Math.min(n - MAX_TRIES, 5), 15 * 60L); // 30 s, 1 m, 2 m … 15 m
            until = System.currentTimeMillis() + secs * 1000L;
        }
        s.edit().putInt("fails", n).putLong("lockout_until", until).apply();
        return Math.max(0, MAX_TRIES - n);
    }

    static int fails(Context c) { return sp(c).getInt("fails", 0); }

    static void resetFails(Context c) {
        sp(c).edit().putInt("fails", 0).putLong("lockout_until", 0).apply();
    }

    static long waitLeftMs(Context c) {
        long left = sp(c).getLong("lockout_until", 0) - System.currentTimeMillis();
        return Math.max(0, Math.min(left, 15 * 60_000L)); // clamp if the clock was moved back
    }

    // ---------- which apps ----------

    static boolean hasLockedList(Context c) { return sp(c).contains("locked"); }

    static Set<String> locked(Context c) {
        Set<String> s = lockedCache;
        if (s == null) {
            s = Collections.unmodifiableSet(new HashSet<>(sp(c).getStringSet("locked", new HashSet<>())));
            lockedCache = s;
        }
        return s;
    }

    static void setLocked(Context c, Set<String> pkgs) {
        Set<String> copy = new HashSet<>(pkgs);
        sp(c).edit().putStringSet("locked", copy).apply();
        lockedCache = Collections.unmodifiableSet(copy);
    }

    static boolean protectSettings(Context c) { return sp(c).getBoolean("protect_settings", true); }

    static void setProtectSettings(Context c, boolean on) {
        sp(c).edit().putBoolean("protect_settings", on).apply();
    }

    static boolean isLocked(Context c, String pkg) {
        return locked(c).contains(pkg) || isSystemLocked(c, pkg);
    }

    static boolean isSystemLocked(Context c, String pkg) {
        return Known.SYSTEM_PROTECTED.contains(pkg) && protectSettings(c);
    }

    /** Lock an open money app that has not been touched for this long. */
    static long idleMs(Context c) { return sp(c).getLong("idle_ms", 60_000L); }

    static void setIdleMs(Context c, long ms) { sp(c).edit().putLong("idle_ms", ms).apply(); }
}
