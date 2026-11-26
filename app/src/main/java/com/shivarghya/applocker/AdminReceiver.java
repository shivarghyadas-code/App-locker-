package com.shivarghya.applocker;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Uninstall protection. While App Locker is a "device admin", Android refuses to uninstall it
 * (from the home screen too) until it is switched off in Settings, and Settings needs the
 * App Locker password.
 */
public class AdminReceiver extends DeviceAdminReceiver {

    static ComponentName component(Context c) { return new ComponentName(c, AdminReceiver.class); }

    static boolean isOn(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(component(c));
    }

    static Intent enableIntent(Context c) {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(c))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Stops anyone from deleting App Locker. App Locker uses this only to block "
                        + "uninstalling. It cannot erase or read anything on the phone.");
    }

    @Override
    public CharSequence onDisableRequested(Context c, Intent i) {
        return "If you turn this off, anyone can delete App Locker and open the bank apps.";
    }
}
