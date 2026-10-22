package com.jgx.launcher;

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

/** Apple TV style launcher: black background, dock of four apps, grid below, one glass highlight. */
public class Launcher extends Activity {
    // package, label override (null = app label), fallback action when the package has no launcher entry
    private static final String[][] DOCK = {
        {"com.jgx.iptv", null, null},
        {"com.esaba.downloader", null, null},
        {"com.android.vending", null, null},
        {"com.android.tv.settings", "Ajustes", Settings.ACTION_SETTINGS},
    };
    private static final String[] HIDDEN = {"com.droidlogic.mboxlauncher", "com.jgx.launcher"};
    private static final int COLS = 4;
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
    private TextView dockName, wifiPill, settingsPill;
    private final List<View> dockCells = new ArrayList<>();
    private SharedPreferences prefs;
    private BroadcastReceiver pkgReceiver;
    private boolean recentsDirty, longHandled;
    private List<Entry> dockEntries = new ArrayList<>();
    private TextView stats;
    private final Map<View, Runnable> longActions = new HashMap<>();

    private int dp(float v) { return Math.round(v * d); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        DisplayMetrics m = getResources().getDisplayMetrics();
        d = m.density;
        sw = m.widthPixels;
        sh = m.heightPixels;
        build();

        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                // a freshly installed app goes to the top of Recientes
                if (Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())
                        && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false) && i.getData() != null) {
                    pushRecent(i.getData().getSchemeSpecificPart());
                }
                recreate();
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
    protected void onRestart() {
        super.onRestart();
        if (recentsDirty) {
            recentsDirty = false;
            recreate(); // refresh the Recientes row after using an app
        }
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
        String t = name != null ? "Wi-Fi  ·  " + name : "Wi-Fi";
        if (name != null && speed > 0) t += "  ·  " + speed + " Mbps";
        wifiPill.setText(t);
    }

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
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setSingleLine(true);
        t.setShadowLayer(dp(4), 0, dp(2), 0xAA000000); // legibility on any glow colour
        return t;
    }

    private void build() {
        List<Entry> apps = loadApps();
        Map<String, Entry> all = new HashMap<>();
        for (Entry e : apps) all.put(e.pkg, e);

        List<Entry> dock = new ArrayList<>();
        List<String> used = new ArrayList<>();
        for (String h : HIDDEN) used.add(h);
        for (String[] spec : dockSpecs()) {
            Entry e = dockEntry(spec, all);
            if (e != null) { dock.add(e); used.add(spec[0]); }
        }
        dockEntries = dock;
        Set<String> hidden = prefs.getStringSet("hidden", new HashSet<String>());
        List<Entry> grid = new ArrayList<>();
        for (Entry e : apps) if (!used.contains(e.pkg) && !hidden.contains(e.pkg)) grid.add(e);
        Collections.sort(grid, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.label.compareToIgnoreCase(b.label); }
        });
        List<Entry> recents = new ArrayList<>();
        for (String p : prefs.getString("recent", "").split(",")) {
            for (Entry e : grid) if (e.pkg.equals(p) && recents.size() < COLS) recents.add(e);
        }

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
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);

        content = new FrameLayout(this);
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));

        // ambient glow, one view behind everything
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(sw * 0.55f);
        glow.setGradientCenter(0.25f, 0.82f);
        glow.setColors(new int[]{0x00000000, 0x00000000});
        View glowView = new View(this);
        glowView.setBackground(glow);
        content.addView(glowView, new FrameLayout.LayoutParams(-1, sh));

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
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 98.8f);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(dp(5), 0, dp(3), 0xAA000000);
        head.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d 'de' MMMM");
        date.setFormat12Hour("EEEE d 'de' MMMM");
        date.setTextColor(0xF2FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24.7f);
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
        wifiPill = pill("Wi-Fi");
        settingsPill = pill("Ajustes");
        wifiPill.setId(View.generateViewId());
        settingsPill.setId(View.generateViewId());
        final TextView ramPill = pill("Liberar RAM");
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
            @Override public void onClick(View v) { start(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        });
        // long press on Ajustes: bring back every hidden app
        longActions.put(settingsPill, new Runnable() {
            @Override public void run() {
                prefs.edit().remove("hidden").apply();
                Toast.makeText(Launcher.this, "Apps ocultas restauradas", Toast.LENGTH_SHORT).show();
                recreate();
            }
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
            View c = cell(dock.get(i), true);
            c.setNextFocusUpId(wifiPill.getId());
            if (i == 0) c.setNextFocusLeftId(c.getId());
            if (i == dock.size() - 1) c.setNextFocusRightId(c.getId());
            dockRow.addView(c);
            dockCells.add(c);
        }
        ramPill.setNextFocusDownId(dockCells.isEmpty() ? View.NO_ID : dockCells.get(0).getId());
        wifiPill.setNextFocusDownId(dockCells.isEmpty() ? View.NO_ID : dockCells.get(0).getId());
        settingsPill.setNextFocusDownId(dockCells.isEmpty() ? View.NO_ID : dockCells.get(Math.min(dockCells.size() - 1, 3)).getId());

        // grid
        LinearLayout gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setPadding(dp(MARGIN + BAND_PAD), dp(30), 0, dp(120));
        col.addView(gridBox, new LinearLayout.LayoutParams(-1, -2));
        List<View> gridCells = new ArrayList<>();
        if (!recents.isEmpty()) {
            gridBox.addView(sectionTitle("Recientes"));
            addRows(gridBox, recents, null, true);
            gridBox.addView(sectionTitle("Todas las apps"));
        }
        addRows(gridBox, grid, gridCells, false);

        setContentView(scroll);

        // restore last focused tile
        final String last = prefs.getString("last", null);
        View start = dockCells.isEmpty() ? null : dockCells.get(0);
        if (last != null) {
            for (View v : dockCells) if (last.equals(v.getTag())) start = v;
            for (View v : gridCells) if (last.equals(v.getTag())) start = v;
        }
        final View first = start;
        if (first != null) content.post(new Runnable() {
            @Override public void run() { first.requestFocus(); }
        });
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 22.8f, "sans-serif");
        t.setAlpha(0.85f);
        t.setPadding(0, dp(6), 0, dp(14));
        return t;
    }

    private void addRows(LinearLayout box, List<Entry> list, List<View> out, boolean recent) {
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                box.addView(row, new LinearLayout.LayoutParams(-2, -2));
            }
            View c = cell(list.get(i), false);
            if (recent) c.setTag("r:" + list.get(i).pkg); // same app also appears in the grid: keep tags unique
            if (i % COLS == 0) c.setNextFocusLeftId(c.getId());
            if (i % COLS == COLS - 1 || i == list.size() - 1) c.setNextFocusRightId(c.getId());
            row.addView(c);
            if (out != null) out.add(c);
        }
    }

    private void pushRecent(String pkg) {
        StringBuilder sb = new StringBuilder(pkg);
        int n = 1;
        for (String p : prefs.getString("recent", "").split(",")) {
            if (p.isEmpty() || p.equals(pkg) || n >= 8) continue;
            sb.append(',').append(p);
            n++;
        }
        prefs.edit().putString("recent", sb.toString()).apply();
    }

    private void launch(Entry e) {
        pushRecent(e.pkg);
        recentsDirty = true;
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

    private void saveDock(List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) sb.append(sb.length() > 0 ? "," : "").append(p);
        prefs.edit().putString("dock", sb.toString()).apply();
        recreate();
    }

    private void addToDock(final String pkg) {
        List<String> l = dockPkgs();
        l.add(pkg);
        saveDock(l); // no limit: the dock scrolls sideways
    }

    // ---- top bar stats: Wi-Fi speed, free RAM, temperature (local reads, no network)

    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            updateStats();
            updateWifi();
            if (stats != null) stats.postDelayed(this, 5000);
        }
    };

    /** Ends cached/background processes of every other app (foreground apps are untouched). */
    private void freeRam() {
        final android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        final long before = mi.availMem;
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            if (!ai.packageName.equals(getPackageName())) am.killBackgroundProcesses(ai.packageName);
        }
        Toast.makeText(this, "Liberando RAM…", Toast.LENGTH_SHORT).show();
        stats.postDelayed(new Runnable() {
            @Override public void run() {
                android.app.ActivityManager.MemoryInfo m2 = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(m2);
                long freed = (m2.availMem - before) / 1048576;
                Toast.makeText(Launcher.this,
                        freed > 20 ? "Se liberaron " + freed + " MB" : "La RAM ya estaba optimizada",
                        Toast.LENGTH_LONG).show();
                updateStats();
            }
        }, 1500);
    }

    private void updateStats() {
        StringBuilder sb = new StringBuilder();
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append(String.format(java.util.Locale.US, "RAM %.1f / %.1f GB libre",
                    mi.availMem / 1073741824.0, mi.totalMem / 1073741824.0));
        } catch (Exception ignored) { }
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

    private void showMenu(final Entry e, final boolean dock) {
        final List<String> items = new ArrayList<>();
        final List<String> dl = dockPkgs();
        final int di = dl.indexOf(e.pkg);
        if (dock) {
            if (di > 0) items.add("Mover a la izquierda");
            if (di >= 0 && di < dl.size() - 1) items.add("Mover a la derecha");
            if (dl.size() > 1) items.add("Quitar del dock");
        } else {
            items.add("Agregar al dock");
            items.add("Ocultar");
        }
        items.add("Información de la app");
        items.add("Desinstalar");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e.label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int which) {
                        String it = items.get(which);
                        Uri uri = Uri.parse("package:" + e.pkg);
                        if (it.equals("Agregar al dock")) {
                            addToDock(e.pkg);
                        } else if (it.startsWith("Mover")) {
                            int j = it.endsWith("izquierda") ? di - 1 : di + 1;
                            Collections.swap(dl, di, j);
                            saveDock(dl);
                        } else if (it.equals("Quitar del dock")) {
                            dl.remove(di);
                            saveDock(dl);
                        } else if (it.equals("Ocultar")) {
                            Set<String> h = new HashSet<>(prefs.getStringSet("hidden", new HashSet<String>()));
                            h.add(e.pkg);
                            prefs.edit().putStringSet("hidden", h).apply();
                            recreate();
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
        t.setPadding(dp(26), dp(12), dp(26), dp(12));
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

    private View cell(final Entry e, final boolean dock) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setId(View.generateViewId());
        c.setTag(e.pkg);
        c.setFocusable(true);
        c.setClickable(true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(TILE_W), -2);
        cp.rightMargin = dp(GAP);
        if (!dock) cp.bottomMargin = dp(18);
        c.setLayoutParams(cp);

        final ImageView img = new ImageView(this);
        img.setBackground(round(e.banner ? 0xFF1C1C1E : 0xFF2A2A2E, 16));
        img.setClipToOutline(true);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16)); }
        });
        img.setImageDrawable(e.icon);
        if (e.banner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(dp(30), dp(14), dp(30), dp(14));
        }
        c.addView(img, new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H)));

        final TextView label;
        if (!dock) {
            label = text(e.label, 20.9f, "sans-serif-medium");
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setPadding(0, dp(14), 0, 0);
            label.setAlpha(0f);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            c.addView(label, new LinearLayout.LayoutParams(-1, dp(54)));
        } else {
            label = null;
        }

        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(e); }
        });
        longActions.put(c, new Runnable() {
            @Override public void run() { showMenu(e, dock); }
        });
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                float s = has ? 1.1f : 1f;
                img.animate().scaleX(s).scaleY(s).setDuration(180).start();
                if (label != null) label.animate().alpha(has ? 1f : 0f).setDuration(150).start();
                if (!has) return;
                prefs.edit().putString("last", e.pkg).apply();
                moveHighlight(img);
                if (dock) {
                    dockName.setText(e.label);
                    setGlow(e.color, img);
                    scroll.smoothScrollTo(0, 0);
                } else {
                    dockName.setText("");
                    Rect r = new Rect(0, 0, c.getWidth(), c.getHeight());
                    content.offsetDescendantRectToMyCoords(c, r);
                    scroll.smoothScrollTo(0, Math.max(0, r.top - dp(170)));
                }
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

    private void setGlow(int color, View img) {
        Rect r = new Rect(0, 0, img.getWidth(), img.getHeight());
        content.offsetDescendantRectToMyCoords(img, r);
        glow.setGradientCenter(Math.min(0.95f, (r.centerX()) / (float) sw), 0.84f);
        glow.setColors(new int[]{(color & 0x00FFFFFF) | 0x66000000, (color & 0x00FFFFFF) | 0x00000000});
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
