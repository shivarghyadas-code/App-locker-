package com.shivarghya.applocker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** App Locker's own screen. Itself protected by the same password. */
public class MainActivity extends Activity {

    static volatile boolean adminOk;
    static volatile long stoppedAt;
    private static final long GRACE_MS = 3 * 60_000L;

    private ListView list;
    private TextView status;
    private Button onBtn;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Store.hasPassword(this)) {
            if (list != null || getWindow().getDecorView().findViewWithTag("pwform") == null) showPasswordForm(false);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!adminOk || (stoppedAt != 0 && now - stoppedAt > GRACE_MS)) {
            adminOk = false;
            startActivity(new Intent(this, LockActivity.class)
                    .putExtra(LockActivity.EXTRA_PKG, getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        stoppedAt = 0;
        if (list == null) buildMain();
        refreshStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adminOk) stoppedAt = SystemClock.elapsedRealtime();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------- create / change password ----------

    private void showPasswordForm(boolean change) {
        list = null;
        ScrollView sv = new ScrollView(this);
        sv.setTag("pwform");
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(32), dp(24), dp(24));
        sv.addView(col);

        TextView t = text(change ? "New password" : "🔒 Create your App Locker password", 22, true);
        col.addView(t);
        col.addView(text("This password will be asked every time PNB ONE, YONO SBI, PhonePe or any "
                + "other locked app is opened.\n\n"
                + "• At least 8 characters, with letters AND numbers (example: Raju2024ab — "
                + "but make your own)\n"
                + "• Do NOT use the phone's screen lock PIN, your UPI PIN, or your birth date\n"
                + "• Write it on paper and keep it at home, not in the auto or the phone cover", 15, false));

        EditText p1 = passField("Password");
        EditText p2 = passField("Type it again");
        col.addView(p1);
        col.addView(p2);

        CheckBox show = new CheckBox(this);
        show.setText("Show password");
        show.setOnCheckedChangeListener((v, on) -> {
            int type = InputType.TYPE_CLASS_TEXT | (on ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD);
            p1.setInputType(type);
            p2.setInputType(type);
            p1.setTypeface(Typeface.DEFAULT);
            p2.setTypeface(Typeface.DEFAULT);
        });
        col.addView(show);

        TextView err = text("", 15, false);
        err.setTextColor(0xFFD32F2F);
        col.addView(err);

        Button save = bigButton("Save password");
        save.setOnClickListener(v -> {
            String a = p1.getText().toString(), c = p2.getText().toString();
            String weak = Store.weakness(a);
            if (weak != null) { err.setText(weak); return; }
            if (!a.equals(c)) { err.setText("The two passwords are not the same."); return; }
            save.setEnabled(false);
            new Thread(() -> {
                Store.setPassword(this, a);
                runOnUiThread(() -> {
                    adminOk = true;
                    stoppedAt = 0;
                    Toast.makeText(this, "Password saved", Toast.LENGTH_SHORT).show();
                    buildMain();
                    refreshStatus();
                });
            }).start();
        });
        col.addView(save);

        if (change) {
            Button cancel = new Button(this);
            cancel.setText("Cancel");
            cancel.setAllCaps(false);
            cancel.setOnClickListener(v -> { buildMain(); refreshStatus(); });
            col.addView(cancel);
        }
        setContentView(sv);
    }

    private EditText passField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(19);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        e.setLayoutParams(lp);
        return e;
    }

    // ---------- main screen ----------

    private void buildMain() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(20), dp(16), dp(8));

        head.addView(text("🔒 App Locker", 24, true));

        // 1. Turn on
        LinearLayout c1 = card(head);
        status = text("", 16, true);
        c1.addView(status);
        onBtn = bigButton("Turn on App Locker");
        onBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Find \"App Locker\" in the list and turn it ON", Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), null);
        });
        c1.addView(onBtn);

        // 2. MIUI settings so the phone never stops it
        LinearLayout c2 = card(head);
        c2.addView(text("Poco / MIUI phone settings (do all 3 once)", 16, true));
        c2.addView(text("Without these, MIUI may block the password screen or stop App Locker.", 14, false));
        Button pop = smallButton("1. Allow pop-up windows");
        pop.setOnClickListener(v -> {
            Toast.makeText(this, "Turn ON \"Display pop-up windows while running in background\"", Toast.LENGTH_LONG).show();
            open(new Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", getPackageName()), appDetails());
        });
        c2.addView(pop);
        Button auto = smallButton("2. Allow Autostart");
        auto.setOnClickListener(v -> {
            Toast.makeText(this, "Turn ON Autostart for App Locker", Toast.LENGTH_LONG).show();
            open(new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")), appDetails());
        });
        c2.addView(auto);
        Button bat = smallButton("3. Battery saver: No restrictions");
        bat.setOnClickListener(v -> {
            Toast.makeText(this, "Choose \"No restrictions\"", Toast.LENGTH_LONG).show();
            open(new Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                    .putExtra("package_name", getPackageName())
                    .putExtra("package_label", "App Locker"), appDetails());
        });
        c2.addView(bat);

        // 3. Safety options
        LinearLayout c3 = card(head);
        c3.addView(text("Safety", 16, true));
        Switch prot = new Switch(this);
        prot.setText("Also lock Settings and uninstall\n(so a thief cannot switch off or remove App Locker)");
        prot.setTextSize(15);
        prot.setChecked(Store.protectSettings(this));
        prot.setOnCheckedChangeListener((v, on) -> {
            Store.setProtectSettings(this, on);
            if (adapter != null) adapter.notifyDataSetChanged();
            if (!on) Toast.makeText(this, "Not recommended: a thief could turn App Locker off", Toast.LENGTH_LONG).show();
        });
        c3.addView(prot);

        c3.addView(text("\nLock an open money app if nobody touches it for:", 15, false));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        long[] ms = {30_000L, 60_000L, 120_000L, 300_000L};
        String[] names = {"30 s", "1 min", "2 min", "5 min"};
        long cur = Store.idleMs(this);
        for (int i = 0; i < ms.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(1000 + i);
            final long val = ms[i];
            rb.setOnCheckedChangeListener((v, on) -> { if (on) Store.setIdleMs(this, val); });
            rg.addView(rb);
            if (ms[i] == cur) rb.setChecked(true);
        }
        c3.addView(rg);
        c3.addView(text("Apps also lock again when you leave them and whenever the screen turns off.", 13, false));

        Button chg = smallButton("Change password");
        chg.setOnClickListener(v -> showPasswordForm(true));
        c3.addView(chg);

        // 4. App list
        TextView lh = text("\nApps to lock", 18, true);
        head.addView(lh);
        head.addView(text("Bank and UPI apps are ticked automatically. Tick any other app you want "
                + "to protect (WhatsApp, Gallery…).", 14, false));

        list = new ListView(this);
        list.setDividerHeight(0);
        list.addHeaderView(head, null, false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        setContentView(list);
        loadApps();
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean on = LockService.isOn(this);
        status.setText(on ? "✅ App Locker is ON\nLocked apps ask for your password."
                : "⚠️ App Locker is OFF\nTap the button → find App Locker → turn it ON → Allow.");
        status.setTextColor(on ? 0xFF2E7D32 : 0xFFC62828);
        onBtn.setText(on ? "Accessibility settings" : "Turn on App Locker");
    }

    private Intent appDetails() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
    }

    /** Opens a settings screen without App Locker asking for the password again. */
    private void open(Intent first, Intent fallback) {
        LockService.preAllow(Known.SYSTEM_PROTECTED.toArray(new String[0]));
        try {
            startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            if (fallback == null) return;
            try { startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
        }
    }

    // ---------- app list ----------

    static final class App {
        String pkg, label;
        boolean money;
        Drawable icon;
    }

    private final List<Object> rows = new ArrayList<>();

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(li, 0);
            Map<String, App> m = new LinkedHashMap<>();
            for (ResolveInfo ri : ris) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || m.containsKey(p)) continue;
                App a = new App();
                a.pkg = p;
                a.label = String.valueOf(ri.activityInfo.applicationInfo.loadLabel(pm));
                a.money = Known.isMoney(p, a.label);
                m.put(p, a);
            }
            List<App> money = new ArrayList<>(), other = new ArrayList<>();
            for (App a : m.values()) (a.money ? money : other).add(a);
            java.util.Comparator<App> byName = (x, y) -> x.label.compareToIgnoreCase(y.label);
            money.sort(byName);
            other.sort(byName);
            runOnUiThread(() -> {
                if (!Store.hasLockedList(this)) {          // first time: lock all money apps
                    Set<String> s = new HashSet<>();
                    for (App a : money) s.add(a.pkg);
                    Store.setLocked(this, s);
                }
                rows.clear();
                rows.add("💰 Money apps (" + money.size() + ")");
                rows.addAll(money);
                rows.add("Other apps");
                rows.addAll(other);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return rows.get(i) instanceof String ? 0 : 1; }
        @Override public boolean isEnabled(int i) { return false; }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            Object o = rows.get(i);
            if (o instanceof String) {
                TextView t = cv instanceof TextView ? (TextView) cv : text("", 15, true);
                t.setPadding(dp(16), dp(18), dp(16), dp(6));
                t.setTextColor(0xFF1E3A8A);
                t.setText((String) o);
                return t;
            }
            App a = (App) o;
            Row r;
            if (cv == null || !(cv.getTag() instanceof Row)) {
                r = new Row();
                LinearLayout l = new LinearLayout(MainActivity.this);
                l.setOrientation(LinearLayout.HORIZONTAL);
                l.setGravity(Gravity.CENTER_VERTICAL);
                l.setPadding(dp(16), dp(8), dp(12), dp(8));
                r.icon = new ImageView(MainActivity.this);
                l.addView(r.icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(14), 0, dp(8), 0);
                r.name = text("", 16, false);
                r.note = text("", 12, false);
                r.note.setTextColor(0xFF757575);
                texts.addView(r.name);
                texts.addView(r.note);
                l.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
                r.box = new CheckBox(MainActivity.this);
                l.addView(r.box);
                l.setTag(r);
                cv = l;
            } else {
                r = (Row) cv.getTag();
            }
            if (a.icon == null) {
                try { a.icon = getPackageManager().getApplicationIcon(a.pkg); } catch (Exception ignored) {}
            }
            r.icon.setImageDrawable(a.icon);
            r.name.setText(a.label);
            boolean sys = Store.isSystemLocked(MainActivity.this, a.pkg);
            boolean on = sys || Store.locked(MainActivity.this).contains(a.pkg);
            r.note.setText(sys ? "Locked by \"Also lock Settings\"" : on ? "Locked" : "");
            r.note.setVisibility(r.note.getText().length() == 0 ? View.GONE : View.VISIBLE);
            r.box.setOnCheckedChangeListener(null);
            r.box.setChecked(on);
            r.box.setEnabled(!sys);
            r.box.setOnCheckedChangeListener((v, checked) -> {
                Set<String> s = new HashSet<>(Store.locked(MainActivity.this));
                if (checked) s.add(a.pkg); else s.remove(a.pkg);
                Store.setLocked(MainActivity.this, s);
                notifyDataSetChanged();
            });
            final CheckBox box = r.box;
            cv.setOnClickListener(v -> { if (box.isEnabled()) box.toggle(); });
            return cv;
        }
    }

    private static final class Row {
        ImageView icon;
        TextView name, note;
        CheckBox box;
    }

    // ---------- small view helpers ----------

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(0xFF212121);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF1F5F9);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private Button bigButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2563EB);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }
}
