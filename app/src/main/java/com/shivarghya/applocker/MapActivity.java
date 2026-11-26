package com.shivarghya.applocker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Pick a trusted place on the map (OpenStreetMap) and choose how big it is. */
public class MapActivity extends Activity {

    private WebView web;
    private TextView radiusText;
    private EditText name;
    private volatile double pickLat = Double.NaN, pickLon = Double.NaN;
    private int radius = 150;
    private Loc loc;
    private boolean pageReady, wantCenterOnMe = true;
    private Location lastMe;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!MainActivity.adminOk) { finish(); return; }     // only from unlocked App Locker
        setTitle("Add trusted place");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString("AppLocker/1.1 (Android; github.com/shivarghyadas-code/app-locker-) " + ws.getUserAgentString());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                pageReady = true;
                for (NightLock.Place p : NightLock.places(MapActivity.this)) {
                    js(String.format(Locale.US, "addExisting(%f,%f,%d,%s)", p.lat, p.lon, p.radius, quote(p.name)));
                }
                js("setRadius(" + radius + ")");
                if (lastMe != null) onMe(lastMe);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        panel.setPadding(p, dp(10), p, dp(12));
        panel.setBackgroundColor(Color.WHITE);

        Button me = new Button(this);
        me.setText("📍 Put pin at my current location");
        me.setAllCaps(false);
        me.setOnClickListener(v -> {
            if (lastMe == null) {
                Toast.makeText(this, "Finding your location… turn on GPS if it is off", Toast.LENGTH_SHORT).show();
                wantCenterOnMe = true;
                startLoc();
            } else {
                js(String.format(Locale.US, "setPoint(%f,%f,17)", lastMe.getLatitude(), lastMe.getLongitude()));
            }
        });
        panel.addView(me);

        name = new EditText(this);
        name.setHint("Name (Home, Auto stand, Petrol pump…)");
        name.setSingleLine(true);
        panel.addView(name);

        radiusText = new TextView(this);
        radiusText.setTextSize(15);
        radiusText.setTextColor(0xFF212121);
        radiusText.setPadding(0, dp(8), 0, 0);
        panel.addView(radiusText);

        SeekBar sb = new SeekBar(this);
        sb.setMax(19);                       // 50 m … 1000 m in 50 m steps
        sb.setProgress(radius / 50 - 1);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                radius = (v + 1) * 50;
                showRadius();
                js("setRadius(" + radius + ")");
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        panel.addView(sb);
        showRadius();

        Button save = new Button(this);
        save.setText("Save trusted place");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setTextSize(17);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2563EB);
        bg.setCornerRadius(dp(12));
        save.setBackground(bg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(52));
        slp.topMargin = dp(6);
        save.setOnClickListener(v -> save());
        panel.addView(save, slp);

        root.addView(panel);
        setContentView(root);
        web.loadUrl("file:///android_asset/map.html");
    }

    @Override protected void onResume() { super.onResume(); startLoc(); }

    @Override protected void onPause() {
        if (loc != null) { loc.stop(); loc = null; }
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void startLoc() {
        if (loc != null) return;
        loc = new Loc(this, this::onMe);
        if (!loc.start()) loc = null;
    }

    private void onMe(Location l) {
        if (Loc.isFake(l)) return;
        if (lastMe == null || l.getAccuracy() <= lastMe.getAccuracy() + 20 || l.getTime() - lastMe.getTime() > 10_000) lastMe = l;
        if (!pageReady) return;
        js(String.format(Locale.US, "showMe(%f,%f,%f)", lastMe.getLatitude(), lastMe.getLongitude(), lastMe.getAccuracy()));
        if (wantCenterOnMe && Double.isNaN(pickLat) && lastMe.getAccuracy() <= 100) {
            wantCenterOnMe = false;
            js(String.format(Locale.US, "setPoint(%f,%f,17)", lastMe.getLatitude(), lastMe.getLongitude()));
        }
    }

    private void showRadius() {
        radiusText.setText("Size of the trusted area: " + radius + " m around the pin");
    }

    private void save() {
        if (Double.isNaN(pickLat)) {
            Toast.makeText(this, "Tap the map to put the pin first", Toast.LENGTH_SHORT).show();
            return;
        }
        String n = name.getText().toString().trim();
        if (n.isEmpty()) n = "Place " + (NightLock.places(this).size() + 1);
        NightLock.Place p = new NightLock.Place();
        p.name = n;
        p.lat = pickLat;
        p.lon = pickLon;
        p.radius = radius;
        NightLock.addPlace(this, p);
        MainActivity.stoppedAt = SystemClock.elapsedRealtime();   // back to settings without the password again
        Toast.makeText(this, "Saved: " + n, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void js(String code) {
        if (web != null && pageReady) web.evaluateJavascript(code, null);
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("<", "&lt;") + "'";
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private final class Bridge {
        @JavascriptInterface
        public void onPick(double lat, double lon) {
            pickLat = lat;
            pickLon = lon;
        }
    }
}
