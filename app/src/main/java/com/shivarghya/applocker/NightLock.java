package com.shivarghya.applocker;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Night lock: during the night hours, money apps (and Settings / App Locker itself) open only
 * when the phone is inside a trusted place. Outside, they stay blocked even with the password.
 */
final class NightLock {
    private NightLock() {}

    static final class Place {
        String name;
        double lat, lon;
        int radius;   // metres
    }

    // ---------- settings ----------

    static boolean on(Context c) { return Store.sp(c).getBoolean("night_on", true); }
    static void setOn(Context c, boolean v) { Store.sp(c).edit().putBoolean("night_on", v).apply(); }

    /** Minutes after midnight. Default 10:00 PM – 6:00 AM. */
    static int start(Context c) { return Store.sp(c).getInt("night_start", 22 * 60); }
    static int end(Context c) { return Store.sp(c).getInt("night_end", 6 * 60); }
    static void setStart(Context c, int m) { Store.sp(c).edit().putInt("night_start", m).apply(); }
    static void setEnd(Context c, int m) { Store.sp(c).edit().putInt("night_end", m).apply(); }

    static boolean isNightNow(Context c) {
        if (!on(c)) return false;
        Calendar cal = Calendar.getInstance();
        int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int s = start(c), e = end(c);
        if (s == e) return false;
        return s < e ? (m >= s && m < e) : (m >= s || m < e);
    }

    /** Which locked screens the night rule applies to. */
    static boolean appliesTo(Context c, String pkg) {
        return Store.locked(c).contains(pkg)
                || Known.SYSTEM_PROTECTED.contains(pkg)
                || pkg.equals(c.getPackageName());
    }

    static String fmt(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        String ap = h < 12 ? "AM" : "PM";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, ap);
    }

    static String window(Context c) { return fmt(start(c)) + " – " + fmt(end(c)); }

    // ---------- trusted places ----------

    static List<Place> places(Context c) {
        List<Place> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Store.sp(c).getString("places", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Place p = new Place();
                p.name = o.optString("name", "Place");
                p.lat = o.getDouble("lat");
                p.lon = o.getDouble("lon");
                p.radius = o.optInt("r", 150);
                out.add(p);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void savePlaces(Context c, List<Place> list) {
        JSONArray a = new JSONArray();
        try {
            for (Place p : list) {
                a.put(new JSONObject().put("name", p.name).put("lat", p.lat).put("lon", p.lon).put("r", p.radius));
            }
        } catch (Exception ignored) {}
        Store.sp(c).edit().putString("places", a.toString()).apply();
    }

    static void addPlace(Context c, Place p) {
        List<Place> l = places(c);
        l.add(p);
        savePlaces(c, l);
    }

    static void removePlace(Context c, int index) {
        List<Place> l = places(c);
        if (index >= 0 && index < l.size()) l.remove(index);
        savePlaces(c, l);
    }

    /** The trusted place this location is in, or null. GPS error is allowed for up to 50 m. */
    static Place match(Context c, Location l) {
        float[] d = new float[1];
        float slack = Math.min(l.getAccuracy(), 50f);
        for (Place p : places(c)) {
            Location.distanceBetween(l.getLatitude(), l.getLongitude(), p.lat, p.lon, d);
            if (d[0] <= p.radius + slack) return p;
        }
        return null;
    }
}
