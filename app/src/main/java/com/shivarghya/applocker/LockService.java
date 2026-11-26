package com.shivarghya.applocker;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * Watches which app comes to the front. When a locked app opens, the password screen is
 * put on top of it. It only looks at the app's package name; it never reads screen content.
 *
 * Rules (built for a phone that is snatched while unlocked):
 *  - each unlock is for one app only;
 *  - the app locks again as soon as you leave it (home, another app, recents);
 *  - every app locks again when the screen turns off;
 *  - an open locked app that is not touched for a while (default 1 minute) locks again;
 *  - if the password screen cannot be shown, the app is pushed away to the home screen
 *    instead (fail-closed).
 */
public class LockService extends AccessibilityService {

    static volatile LockService instance;

    private final Handler h = new Handler(Looper.getMainLooper());
    /** Unlocked apps: package → time of last use (elapsedRealtime). */
    private final Map<String, Long> unlocked = new HashMap<>();
    private final Map<String, Boolean> activityCache = new HashMap<>();
    private String fg = "";                  // last app seen in front
    private String lastShown = "";
    private long lastShownAt;
    private BroadcastReceiver screen;

    private static final java.util.Set<String> IGNORE = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.android.systemui", "android", "com.miui.voip", "com.android.incallui"));

    // ---------- lifecycle ----------

    @Override
    protected void onServiceConnected() {
        instance = this;
        screen = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                    unlocked.clear();
                    MainActivity.adminOk = false;
                    h.removeCallbacks(idleCheck);
                } else if (Store.isLocked(LockService.this, fg) && !isUnlocked(fg)) {
                    // Screen back on with a locked app still in front: cover it again.
                    showLock(fg, Intent.ACTION_USER_PRESENT.equals(a));
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, f);
    }

    @Override
    public void onDestroy() {
        instance = null;
        h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screen); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public void onInterrupt() {}

    // ---------- events ----------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (IGNORE.contains(pkg)) return;

        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // A tap, scroll or typing: the person is still using the app.
            if (unlocked.containsKey(pkg) && unlocked.get(pkg) != 0L) unlocked.put(pkg, SystemClock.elapsedRealtime());
            return;
        }

        if (pkg.equals(getPackageName())) {           // our own lock / settings screen
            leaveAllExcept(null);
            fg = pkg;
            return;
        }

        CharSequence cls = e.getClassName();
        boolean userLocked = Store.locked(this).contains(pkg);
        boolean sysLocked = !userLocked && Store.isSystemLocked(this, pkg) && isActivity(pkg, cls);

        if (userLocked || sysLocked) {
            leaveAllExcept(pkg);
            fg = pkg;
            if (isUnlocked(pkg)) {
                unlocked.put(pkg, SystemClock.elapsedRealtime());
                scheduleIdleCheck();
            } else {
                showLock(pkg, true);
            }
        } else if (isActivity(pkg, cls)) {
            // A real screen of another (unlocked) app or the home screen: you left the locked app.
            leaveAllExcept(null);
            fg = pkg;
        }
        // Anything else (keyboard, small pop-ups, floating bubbles) is ignored.
    }

    /** Unlocked apps other than {@code keep} lose their unlock. */
    private void leaveAllExcept(String keep) {
        if (unlocked.isEmpty()) return;
        // Moving between Settings and MIUI's App info / installer screens counts as one place.
        boolean keepSystem = keep != null && Known.SYSTEM_PROTECTED.contains(keep);
        java.util.Iterator<Map.Entry<String, Long>> it = unlocked.entrySet().iterator();
        while (it.hasNext()) {
            String p = it.next().getKey();
            if (p.equals(keep)) continue;
            if (keepSystem && Known.SYSTEM_PROTECTED.contains(p)) continue;
            it.remove();
        }
    }

    private boolean isUnlocked(String pkg) {
        Long last = unlocked.get(pkg);
        if (last == null) return false;
        if (last == 0L) return true;                   // just unlocked / opened from App Locker
        if (SystemClock.elapsedRealtime() - last > Store.idleMs(this)) {
            unlocked.remove(pkg);
            return false;
        }
        return true;
    }

    /** True if this window is a full app screen (an Activity), not a pop-up or overlay. */
    private boolean isActivity(String pkg, CharSequence cls) {
        if (cls == null) return false;
        String key = pkg + "/" + cls;
        Boolean v = activityCache.get(key);
        if (v == null) {
            try {
                getPackageManager().getActivityInfo(new ComponentName(pkg, cls.toString()), 0);
                v = true;
            } catch (Exception ex) {
                v = false;
            }
            if (activityCache.size() > 400) activityCache.clear();
            activityCache.put(key, v);
        }
        return v;
    }

    // ---------- showing the lock ----------

    private void showLock(String pkg, boolean watch) {
        long now = SystemClock.elapsedRealtime();
        if (pkg.equals(lastShown) && now - lastShownAt < 400 && LockActivity.visible) return;
        lastShown = pkg;
        lastShownAt = now;
        h.removeCallbacks(idleCheck);
        try {
            startActivity(new Intent(this, LockActivity.class)
                    .putExtra(LockActivity.EXTRA_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
        } catch (Exception ignored) {}
        if (watch) {
            h.removeCallbacks(watchdog);
            watchPkg = pkg;
            h.postDelayed(watchdog, 1500);
        }
    }

    private String watchPkg = "";
    /** If the phone blocked the password screen, never leave the money app open: go home. */
    private final Runnable watchdog = () -> {
        if (watchPkg.equals(fg) && !LockActivity.visible && !isUnlocked(watchPkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            Toast.makeText(this, "App Locker: open App Locker and allow \"Display pop-up windows "
                    + "while running in background\"", Toast.LENGTH_LONG).show();
        }
    };

    private void scheduleIdleCheck() {
        h.removeCallbacks(idleCheck);
        h.postDelayed(idleCheck, 5_000);
    }

    /** Re-locks an open money app nobody has touched for the idle time. */
    private final Runnable idleCheck = new Runnable() {
        @Override public void run() {
            if (!Store.isLocked(LockService.this, fg) || !unlocked.containsKey(fg)) return;
            if (isUnlocked(fg)) {
                h.postDelayed(this, 5_000);
            } else {
                showLock(fg, true);
            }
        }
    };

    // ---------- called by the app's screens ----------

    /** After the right password: this app may be used until you leave it or stop touching it. */
    static void allow(String pkg) {
        LockService s = instance;
        if (s == null) return;
        s.unlocked.put(pkg, SystemClock.elapsedRealtime());
        s.scheduleIdleCheck();
    }

    /** Opening Settings from inside App Locker (already unlocked) should not ask again. */
    static void preAllow(String... pkgs) {
        LockService s = instance;
        if (s == null) return;
        for (String p : pkgs) s.unlocked.put(p, 0L);
    }

    static boolean isOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return instance != null;
        ComponentName me = new ComponentName(c, LockService.class);
        for (String x : s.split(":")) {
            if (x.equalsIgnoreCase(me.flattenToString()) || x.equalsIgnoreCase(me.flattenToShortString())) return true;
        }
        return instance != null;
    }
}
