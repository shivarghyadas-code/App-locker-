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

    /** 5 wrong tries → first wait; 3 more wrong → alert (8), then again every 3 more (11, 14…). */
    static final int FIRST_ALERT_AT = Store.MAX_TRIES + 3;
    private static final long MIN_GAP_MS = 2 * 60_000L;
    private static long lastSentAt = -MIN_GAP_MS;

    static boolean shouldAlert(int wrongTries) {
        return wrongTries >= FIRST_ALERT_AT && (wrongTries - FIRST_ALERT_AT) % 3 == 0;
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
        if (!on(c) || !canSend(c) || numbers(c).isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastSentAt < MIN_GAP_MS) return;
        lastSentAt = now;
        String reason = wrongTries + " wrong password tries";
        locateThenSend(c, reason);
    }

    static void test(Context ctx) {
        locateThenSend(ctx.getApplicationContext(), "TEST message (no danger)");
    }

    private static void locateThenSend(Context c, String reason) {
        final Handler h = new Handler(Looper.getMainLooper());
        final Location[] best = {lastKnown(c)};
        final boolean[] sent = {false};
        final Loc[] loc = {null};
        final Runnable send = () -> {
            if (sent[0]) return;
            sent[0] = true;
            if (loc[0] != null) loc[0].stop();
            sendSms(c, reason, best[0]);
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

    private static void sendSms(Context c, String reason, Location l) {
        String time = new SimpleDateFormat("d MMM, h:mm a", Locale.US).format(new Date());
        StringBuilder m = new StringBuilder("App Locker ALERT: phone may be stolen! ")
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
        try {
            SmsManager sms = Build.VERSION.SDK_INT >= 31 ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(m.toString());
            for (String n : numbers(c)) sms.sendMultipartTextMessage(n, null, parts, null, null);
        } catch (Exception ignored) {}
    }
}
