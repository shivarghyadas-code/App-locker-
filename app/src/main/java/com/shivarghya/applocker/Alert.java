package com.shivarghya.applocker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SmsManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Theft alert: after many wrong passwords, silently texts the phone's location to family numbers.
 * Sent in the background with no screen or sound, so whoever is holding the phone doesn't see it.
 */
final class Alert {
    private Alert() {}

    /** 5 wrong → 15 min wait; 10 → 1 hour wait; 15 → SOS (SMS + call), then again every 5 more (20, 25…). */
    static final int FIRST_ALERT_AT = Store.MAX_TRIES * 3;
    private static final long MIN_GAP_MS = 2 * 60_000L;
    private static long lastSentAt = -MIN_GAP_MS;

    static boolean shouldAlert(int wrongTries) {
        return wrongTries >= FIRST_ALERT_AT && wrongTries % Store.MAX_TRIES == 0;
    }

    // ---------- settings ----------

    static boolean on(Context c) { return Store.sp(c).getBoolean("alert_on", true); }
    static void setOn(Context c, boolean v) { Store.sp(c).edit().putBoolean("alert_on", v).apply(); }

    static String numbersText(Context c) { return Store.sp(c).getString("alert_numbers", ""); }

    /** Cleans and saves up to 3 numbers. Returns how many were saved. */
    static int setNumbers(Context c, String raw) {
        List<String> nums = parse(raw);
        Store.sp(c).edit().putString("alert_numbers", String.join(", ", nums)).apply();
        return nums.size();
    }

    static List<String> numbers(Context c) { return parse(numbersText(c)); }

    private static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[,;\\n]")) {
            String d = part.replaceAll("[^0-9+]", "");
            if (d.startsWith("+91")) d = d.substring(3);
            else if (d.startsWith("0")) d = d.substring(1);
            if (d.matches("[6-9][0-9]{9}") && !out.contains(d)) out.add(d);
            if (out.size() == 3) break;
        }
        return out;
    }

    static boolean canSend(Context c) {
        return c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------- sending ----------

    /** Wrong-password alert: waits up to 12 s for an exact location, then sends with the best one. */
    static void trigger(Context ctx, int wrongTries) {
        Context c = ctx.getApplicationContext();
        if (!on(c) || numbers(c).isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastSentAt < MIN_GAP_MS) return;
        lastSentAt = now;
        if (!canSend(c)) { callFirst(c); return; }   // no SMS permission: at least ring the family
        String reason = wrongTries + " wrong password tries";
        locateThenSend(c, THEFT, reason, (n, where) -> {
            // Remembered so the lock screen can warn that the location has gone out.
            if (n > 0) Store.sp(c).edit().putLong("alert_last_at", System.currentTimeMillis()).apply();
            callFirst(c);     // SOS: then ring the first emergency number
        });
    }

    static void test(Context ctx) {
        locateThenSend(ctx.getApplicationContext(), THEFT, "TEST message (no danger)", null);
    }

    /** Result shown on the SOS screen: how many numbers, and how exact the location was. */
    interface Done { void sent(int numbers, Location where); }

    private static final String THEFT = "App Locker ALERT: phone may be stolen! ";
    private static final String SOS = "SOS! I need help. ";

    /** SOS pressed by the phone's owner: sends straight away with the best location found in ≤12 s. */
    static void sos(Context ctx, Done done) {
        locateThenSend(ctx.getApplicationContext(), SOS, "Sent from SOS button", done);
    }

    private static void locateThenSend(Context c, String headline, String reason, Done done) {
        final Handler h = new Handler(Looper.getMainLooper());
        final Location[] best = {lastKnown(c)};
        final boolean[] sent = {false};
        final Loc[] loc = {null};
        final Runnable send = () -> {
            if (sent[0]) return;
            sent[0] = true;
            if (loc[0] != null) loc[0].stop();
            int n = sendSms(c, headline, reason, best[0]);
            if (done != null) done.sent(n, best[0]);
        };
        loc[0] = new Loc(c, l -> {
            if (Loc.isFake(l)) return;
            if (best[0] == null || l.getAccuracy() < best[0].getAccuracy() || age(best[0]) > 120_000) best[0] = l;
            if (l.getAccuracy() <= 40) h.post(send);
        });
        if (!loc[0].start()) loc[0] = null;
        h.postDelayed(send, loc[0] == null ? 0 : 12_000);
    }

    @SuppressWarnings("MissingPermission")
    private static Location lastKnown(Context c) {
        Location best = null;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null || !Loc.hasPermission(c)) return null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (Exception ignored) {}
        return best;
    }

    private static long age(Location l) {
        return (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000L;
    }

    private static int sendSms(Context c, String headline, String reason, Location l) {
        String time = new SimpleDateFormat("d MMM, h:mm a", Locale.US).format(new Date());
        StringBuilder m = new StringBuilder(headline)
                .append(reason).append(" at ").append(time).append(". ");
        if (l != null) {
            long mins = age(l) / 60_000L;
            m.append("Location: https://maps.google.com/?q=")
                    .append(String.format(Locale.US, "%.6f,%.6f", l.getLatitude(), l.getLongitude()))
                    .append(" (±").append(Math.round(l.getAccuracy())).append(" m");
            if (mins >= 2) m.append(", ").append(mins).append(" min old");
            m.append("). ");
        } else {
            m.append("Location not available (GPS off). ");
        }
        m.append("Call 112 / police.");
        int sent = 0;
        try {
            SmsManager sms = Build.VERSION.SDK_INT >= 31 ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(m.toString());
            for (String n : numbers(c)) {
                try { sms.sendMultipartTextMessage(n, null, parts, null, null); sent++; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return sent;
    }

    /** Rings the first saved number (needs the Phone permission; never 112, which apps may not auto-dial). */
    static void callFirst(Context c) {
        List<String> nums = numbers(c);
        if (nums.isEmpty() || c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return;
        try {
            c.startActivity(new android.content.Intent(android.content.Intent.ACTION_CALL,
                    android.net.Uri.parse("tel:" + nums.get(0))).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
    }

    /** 9876543210 → 98•••••210 (shown on the lock screen without giving the full number away). */
    static String masked(String n) {
        return n.length() < 6 ? n : n.substring(0, 2) + "•••••" + n.substring(n.length() - 3);
    }

    static String mapsLink(Location l) {
        return "https://maps.google.com/?q=" + String.format(Locale.US, "%.6f,%.6f", l.getLatitude(), l.getLongitude());
    }
}
