package com.shivarghya.applocker;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The password screen shown on top of a locked app. */
public class LockActivity extends Activity {

    static final String EXTRA_PKG = "pkg";
    static volatile boolean visible;

    private final Handler h = new Handler(Looper.getMainLooper());
    private String target;
    private ImageView icon;
    private TextView title, msg;
    private EditText pw;
    private Button btn;
    private boolean checking;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // No screenshots / screen recording / Recents preview of this screen.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        buildUi();
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i);
    }

    private void handle(Intent i) {
        String t = i == null ? null : i.getStringExtra(EXTRA_PKG);
        target = t == null ? getPackageName() : t;
        PackageManager pm = getPackageManager();
        String label;
        Drawable d = null;
        if (target.equals(getPackageName())) {
            label = "App Locker settings";
            d = getApplicationInfo().loadIcon(pm);
        } else {
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString();
                d = pm.getApplicationIcon(target);
            } catch (Exception e) {
                label = "This app";
            }
        }
        icon.setImageDrawable(d);
        title.setText(label + " is locked");
        pw.setText("");
        msg.setTextColor(0xFFB0BEC5);
        msg.setText("");
        refreshWait();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        refreshWait();
        pw.requestFocus();
        h.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && pw.isEnabled()) imm.showSoftInput(pw, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() { goHome(); }

    private void goHome() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
        finish();
        overridePendingTransition(0, 0);
    }

    // ---------- unlocking ----------

    private void tryUnlock() {
        if (checking || Store.waitLeftMs(this) > 0) return;
        final String p = pw.getText().toString();
        if (p.isEmpty()) return;
        checking = true;
        btn.setEnabled(false);
        btn.setText("Checking…");
        new Thread(() -> {
            boolean ok = Store.check(this, p);
            h.post(() -> done(ok));
        }).start();
    }

    private void done(boolean ok) {
        checking = false;
        btn.setText("Unlock");
        btn.setEnabled(true);
        pw.setText("");
        if (ok) {
            Store.resetFails(this);
            if (target.equals(getPackageName())) {
                MainActivity.adminOk = true;
                MainActivity.stoppedAt = 0;
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                LockService.allow(target);
            }
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        int left = Store.fail(this);
        msg.setTextColor(0xFFFF8A80);
        if (left > 0) {
            msg.setText("Wrong password. " + left + (left == 1 ? " try" : " tries") + " left before a wait.\nভুল পাসওয়ার্ড");
        }
        refreshWait();
        pw.animate().translationX(24).setDuration(50).withEndAction(() ->
                pw.animate().translationX(-24).setDuration(50).withEndAction(() ->
                        pw.animate().translationX(0).setDuration(50)));
    }

    private final Runnable tick = this::refreshWait;

    private void refreshWait() {
        h.removeCallbacks(tick);
        long left = Store.waitLeftMs(this);
        boolean waiting = left > 0;
        pw.setEnabled(!waiting);
        btn.setEnabled(!waiting && !checking);
        if (waiting) {
            long s = (left + 999) / 1000;
            String t = s >= 60 ? (s / 60) + " min " + (s % 60) + " s" : s + " s";
            msg.setTextColor(0xFFFF8A80);
            msg.setText("Too many wrong tries.\nTry again in " + t);
            h.postDelayed(tick, 1000);
        } else if (msg.getText().toString().startsWith("Too many")) {
            msg.setText("");
        }
    }

    // ---------- layout ----------

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFF0F172A);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(28), dp(56), dp(28), dp(24));
        sv.addView(col, new ScrollView.LayoutParams(-1, -1));

        TextView brand = new TextView(this);
        brand.setText("🔒  App Locker");
        brand.setTextColor(0xFF93C5FD);
        brand.setTextSize(15);
        col.addView(brand);

        icon = new ImageView(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(84), dp(84));
        ilp.topMargin = dp(36);
        col.addView(icon, ilp);

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(4));
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Enter password  ·  পাসওয়ার্ড দিন");
        sub.setTextColor(0xFFCBD5E1);
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub);

        pw = new EditText(this);
        pw.setSingleLine(true);
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pw.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        pw.setTextColor(Color.WHITE);
        pw.setTextSize(20);
        pw.setGravity(Gravity.CENTER);
        pw.setHint("Password");
        pw.setHintTextColor(0xFF64748B);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0xFF334155);
        pw.setBackground(bg);
        pw.setPadding(dp(16), dp(14), dp(16), dp(14));
        pw.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                tryUnlock();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(28);
        col.addView(pw, plp);

        CheckBox show = new CheckBox(this);
        show.setText("Show password");
        show.setTextColor(0xFFCBD5E1);
        show.setOnCheckedChangeListener((v, on) -> {
            int sel = pw.getSelectionEnd();
            pw.setInputType(InputType.TYPE_CLASS_TEXT | (on ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            pw.setTypeface(Typeface.DEFAULT);
            pw.setSelection(Math.max(0, Math.min(sel, pw.length())));
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.gravity = Gravity.START;
        slp.topMargin = dp(6);
        col.addView(show, slp);

        btn = new Button(this);
        btn.setText("Unlock");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18);
        btn.setAllCaps(false);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xFF2563EB);
        bb.setCornerRadius(dp(12));
        btn.setBackground(bb);
        btn.setOnClickListener(v -> tryUnlock());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(54));
        blp.topMargin = dp(14);
        col.addView(btn, blp);

        msg = new TextView(this);
        msg.setTextSize(15);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, dp(16), 0, 0);
        col.addView(msg);

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        Button home = new Button(this);
        home.setText("Go to home screen");
        home.setAllCaps(false);
        home.setTextColor(0xFFCBD5E1);
        home.setBackgroundColor(Color.TRANSPARENT);
        home.setOnClickListener(v -> goHome());
        col.addView(home);

        setContentView(sv);
        // Ignore taps sent through a see-through window drawn on top by another app.
        sv.setFilterTouchesWhenObscured(true);
    }
}
