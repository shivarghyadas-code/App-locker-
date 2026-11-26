package com.shivarghya.applocker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Which apps count as "money apps" (ticked automatically on first setup). */
final class Known {
    private Known() {}

    static final Set<String> MONEY = new HashSet<>(Arrays.asList(
            "com.Version1",                              // PNB ONE
            "com.fss.pnbpsp",                            // PNB UPI
            "com.sbi.lotusintouch",                      // YONO SBI
            "com.sbi.SBIFreedomPlus",                    // YONO Lite SBI
            "com.sbi.upi",                               // BHIM SBI Pay
            "com.phonepe.app",                           // PhonePe
            "com.phonepe.app.business",
            "com.phonepe.merchant",
            "com.google.android.apps.nbu.paisa.user",    // Google Pay
            "com.google.android.apps.nbu.paisa.merchant",
            "net.one97.paytm",                           // Paytm
            "com.paytm.business",
            "in.org.npci.upiapp",                        // BHIM
            "com.dreamplug.androidapp",                  // CRED
            "com.mobikwik_new",                          // MobiKwik
            "com.csam.icici.bank.imobile",               // ICICI iMobile
            "com.snapwork.hdfc",                         // HDFC
            "com.axis.mobile",                           // Axis
            "com.msf.kbank.mobile",                      // Kotak
            "com.bankofbaroda.mconnect",                 // bob World
            "com.canarabank.mobility",                   // Canara ai1
            "com.unionbankofindia.vyom"                  // Union Vyom
    ));

    /** Apps with a lock of their own that must stay usable (UPI Voice's QR screen for customers). */
    static final Set<String> NEVER_AUTO = new HashSet<>(Arrays.asList(
            "com.shivarghya.upivoice"
    ));

    /** Settings, app info and uninstall screens: locked when "Protect Settings" is on. */
    static final Set<String> SYSTEM_PROTECTED = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.miui.securitycenter",          // MIUI App info / permissions / uninstall
            "com.miui.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
    ));

    private static final String[] WORDS = {"bank", "upi", "yono", "pnb", "sbi", "bhim", "paytm", "phonepe"};

    static boolean isMoney(String pkg, String label) {
        if (NEVER_AUTO.contains(pkg)) return false;
        if (MONEY.contains(pkg)) return true;
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        for (String w : WORDS) if (l.contains(w)) return true;
        // "Google Pay", "Amazon Pay", "Mi Pay"... but not "Display" or "Play Store"
        for (String part : l.split("[^a-z]+")) if (part.equals("pay")) return true;
        return false;
    }
}
