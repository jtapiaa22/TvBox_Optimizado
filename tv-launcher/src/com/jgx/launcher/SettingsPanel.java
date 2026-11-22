package com.jgx.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * "Personalizar launcher" como panel lateral grande (como el de Ajustes de Android TV), en vez de un dialogo chico que se cierra
 * en cada cambio. Con ◀ ▶ se cambia cada opcion y el launcher, que queda visible detras, se rearma solo en vivo (escucha las
 * preferencias mientras `open` es true). Atras cierra. Las acciones que abren otra lista (apps ocultas, exportar, importar)
 * cierran el panel y las hace el launcher (extra "panel_action").
 */
public class SettingsPanel extends Activity {
    /** El launcher solo se rearma por cambios de preferencias mientras el panel esta abierto. */
    static boolean open;

    private SharedPreferences prefs;
    private float d;
    private LinearLayout list;
    private final List<Runnable> refreshers = new ArrayList<>();

    private int dp(float v) { return Math.round(v * d); }

    /** Una fila: titulo y valor; `change(+1/-1)` la cambia; las de accion (`action` != null) responden solo a OK. */
    private abstract class Row {
        final String title;
        final String action;
        Row(String title, String action) { this.title = title; this.action = action; }
        abstract String value();
        void change(int dir) { }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        DisplayMetrics real = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(real);
        d = Math.min(Math.max(real.widthPixels, real.heightPixels) / 1280f, Math.min(real.widthPixels, real.heightPixels) / 720f);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x33000000); // el launcher se sigue viendo a la izquierda

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF121620, 0xFF0B0E14});
        panel.setBackground(bg);
        panel.setPadding(dp(36), dp(40), dp(28), dp(24));
        root.addView(panel, new FrameLayout.LayoutParams(dp(540), -1, Gravity.END));

        TextView title = text("Personalizar launcher", 30, true);
        panel.addView(title);
        TextView hint = text("◀ ▶ cambia   ·   OK abre   ·   Atrás cierra", 16, false);
        hint.setAlpha(0.6f);
        hint.setPadding(0, dp(6), 0, dp(18));
        panel.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        panel.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        for (Row r : rows()) addRow(r);
        setContentView(root);

        panel.setTranslationX(dp(540));
        panel.animate().translationX(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    @Override protected void onResume() { super.onResume(); open = true; }
    @Override protected void onPause() { super.onPause(); open = false; }

    private List<Row> rows() {
        List<Row> r = new ArrayList<>();
        r.add(choice("Orden del dock", "dock_order", Launcher.ORDER_NAMES, 0));
        r.add(choice("Tamaño de las tarjetas", "card_size", Launcher.CARD_SIZE_NAMES, 1));
        r.add(choice("Esquinas", "card_corners", Launcher.CARD_CORNER_NAMES, 1));
        r.add(choice("Zoom al enfocar", "card_zoom", Launcher.CARD_ZOOM_NAMES, 2));
        r.add(choice("Tema", "theme", Launcher.THEME_NAMES, 0));
        r.add(choice("Reloj", "clock_style", Launcher.CLOCK_STYLE_NAMES, 0));
        r.add(toggle("Respiración al seleccionar", "breath"));
        r.add(toggle("Brillo de fondo", "glow"));
        r.add(toggle("Animación al abrir apps", "open_anim"));
        r.add(background());
        r.add(choice("Oscurecer fondo", "bg_dim", Launcher.BG_DIM_NAMES, 2));
        if (!prefs.getStringSet("hidden", new HashSet<String>()).isEmpty()) r.add(action("Apps ocultas…", "hidden"));
        r.add(action("Exportar ajustes", "export"));
        r.add(action("Importar ajustes", "import"));
        return r;
    }

    private int idx(String key, int def, int n) {
        int v = prefs.getInt(key, def);
        return v < 0 || v >= n ? def : v;
    }

    private Row choice(String title, final String key, final String[] names, final int def) {
        return new Row(title, null) {
            String value() { return names[idx(key, def, names.length)]; }
            void change(int dir) {
                int n = names.length;
                prefs.edit().putInt(key, (idx(key, def, n) + dir + n) % n).apply();
            }
        };
    }

    private Row toggle(String title, final String key) {
        return new Row(title, null) {
            String value() { return prefs.getBoolean(key, true) ? "Sí" : "No"; }
            void change(int dir) { prefs.edit().putBoolean(key, !prefs.getBoolean(key, true)).apply(); }
        };
    }

    /** Fondo: "Ninguno" y los archivos de la carpeta de fondos, en ronda. */
    private Row background() {
        return new Row("Fondo", null) {
            List<String> opts() {
                List<String> o = new ArrayList<>();
                o.add("");
                for (java.io.File f : Launcher.listBackgrounds(SettingsPanel.this)) o.add(f.getName());
                return o;
            }
            String value() {
                String cur = prefs.getString("bg_file", "");
                List<String> o = opts();
                if (o.size() == 1) return "no hay archivos";
                return cur == null || cur.isEmpty() || !o.contains(cur) ? "Ninguno" : cur;
            }
            void change(int dir) {
                List<String> o = opts();
                if (o.size() == 1) return;
                int i = o.indexOf(prefs.getString("bg_file", ""));
                if (i < 0) i = 0;
                prefs.edit().putString("bg_file", o.get((i + dir + o.size()) % o.size())).apply();
            }
        };
    }

    private Row action(String title, String act) {
        return new Row(title, act) {
            String value() { return "›"; }
        };
    }

    private void addRow(final Row r) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        final GradientDrawable idle = round(0x00000000), focused = round(0x30FFFFFF);
        row.setBackground(idle);

        TextView label = text(r.title, 19, false);
        label.setSingleLine(true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView val = text("", 19, true);
        val.setGravity(Gravity.END);
        val.setSingleLine(true);
        val.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(val, new LinearLayout.LayoutParams(dp(190), -2));

        final Runnable show = new Runnable() {
            @Override public void run() {
                String v = r.value();
                val.setText(r.action != null ? v : (row.hasFocus() ? "◀  " + v + "  ▶" : v));
            }
        };
        refreshers.add(show);
        show.run();
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                row.setBackground(has ? focused : idle);
                val.setAlpha(has ? 1f : 0.75f);
                show.run();
            }
        });
        row.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent ev) {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) {
                    return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
                }
                boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
                if (r.action != null) {
                    if (ok && ev.getRepeatCount() == 0) runAction(r.action);
                    return ok;
                }
                int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT || ok ? 1 : 0;
                if (dir == 0) return false;
                if (ev.getRepeatCount() == 0 || !ok) {
                    r.change(dir);
                    for (Runnable s : refreshers) s.run(); // "Fondo" cambia lo que muestra "Oscurecer", etc.
                }
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(4);
        list.addView(row, lp);
    }

    private void runAction(String act) {
        startActivity(new Intent(this, Launcher.class).putExtra("panel_action", act));
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * d);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }
}
