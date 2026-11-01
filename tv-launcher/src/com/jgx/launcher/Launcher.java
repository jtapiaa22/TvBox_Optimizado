package com.jgx.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.widget.Toast;
import java.util.HashSet;
import java.util.Set;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Apple TV style launcher: ambient glow, one scrolling dock with every app, one glass highlight. */
public class Launcher extends Activity {
    // package, label override (null = app label), fallback action when the package has no launcher entry
    private static final String[][] DOCK = {
        {"com.jgx.iptv", null, null},
        {"com.esaba.downloader", null, null},
        {"com.android.vending", null, null},
        {"com.android.tv.settings", "Ajustes", Settings.ACTION_SETTINGS},
    };
    // No entran solas al dock: el launcher original, este mismo launcher y utilidades de sistema que nadie abre a
    // proposito (explorador "Descargas" de Android y WifiDisplay de Rockchip). Quita una linea para verlas.
    private static final String[] HIDDEN = {
        "com.droidlogic.mboxlauncher", "com.jgx.launcher",
        "com.android.documentsui", "com.rockchip.wfd",
    };
    private static final int TILE_W = 240, TILE_H = 135, GAP = 28, HL_PAD = 20;
    private static final int MARGIN = 60, BAND_PAD = 30, BAND_H = 196, BAND_BOTTOM = 56;

    private static class Entry {
        String pkg, label;
        Intent intent;
        Drawable icon;
        boolean banner;
        int color = 0xFF3A6EA5;
    }

    private float d;
    private int sw, sh;
    private FrameLayout content;
    private ScrollView scroll;
    private View hl;
    private boolean hlShown;
    private GradientDrawable glow;
    // The glow lives in a fixed layer BEHIND the scrolling content, so the background is the same on every screen.
    private boolean netCable;
    private PillIcon icoWifi, icoEth;
    private FrameLayout root;
    private float glowCx = 0.25f, glowCy = 0.84f;
    private int glowColor = 0x003A6EA5;
    private ValueAnimator glowAnim;
    private TextView dockName, wifiPill, settingsPill;
    private final List<View> dockCells = new ArrayList<>();
    private SharedPreferences prefs;
    private BroadcastReceiver pkgReceiver;
    private boolean longHandled;
    private List<Entry> dockEntries = new ArrayList<>();
    private TextView stats;
    private final Map<View, Runnable> longActions = new HashMap<>();

    private int dp(float v) { return Math.round(v * d); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        DisplayMetrics m = getResources().getDisplayMetrics();
        sw = m.widthPixels;
        sh = m.heightPixels;
        // El diseno esta pensado para 1280x720: escala todo (medidas y textos) segun la pantalla real, no segun la
        // densidad que declare el firmware (que en estas cajas es poco fiable). En 1280x720 vale 1.0.
        DisplayMetrics real = new DisplayMetrics(); // tamano fisico: widthPixels/heightPixels descuentan barras del sistema
        getWindowManager().getDefaultDisplay().getRealMetrics(real);
        d = Math.min(Math.max(real.widthPixels, real.heightPixels) / 1280f, Math.min(real.widthPixels, real.heightPixels) / 720f);
        build();

        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                recreate(); // build() puts a freshly installed app at the front of the dock
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pkgReceiver != null) unregisterReceiver(pkgReceiver);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Home pressed while already on the launcher: back to the first dock app
        if (!dockCells.isEmpty()) {
            scroll.scrollTo(0, 0);
            dockCells.get(0).requestFocus();
        }
    }

    /** Holding OK/Enter (key repeat) runs the focused view's long action; the key-up that follows is swallowed. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int k = ev.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() > 0) {
                if (!longHandled) {
                    View f = scroll.findFocus();
                    Runnable r = f == null ? null : longActions.get(f);
                    if (r != null) {
                        longHandled = true;
                        f.setPressed(false);
                        r.run();
                    }
                }
                return true;
            }
            if (ev.getAction() == KeyEvent.ACTION_UP && longHandled) {
                longHandled = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWifi();
        syncHighlight();
        if (stats != null) {
            stats.removeCallbacks(statsTick);
            stats.post(statsTick);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stats != null) stats.removeCallbacks(statsTick);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) syncHighlight();
    }

    /** After coming back from another app, snap the highlight (and glow) onto the focused tile without animating. */
    private void syncHighlight() {
        if (content == null) return;
        content.post(new Runnable() {
            @Override public void run() { placeHighlight(); }
        });
    }

    private void placeHighlight() {
        View f = scroll.findFocus();
        if (!(f instanceof LinearLayout) || ((LinearLayout) f).getChildCount() == 0) return;
        View img = ((LinearLayout) f).getChildAt(0);
        Rect r = new Rect(0, 0, img.getWidth(), img.getHeight());
        content.offsetDescendantRectToMyCoords(img, r);
        hl.animate().cancel();
        hl.setX(r.left - dp(HL_PAD));
        hl.setY(r.top - dp(HL_PAD));
        hl.setAlpha(1f);
        hlShown = true;
    }

    private void updateWifi() {
        String name = null;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) {
                name = info.getSSID();
                if (name != null) name = name.replace("\"", "");
                if ("<unknown ssid>".equals(name)) name = null;
            }
        } catch (Exception ignored) { }
        int speed = -1;
        try {
            WifiInfo wi = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo();
            if (wi != null) speed = wi.getLinkSpeed();
        } catch (Exception ignored) { }
        boolean cable = false;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            cable = ni != null && ni.isConnected() && ni.getType() == android.net.ConnectivityManager.TYPE_ETHERNET;
        } catch (Exception ignored) { }
        String t;
        if (cable) {
            int eth = -1;
            try { // velocidad del enlace; muchos kernels de TV box no exponen este archivo
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("/sys/class/net/eth0/speed"));
                eth = Integer.parseInt(br.readLine().trim());
                br.close();
            } catch (Exception ignored) { }
            t = "Cable" + (eth > 0 ? "  ·  " + eth + " Mbps" : "");
        } else {
            t = name != null ? "Wi-Fi  ·  " + name : "Wi-Fi";
            if (name != null && speed > 0) t += "  ·  " + speed + " Mbps";
        }
        netCable = cable;
        wifiPill.setText(t);
        wifiPill.setCompoundDrawables(cable ? icoEth : icoWifi, null, null, null);
    }

    /** Iconos de las pastillas, dibujados por codigo (sin recursos): wifi, cable de red, engranaje, chip de RAM. */
    private static final class PillIcon extends Drawable {
        static final int WIFI = 0, ETH = 1, GEAR = 2, CHIP = 3;
        private final int kind;
        private final int size;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        PillIcon(int kind, int size) {
            this.kind = kind;
            this.size = size;
            p.setColor(Color.WHITE);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            setBounds(0, 0, size, size);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }

        @Override public void draw(Canvas c) {
            float s = size;
            p.setStrokeWidth(s * 0.09f);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            if (kind == WIFI) {
                float cx = s * 0.5f, cy = s * 0.82f;
                for (int i = 1; i <= 3; i++) {
                    float r = s * 0.2f * i;
                    c.drawArc(new android.graphics.RectF(cx - r, cy - r, cx + r, cy + r), 225, 90, false, p);
                }
                p.setStyle(android.graphics.Paint.Style.FILL);
                c.drawCircle(cx, cy, s * 0.07f, p);
            } else if (kind == ETH) {
                c.drawRoundRect(new android.graphics.RectF(s * 0.14f, s * 0.14f, s * 0.86f, s * 0.64f), s * 0.08f, s * 0.08f, p);
                for (int i = 0; i < 4; i++) {
                    float x = s * (0.28f + 0.147f * i);
                    c.drawLine(x, s * 0.14f, x, s * 0.34f, p);
                }
                c.drawLine(s * 0.5f, s * 0.64f, s * 0.5f, s * 0.86f, p);
                c.drawLine(s * 0.3f, s * 0.86f, s * 0.7f, s * 0.86f, p);
            } else if (kind == GEAR) {
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.26f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    c.drawLine(s * 0.5f + cs * s * 0.34f, s * 0.5f + sn * s * 0.34f,
                            s * 0.5f + cs * s * 0.45f, s * 0.5f + sn * s * 0.45f, p);
                }
            } else {
                c.drawRoundRect(new android.graphics.RectF(s * 0.18f, s * 0.28f, s * 0.82f, s * 0.72f), s * 0.06f, s * 0.06f, p);
                for (int i = 0; i < 4; i++) {
                    float x = s * (0.28f + 0.147f * i);
                    c.drawLine(x, s * 0.12f, x, s * 0.28f, p);
                    c.drawLine(x, s * 0.72f, x, s * 0.88f, p);
                }
            }
        }
    }

    private PillIcon pillIcon(int kind) { return new PillIcon(kind, dp(24)); }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 6) return "Buenas noches";
        if (h < 13) return "Buenos días";
        if (h < 20) return "Buenas tardes";
        return "Buenas noches";
    }

    // ---------------------------------------------------------------- data

    private List<Entry> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, Entry> byPkg = new HashMap<>();
        String[] cats = {Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER"};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                boolean leanback = cat.endsWith("LEANBACK_LAUNCHER");
                Entry old = byPkg.get(pkg);
                if (old != null && !leanback) continue;
                Entry e = new Entry();
                e.pkg = pkg;
                e.label = String.valueOf(ri.loadLabel(pm));
                e.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Drawable ban = ri.activityInfo.loadBanner(pm);
                if (ban == null) ban = ri.activityInfo.applicationInfo.loadBanner(pm);
                e.banner = ban != null;
                e.icon = ban != null ? ban : ri.loadIcon(pm);
                byPkg.put(pkg, e);
            }
        }
        for (Entry e : byPkg.values()) e.color = sampleColor(e.icon);
        return new ArrayList<>(byPkg.values());
    }

    private Entry dockEntry(String[] spec, Map<String, Entry> all) {
        Entry e = all.get(spec[0]);
        PackageManager pm = getPackageManager();
        if (e == null) {
            e = new Entry();
            e.pkg = spec[0];
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(spec[0], 0);
                e.label = String.valueOf(ai.loadLabel(pm));
                Drawable ban = ai.loadBanner(pm);
                e.banner = ban != null;
                e.icon = ban != null ? ban : ai.loadIcon(pm);
                e.color = sampleColor(e.icon);
            } catch (PackageManager.NameNotFoundException ex) {
                return null;
            }
            Intent i = spec[2] != null ? new Intent(spec[2]) : pm.getLaunchIntentForPackage(spec[0]);
            if (i == null) return null;
            e.intent = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (spec[1] != null) e.label = spec[1];
        return e;
    }

    private int sampleColor(Drawable dr) {
        try {
            Bitmap bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            dr.setBounds(0, 0, 24, 24);
            dr.draw(c);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int x = 0; x < 24; x++) {
                for (int y = 0; y < 24; y++) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
                }
            }
            bmp.recycle();
            if (n == 0) return 0xFF3A6EA5;
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
            hsv[1] = Math.min(1f, Math.max(hsv[1], 0.5f));
            hsv[2] = Math.max(hsv[2], 0.75f);
            return Color.HSVToColor(hsv);
        } catch (Exception ex) {
            return 0xFF3A6EA5;
        }
    }

    // ---------------------------------------------------------------- views

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, float sp, String family) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * d);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setSingleLine(true);
        t.setShadowLayer(dp(4), 0, dp(2), 0xAA000000); // legibility on any glow colour
        return t;
    }

    private void build() {
        List<Entry> apps = loadApps();
        Map<String, Entry> all = new HashMap<>();
        for (Entry e : apps) all.put(e.pkg, e);

        // Todo vive en el dock. Una app que el launcher no conocia entra sola, en primer lugar (la primera vez que corre
        // esta version, las que ya estaban se agregan al final para no desordenar el dock). "Ocultar" la saca y vuelve
        // desde "Apps ocultas". Las de HIDDEN nunca entran.
        Set<String> hidden = prefs.getStringSet("hidden", new HashSet<String>());
        boolean firstRun = !prefs.contains("known");
        Set<String> known = prefs.getStringSet("known", new HashSet<String>());
        List<String> dl = dockPkgs();
        List<Entry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry e : apps) {
            if (isHiddenByDefault(e.pkg)) continue;
            seen.add(e.pkg);
            if (!known.contains(e.pkg) && !dl.contains(e.pkg) && !hidden.contains(e.pkg)) fresh.add(e);
        }
        Collections.sort(fresh, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.label.compareToIgnoreCase(b.label); }
        });
        for (int i = 0; i < fresh.size(); i++) dl.add(firstRun ? dl.size() : i, fresh.get(i).pkg);
        if (!fresh.isEmpty() || firstRun) storeDock(dl);
        if (!seen.equals(known)) prefs.edit().putStringSet("known", seen).apply();

        List<Entry> dock = new ArrayList<>();
        for (String[] spec : dockSpecs()) {
            Entry e = dockEntry(spec, all);
            if (e != null) dock.add(e);
        }
        dockEntries = dock;

        scroll = new ScrollView(this) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
            @Override public boolean arrowScroll(int dir) {
                View cur = findFocus();
                if (cur == null || cur == this) return super.arrowScroll(dir);
                View next = FocusFinder.getInstance().findNextFocus(this, cur, dir);
                if (next != null) next.requestFocus(dir);
                return true; // never scroll on its own: Up/Down move exactly one row
            }
        };
        scroll.setBackgroundColor(Color.TRANSPARENT);   // the black background and the glow are in `root`, behind it
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);

        content = new FrameLayout(this);
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));

        // ambient glow: a fixed full-screen layer behind the scrolling content (it does not scroll away)
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(sw * 0.55f);
        glow.setGradientCenter(glowCx, glowCy);
        glow.setColors(new int[]{0x00000000, 0x00000000});
        View glowView = new View(this);
        glowView.setBackground(glow);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(glowView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // glass highlight
        hl = new View(this);
        hl.setBackground(round(0x38FFFFFF, 22));
        hl.setAlpha(0f);
        content.addView(hl, new FrameLayout.LayoutParams(dp(TILE_W + 2 * HL_PAD), dp(TILE_H + 2 * HL_PAD)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        content.addView(col, new FrameLayout.LayoutParams(-1, -2));

        FrameLayout screen = new FrameLayout(this);
        final LinearLayout.LayoutParams screenLp = new LinearLayout.LayoutParams(-1, sh);
        col.addView(screen, screenLp);
        // the usable window can be shorter than the display (system bars): fit the first screen to it
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = b - t;
                if (h > 0 && h != screenLp.height) {
                    screenLp.height = h;
                    content.post(new Runnable() {
                        @Override public void run() { content.requestLayout(); syncHighlight(); }
                    });
                }
            }
        });

        // greeting, clock, date
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hp.leftMargin = dp(MARGIN);
        hp.topMargin = dp(30);
        screen.addView(head, hp);
        TextView greet = text(greeting(), 26.6f, "sans-serif");
        greet.setAlpha(0.95f);
        head.addView(greet);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 98.8f * d);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(dp(5), 0, dp(3), 0xAA000000);
        head.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d 'de' MMMM");
        date.setFormat12Hour("EEEE d 'de' MMMM");
        date.setTextColor(0xF2FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24.7f * d);
        date.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        date.setShadowLayer(dp(4), 0, dp(2), 0xAA000000);
        head.addView(date);

        // top-right pills
        LinearLayout pills = new LinearLayout(this);
        pills.setClipChildren(false); // the focused pill scales up past its slot
        pills.setClipToPadding(false);
        pills.setPadding(dp(8), dp(6), dp(8), dp(6));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pp.rightMargin = dp(MARGIN);
        pp.topMargin = dp(36);
        screen.addView(pills, pp);
        icoWifi = pillIcon(PillIcon.WIFI);
        icoEth = pillIcon(PillIcon.ETH);
        wifiPill = pill("Wi-Fi");
        wifiPill.setCompoundDrawables(icoWifi, null, null, null);
        settingsPill = pill("Ajustes");
        settingsPill.setCompoundDrawables(pillIcon(PillIcon.GEAR), null, null, null);
        wifiPill.setId(View.generateViewId());
        settingsPill.setId(View.generateViewId());
        final TextView ramPill = pill("Liberar RAM");
        ramPill.setCompoundDrawables(pillIcon(PillIcon.CHIP), null, null, null);
        ramPill.setId(View.generateViewId());
        ramPill.setNextFocusLeftId(ramPill.getId());
        ramPill.setNextFocusUpId(ramPill.getId());
        ramPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { freeRam(); }
        });
        wifiPill.setNextFocusUpId(wifiPill.getId());
        settingsPill.setNextFocusRightId(settingsPill.getId());
        settingsPill.setNextFocusUpId(settingsPill.getId());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
        gp.rightMargin = dp(16);
        pills.addView(ramPill, gp);
        pills.addView(wifiPill, gp);
        pills.addView(settingsPill);
        wifiPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(new Intent(netCable ? Settings.ACTION_SETTINGS : Settings.ACTION_WIFI_SETTINGS)); }
        });
        // long press on Ajustes: list the hidden apps to bring one (or all) back
        longActions.put(settingsPill, new Runnable() {
            @Override public void run() { showHiddenApps(); }
        });
        settingsPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(new Intent(Settings.ACTION_SETTINGS)); }
        });

        stats = text("", 19, "sans-serif");
        stats.setAlpha(0.9f);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.rightMargin = dp(MARGIN + 6);
        sp.topMargin = dp(110);
        screen.addView(stats, sp);

        // dock name above the dock
        dockName = text("", 47.5f, "sans-serif");
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        np.leftMargin = dp(MARGIN + BAND_PAD);
        np.bottomMargin = dp(BAND_BOTTOM + BAND_H + 22);
        screen.addView(dockName, np);

        // dock band
        LinearLayout band = new LinearLayout(this);
        band.setBackground(round(0x1EFFFFFF, 34));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(BAND_H), Gravity.BOTTOM | Gravity.START);
        bp.leftMargin = dp(MARGIN);
        bp.rightMargin = dp(MARGIN); // caps the band width: with many apps the dock scrolls sideways
        bp.bottomMargin = dp(BAND_BOTTOM);
        screen.addView(band, bp);
        HorizontalScrollView dockScroll = new HorizontalScrollView(this) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                // keep room around the focused tile for its highlight inside the band
                r.left -= dp(34);
                r.right += dp(34);
                return super.computeScrollDeltaToGetChildRectOnScreen(r);
            }
        };
        dockScroll.setFocusable(false);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScroll.setClipToPadding(false); // room for the focused tile's scale-up
        dockScroll.setPadding(0, dp(BAND_PAD), 0, dp(BAND_PAD));
        dockScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) { placeHighlight(); }
        });
        band.addView(dockScroll, new LinearLayout.LayoutParams(-2, -1));
        LinearLayout dockRow = new LinearLayout(this);
        dockRow.setPadding(dp(BAND_PAD), 0, dp(6), 0);
        dockScroll.addView(dockRow, new ViewGroup.LayoutParams(-2, -1));
        for (int i = 0; i < dock.size(); i++) {
            View c = cell(dock.get(i));
            c.setNextFocusUpId(wifiPill.getId());
            if (i == 0) c.setNextFocusLeftId(c.getId());
            if (i == dock.size() - 1) c.setNextFocusRightId(c.getId());
            dockRow.addView(c);
            dockCells.add(c);
        }
        ramPill.setNextFocusDownId(dockCells.isEmpty() ? View.NO_ID : dockCells.get(0).getId());
        wifiPill.setNextFocusDownId(dockCells.isEmpty() ? View.NO_ID : dockCells.get(0).getId());
        settingsPill.setNextFocusDownId(dockCells.isEmpty() ? View.NO_ID : dockCells.get(Math.min(dockCells.size() - 1, 3)).getId());

        setContentView(root);

        // restore last focused tile
        final String last = prefs.getString("last", null);
        View start = dockCells.isEmpty() ? null : dockCells.get(0);
        if (last != null) {
            for (View v : dockCells) if (last.equals(v.getTag())) start = v;
        }
        final View first = start;
        if (first != null) content.post(new Runnable() {
            @Override public void run() { first.requestFocus(); }
        });
    }

    private static boolean isHiddenByDefault(String pkg) {
        for (String h : HIDDEN) if (h.equals(pkg)) return true;
        return false;
    }

    private void launch(Entry e) {
        start(e.intent);
    }

    // ---- editable dock (saved as a comma separated package list; defaults to DOCK)

    private List<String> dockPkgs() {
        List<String> l = new ArrayList<>();
        String saved = prefs.getString("dock", null);
        if (saved == null) {
            for (String[] s : DOCK) l.add(s[0]);
        } else {
            for (String p : saved.split(",")) if (!p.isEmpty()) l.add(p);
        }
        return l;
    }

    private List<String[]> dockSpecs() {
        List<String[]> specs = new ArrayList<>();
        for (String p : dockPkgs()) {
            String[] spec = new String[]{p, null, null};
            for (String[] s : DOCK) if (s[0].equals(p)) spec = s;
            specs.add(spec);
        }
        return specs;
    }

    private void storeDock(List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) sb.append(sb.length() > 0 ? "," : "").append(p);
        prefs.edit().putString("dock", sb.toString()).apply();
    }

    private void saveDock(List<String> pkgs) {
        storeDock(pkgs);
        recreate(); // no limit on apps: the dock scrolls sideways
    }

    /** Hides an app: out of the dock and into "Apps ocultas" (it is not uninstalled). */
    private void hideApp(String pkg) {
        Set<String> h = new HashSet<>(prefs.getStringSet("hidden", new HashSet<String>()));
        h.add(pkg);
        prefs.edit().putStringSet("hidden", h).apply();
        List<String> dl = dockPkgs();
        dl.remove(pkg);
        saveDock(dl);
    }

    /** Brings hidden apps back, at the front of the dock. */
    private void unhideApps(List<String> pkgs) {
        Set<String> h = new HashSet<>(prefs.getStringSet("hidden", new HashSet<String>()));
        h.removeAll(pkgs);
        prefs.edit().putStringSet("hidden", h).apply();
        List<String> dl = dockPkgs();
        for (String p : pkgs) if (!dl.contains(p)) dl.add(0, p);
        saveDock(dl);
    }

    private void showHiddenApps() {
        PackageManager pm = getPackageManager();
        final List<String> pkgs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String p : prefs.getStringSet("hidden", new HashSet<String>())) {
            try {
                names.add(String.valueOf(pm.getApplicationInfo(p, 0).loadLabel(pm)));
                pkgs.add(p);
            } catch (PackageManager.NameNotFoundException ignored) { } // uninstalled since
        }
        if (pkgs.isEmpty()) {
            Toast.makeText(this, "No hay apps ocultas", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pkgs.size() > 1) names.add("Mostrar todas");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Apps ocultas")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int which) {
                        unhideApps(which < pkgs.size() ? Collections.singletonList(pkgs.get(which)) : pkgs);
                    }
                }).show();
    }

    // ---- top bar stats: Wi-Fi speed, free RAM, temperature (local reads, no network)

    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            updateStats();
            updateWifi();
            if (stats != null) stats.postDelayed(this, 5000);
        }
    };

    /**
     * Free RAM in kB, from /proc/meminfo: MemFree + Inactive(file).
     * Some cheap TV boxes patch MemTotal, Cached and ActivityManager's numbers upwards (the MXQ Pro 5G reports
     * 4 GB with 1 GB of real RAM), so those fields and the total are deliberately not used.
     */
    private long freeRamKb() {
        long free = -1, active = 0, inactive = 0, avail = -1;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\s+");
                if (p.length < 2) continue;
                if (p[0].equals("MemFree:")) free = Long.parseLong(p[1]);
                else if (p[0].equals("Active(file):")) active = Long.parseLong(p[1]);
                else if (p[0].equals("Inactive(file):")) inactive = Long.parseLong(p[1]);
                else if (p[0].equals("MemAvailable:")) avail = Long.parseLong(p[1]);
            }
            br.close();
        } catch (Exception ignored) { }
        if (free < 0) return -1;
        // MemAvailable (kernel 3.14+) when the kernel offers it; otherwise a conservative estimate: free memory
        // plus the inactive file cache (Active(file) also looked inflated on the MXQ Pro 5G, so it is left out)
        return avail >= 0 ? avail : free + inactive;
    }

    /** Ends cached/background processes of every other app (foreground apps are untouched). */
    private void freeRam() {
        final android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final long before = freeRamKb();
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            if (!ai.packageName.equals(getPackageName())) am.killBackgroundProcesses(ai.packageName);
        }
        Toast.makeText(this, "Liberando RAM…", Toast.LENGTH_SHORT).show();
        stats.postDelayed(new Runnable() {
            @Override public void run() {
                long freed = (freeRamKb() - before) / 1024;
                Toast.makeText(Launcher.this,
                        freed > 20 ? "Se liberaron " + freed + " MB" : "La RAM ya estaba optimizada",
                        Toast.LENGTH_LONG).show();
                updateStats();
            }
        }, 1500);
    }

    private void updateStats() {
        StringBuilder sb = new StringBuilder();
        long kb = freeRamKb();
        if (kb >= 0) {
            long mb = kb / 1024;
            sb.append(mb >= 1000 ? String.format(java.util.Locale.US, "RAM libre %.1f GB", mb / 1024.0)
                    : "RAM libre " + mb + " MB");
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("/sys/class/thermal/thermal_zone0/temp"));
            float t = Float.parseFloat(br.readLine().trim());
            br.close();
            if (t > 1000) t /= 1000f;
            sb.append(String.format(java.util.Locale.US, "   ·   %.0f°C", t));
        } catch (Exception ignored) { }
        if (stats != null) stats.setText(sb.toString());
    }

    private void showMenu(final Entry e) {
        final List<String> items = new ArrayList<>();
        final List<String> dl = dockPkgs();
        final int di = dl.indexOf(e.pkg);
        if (di > 0) items.add("Mover a la izquierda");
        if (di >= 0 && di < dl.size() - 1) items.add("Mover a la derecha");
        if (dl.size() > 1) items.add("Ocultar");
        if (!prefs.getStringSet("hidden", new HashSet<String>()).isEmpty()) items.add("Apps ocultas…");
        items.add("Información de la app");
        items.add("Desinstalar");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e.label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int which) {
                        String it = items.get(which);
                        Uri uri = Uri.parse("package:" + e.pkg);
                        if (it.startsWith("Mover")) {
                            int j = it.endsWith("izquierda") ? di - 1 : di + 1;
                            Collections.swap(dl, di, j);
                            saveDock(dl);
                        } else if (it.equals("Ocultar")) {
                            hideApp(e.pkg);
                        } else if (it.startsWith("Apps ocultas")) {
                            showHiddenApps();
                        } else if (it.startsWith("Informaci")) {
                            start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri));
                        } else {
                            try {
                                boolean sys = (getPackageManager().getApplicationInfo(e.pkg, 0).flags
                                        & ApplicationInfo.FLAG_SYSTEM) != 0;
                                if (sys) {
                                    Toast.makeText(Launcher.this, "App del sistema: no se puede desinstalar",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    start(new Intent(Intent.ACTION_DELETE, uri));
                                }
                            } catch (PackageManager.NameNotFoundException ignored) { }
                        }
                    }
                }).show();
    }

    private TextView pill(String s) {
        final TextView t = text(s, 20.9f, "sans-serif-medium");
        t.setPadding(dp(24), dp(12), dp(26), dp(12));
        t.setCompoundDrawablePadding(dp(10));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackground(round(0x2EFFFFFF, 30));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                t.setBackground(round(has ? 0x80FFFFFF : 0x2EFFFFFF, 30));
                t.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(150).start();
                if (has) {
                    hl.animate().alpha(0f).setDuration(120).start();
                    hlShown = false;
                    dockName.setText("");
                    scroll.smoothScrollTo(0, 0);
                }
            }
        });
        return t;
    }

    private View cell(final Entry e) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setId(View.generateViewId());
        c.setTag(e.pkg);
        c.setFocusable(true);
        c.setClickable(true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(TILE_W), -2);
        cp.rightMargin = dp(GAP);
        c.setLayoutParams(cp);

        final ImageView img = new ImageView(this);
        if (e.banner) {
            img.setBackground(round(0xFF1C1C1E, 16));
        } else {
            // sin banner: fondo con un tinte oscuro del color del icono, para que el tile no sea un rectangulo gris plano
            int dark = Color.rgb(Color.red(e.color) * 40 / 100 + 14, Color.green(e.color) * 40 / 100 + 14, Color.blue(e.color) * 40 / 100 + 16);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{dark, 0xFF16161B});
            bg.setCornerRadius(dp(16));
            img.setBackground(bg);
        }
        img.setClipToOutline(true);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16)); }
        });
        img.setImageDrawable(e.icon);
        if (e.banner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(dp(36), dp(20), dp(36), dp(20));
        }
        c.addView(img, new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H)));

        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(e); }
        });
        longActions.put(c, new Runnable() {
            @Override public void run() { showMenu(e); }
        });
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                float s = has ? 1.1f : 1f;
                img.animate().scaleX(s).scaleY(s).setDuration(180).start();
                if (!has) return;
                prefs.edit().putString("last", e.pkg).apply();
                moveHighlight(img);
                dockName.setText(e.label);
                setGlow(e.color, img, 0);
                scroll.smoothScrollTo(0, 0);
            }
        });
        return c;
    }

    private void moveHighlight(final View img) {
        final Rect r = new Rect(0, 0, img.getWidth(), img.getHeight());
        content.offsetDescendantRectToMyCoords(img, r);
        float x = r.left - dp(HL_PAD), y = r.top - dp(HL_PAD);
        if (!hlShown) {
            hl.animate().cancel();
            hl.setX(x);
            hl.setY(y);
            hl.animate().alpha(1f).setDuration(150).start();
            hlShown = true;
        } else {
            hl.animate().x(x).y(y).alpha(1f).setDuration(280)
                    .setInterpolator(new OvershootInterpolator(1.3f)).start();
        }
    }

    /**
     * Moves the ambient glow under the focused tile and fades it to the tile's colour. `scrollY` is where the content will
     * be scrolled to once this focus change settles, so the glow lands under the tile on SCREEN (it is a fixed layer).
     */
    private void setGlow(int color, View img, int scrollY) {
        Rect r = new Rect(0, 0, img.getWidth(), img.getHeight());
        content.offsetDescendantRectToMyCoords(img, r);
        int viewH = Math.max(1, root != null && root.getHeight() > 0 ? root.getHeight() : sh);
        final float toX = Math.min(0.95f, r.centerX() / (float) sw);
        final float toY = Math.max(0.30f, Math.min(0.88f, (r.centerY() - scrollY) / (float) viewH));
        final float fromX = glowCx, fromY = glowCy;
        final int fromC = glowColor, toC = color | 0xFF000000;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofFloat(0f, 1f);
        glowAnim.setDuration(280);
        final ArgbEvaluator ev = new ArgbEvaluator();
        glowAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                glowCx = fromX + (toX - fromX) * f;
                glowCy = fromY + (toY - fromY) * f;
                glowColor = (Integer) ev.evaluate(f, fromC, toC);
                glow.setGradientCenter(glowCx, glowCy);
                glow.setColors(new int[]{(glowColor & 0x00FFFFFF) | 0x66000000, glowColor & 0x00FFFFFF});
            }
        });
        glowAnim.start();
    }

    private void start(Intent i) {
        // settings actions resolve to the phone-style com.android.settings; prefer the Android TV one
        if (i.getComponent() == null && i.getAction() != null && i.getAction().startsWith("android.settings.")) {
            Intent tv = new Intent(i).setPackage("com.android.tv.settings");
            if (getPackageManager().resolveActivity(tv, 0) != null) i = tv;
        }
        try {
            startActivity(i);
        } catch (Exception ignored) { }
    }
}
