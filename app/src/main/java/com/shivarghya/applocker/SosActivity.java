package com.shivarghya.applocker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SOS: its own app icon, opens WITHOUT the password. Shows the saved emergency numbers and sends
 * the phone's live location to all of them with one tap. Numbers can only be changed inside
 * App Locker (behind the password).
 */
public class SosActivity extends Activity {

    static final String EXTRA_SEND_NOW = "send_now";
    private static final int REQ_PERMS = 21;

    private TextView status;
    private Button sendBtn;
    private boolean sending;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        if (getIntent().getBooleanExtra(EXTRA_SEND_NOW, false)) send();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFFFF5F5);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(28), dp(20), dp(24));
        sv.addView(col);

        TextView t = new TextView(this);
        t.setText("🚨 SOS");
        t.setTextSize(30);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFB71C1C);
        col.addView(t);

        TextView sub = new TextView(this);
        sub.setText("Sends this phone's live location to your emergency numbers.\nবিপদে পড়লে লাল বোতাম টিপুন।");
        sub.setTextSize(16);
        sub.setTextColor(0xFF424242);
        sub.setPadding(0, dp(4), 0, dp(16));
        col.addView(sub);

        sendBtn = new Button(this);
        sendBtn.setText("SEND MY LOCATION\nto all numbers");
        sendBtn.setTextSize(22);
        sendBtn.setTypeface(Typeface.DEFAULT_BOLD);
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setAllCaps(false);
        GradientDrawable red = new GradientDrawable();
        red.setColor(0xFFD32F2F);
        red.setCornerRadius(dp(20));
        sendBtn.setBackground(red);
        sendBtn.setOnClickListener(v -> send());
        col.addView(sendBtn, new LinearLayout.LayoutParams(-1, dp(130)));

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(0xFF212121);
        status.setPadding(0, dp(12), 0, dp(8));
        col.addView(status);

        TextView h = new TextView(this);
        h.setText("Emergency numbers");
        h.setTextSize(18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(0xFF212121);
        h.setPadding(0, dp(12), 0, dp(4));
        col.addView(h);

        List<String> nums = Alert.numbers(this);
        if (nums.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No numbers saved yet.\nOpen App Locker → Theft alert SMS → type the numbers → Save numbers.");
            none.setTextColor(0xFFC62828);
            none.setTextSize(15);
            col.addView(none);
            sendBtn.setEnabled(false);
            sendBtn.setAlpha(0.5f);
        }
        for (String n : nums) col.addView(callRow("📞  " + pretty(n), n, 0xFF2563EB));
        col.addView(callRow("📞  Police / Emergency  112", "112", 0xFF424242));

        TextView note = new TextView(this);
        note.setText("\nTip: you can also send SOS from the notification panel: swipe down → edit tiles → "
                + "add \"SOS\". The numbers can be changed only inside App Locker (needs the password).");
        note.setTextSize(13);
        note.setTextColor(0xFF616161);
        col.addView(note);

        setContentView(sv);
    }

    private Button callRow(String label, String number, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> call(number));
        return b;
    }

    private static String pretty(String n) {
        return n.length() == 10 ? n.substring(0, 5) + " " + n.substring(5) : n;
    }

    private void call(String number) {
        Uri u = Uri.parse("tel:" + number);
        // Emergency numbers (112) always go through the phone's dialer; family numbers ring straight away.
        boolean direct = !number.equals("112")
                && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        try {
            startActivity(new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, u));
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_DIAL, u)); } catch (Exception ignored) {}
        }
    }

    private void send() {
        if (sending) return;
        if (Alert.numbers(this).isEmpty()) return;
        if (!Alert.canSend(this) || !Loc.hasPermission(this)) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CALL_PHONE}, REQ_PERMS);
            if (!Alert.canSend(this)) return;   // location is optional: send without it if refused
        }
        sending = true;
        sendBtn.setEnabled(false);
        sendBtn.setText("Finding location…\nsending in a few seconds");
        status.setText("");
        Alert.sos(this, this::onSent);
    }

    private void onSent(int count, Location where) {
        runOnUiThread(() -> {
            sending = false;
            sendBtn.setEnabled(true);
            sendBtn.setText("SEND AGAIN\n(updated location)");
            String time = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
            if (count == 0) {
                status.setTextColor(0xFFC62828);
                status.setText("❌ Could not send the SMS. Check balance / SIM, and on MIUI set \"Send SMS\" "
                        + "to Allow for App Locker. Call the numbers below.");
                return;
            }
            status.setTextColor(0xFF2E7D32);
            status.setText("✅ Sent to " + count + (count == 1 ? " number" : " numbers") + " at " + time + ".\n"
                    + (where == null ? "Location was not available (turn on GPS and send again)."
                    : "Location sent (±" + Math.round(where.getAccuracy()) + " m): " + Alert.mapsLink(where)));
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_PERMS && Alert.canSend(this) && !sending) send();
    }
}
