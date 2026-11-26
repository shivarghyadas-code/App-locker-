package com.shivarghya.applocker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;

/** Gets the phone's location from GPS and the network, while a screen of App Locker is open. */
final class Loc implements LocationListener {

    interface Callback { void onFix(Location l); }

    private final Context c;
    private final LocationManager lm;
    private final Callback cb;
    private boolean running;

    Loc(Context c, Callback cb) {
        this.c = c.getApplicationContext();
        this.lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        this.cb = cb;
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean enabled(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled()) return false;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    static boolean isFake(Location l) {
        if (Build.VERSION.SDK_INT >= 31) return l.isMock();
        return l.isFromMockProvider();
    }

    /** @return false if there is no permission or location is switched off. */
    @SuppressWarnings("MissingPermission")
    boolean start() {
        if (lm == null || !hasPermission(c) || !enabled(c)) return false;
        running = true;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(p)) continue;
                lm.requestLocationUpdates(p, 0, 0, this, Looper.getMainLooper());
                Location last = lm.getLastKnownLocation(p);
                // A very recent fix can be used straight away (opening a money app twice in a row).
                if (last != null && ageMs(last) < 60_000) cb.onFix(last);
            } catch (Exception ignored) {}
        }
        return true;
    }

    void stop() {
        if (!running) return;
        running = false;
        try { lm.removeUpdates(this); } catch (Exception ignored) {}
    }

    private static long ageMs(Location l) {
        return (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000L;
    }

    @Override public void onLocationChanged(Location l) { if (running && l != null) cb.onFix(l); }
    // All four written out: on Android 10 these are not default methods yet.
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
}
