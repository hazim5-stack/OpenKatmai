package org.openkatmai.toolbox;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

/**
 * UI v3 — 苹果风 + 彩色主题 + 黑白模式切换
 *
 * 主题：DARK_COLORFUL（深色炫彩，默认） / DARK_MONO（深色黑白） /
 *      LIGHT_COLORFUL（浅色炫彩） / LIGHT_MONO（浅色黑白）
 */
public class UI {

    public enum Theme { DARK_COLORFUL, DARK_MONO, LIGHT_COLORFUL, LIGHT_MONO }

    private static Theme theme = Theme.DARK_COLORFUL;
    private static float dens = 1;

    // 配色（由 theme 动态计算）
    public static int BG, CARD, CARD_HI, SEP, TEXT, TEXT_DIM, ACCENT, ACCENT2, OK, WARN, DANGER;

    public static void init(Context c) {
        dens = c.getResources().getDisplayMetrics().density;
        applyTheme(theme);
    }

    public static Theme getTheme() { return theme; }

    public static void setTheme(Theme t) {
        theme = t;
        applyTheme(t);
    }

    public static void toggleMono() {
        switch (theme) {
            case DARK_COLORFUL: setTheme(Theme.DARK_MONO); break;
            case DARK_MONO:     setTheme(Theme.DARK_COLORFUL); break;
            case LIGHT_COLORFUL:setTheme(Theme.LIGHT_MONO); break;
            case LIGHT_MONO:    setTheme(Theme.LIGHT_COLORFUL); break;
        }
    }

    public static void toggleLight() {
        switch (theme) {
            case DARK_COLORFUL: setTheme(Theme.LIGHT_COLORFUL); break;
            case LIGHT_COLORFUL:setTheme(Theme.DARK_COLORFUL); break;
            case DARK_MONO:     setTheme(Theme.LIGHT_MONO); break;
            case LIGHT_MONO:    setTheme(Theme.DARK_MONO); break;
        }
    }

    private static void applyTheme(Theme t) {
        theme = t;
        switch (t) {
            case DARK_COLORFUL:
                BG = 0xFF0B0F1A;
                CARD = 0xFF16213A;
                CARD_HI = 0xFF1E2A47;
                SEP = 0xFF2A3A5C;
                TEXT = 0xFFFFFFFF;
                TEXT_DIM = 0xFF9AA7C7;
                ACCENT = 0xFF3D8BFF;      // 亮蓝
                ACCENT2 = 0xFFA855F7;     // 紫
                OK = 0xFF22D3A5;          // 青绿
                WARN = 0xFFFFB020;        // 橙
                DANGER = 0xFFFF4D6D;      // 玫红
                break;
            case DARK_MONO:
                BG = 0xFF000000;
                CARD = 0xFF1A1A1A;
                CARD_HI = 0xFF262626;
                SEP = 0xFF3A3A3A;
                TEXT = 0xFFFFFFFF;
                TEXT_DIM = 0xFF8E8E93;
                ACCENT = 0xFFAEAEB2;
                ACCENT2 = 0xFF8E8E93;
                OK = 0xFFC7C7CC;
                WARN = 0xFFD1D1D6;
                DANGER = 0xFFFF6961;
                break;
            case LIGHT_COLORFUL:
                BG = 0xFFF2F5FB;
                CARD = 0xFFFFFFFF;
                CARD_HI = 0xFFE8EDF7;
                SEP = 0xFFD5DDEC;
                TEXT = 0xFF0B0F1A;
                TEXT_DIM = 0xFF6B7896;
                ACCENT = 0xFF2563EB;
                ACCENT2 = 0xFF9333EA;
                OK = 0xFF0D9488;
                WARN = 0xFFD97706;
                DANGER = 0xFFDC2626;
                break;
            case LIGHT_MONO:
                BG = 0xFFFFFFFF;
                CARD = 0xFFF7F7F7;
                CARD_HI = 0xFFEDEDED;
                SEP = 0xFFD9D9D9;
                TEXT = 0xFF111111;
                TEXT_DIM = 0xFF6E6E73;
                ACCENT = 0xFF48484A;
                ACCENT2 = 0xFF6E6E73;
                OK = 0xFF48484A;
                WARN = 0xFF8E8E93;
                DANGER = 0xFFB3261E;
                break;
        }
    }

    public static int dp(float v) { return (int) (v * dens + 0.5f); }

    public static GradientDrawable rounded(int fill, int radius, int strokeW, int strokeC) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, strokeC);
        return g;
    }

    /** 渐变卡片（炫彩模式下用渐变，黑白模式用纯色） */
    public static GradientDrawable cardBg(Context c, boolean highlight) {
        GradientDrawable g = new GradientDrawable();
        if (theme == Theme.DARK_COLORFUL || theme == Theme.LIGHT_COLORFUL) {
            g.setColors(new int[]{highlight ? CARD_HI : CARD, BG});
            g.setOrientation(GradientDrawable.Orientation.TL_BR);
        } else {
            g.setColor(highlight ? CARD_HI : CARD);
        }
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), SEP);
        return g;
    }

    public static LinearLayout page(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(10), dp(14), dp(20));
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(cardBg(c, false));
        l.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(9);
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView sectionTitle(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(ACCENT);
        t.setPadding(0, 0, 0, dp(5));
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(TEXT);
        t.setPadding(0, 0, 0, dp(3));
        return t;
    }

    public static TextView text(Context c, String s, int color, float sizeSp) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    public static TextView dim(Context c, String s) { return text(c, s, TEXT_DIM, 12); }

    public static LinearLayout kvRow(Context c, String k, String v) {
        return kvRow(c, k, v, TEXT_DIM);
    }

    /** kv 行，值可指定颜色（用于彩色状态） */
    public static LinearLayout kvRow(Context c, String k, String v, int valueColor) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView kt = new TextView(c);
        kt.setText(k);
        kt.setTextSize(13);
        kt.setTextColor(theme == Theme.DARK_MONO || theme == Theme.LIGHT_MONO ? TEXT : TEXT_DIM);
        kt.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView vt = new TextView(c);
        vt.setText(v);
        vt.setTextSize(13);
        vt.setTextColor(valueColor);
        vt.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(kt);
        r.addView(vt);
        return r;
    }

    public static TextView button(Context c, String s, int color) {
        TextView b = new TextView(c);
        b.setText(s);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(color, dp(11), 0, 0));
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    public static TextView button2(Context c, String s) {
        TextView b = button(c, s, CARD_HI);
        b.setTextColor(theme == Theme.LIGHT_MONO || theme == Theme.LIGHT_COLORFUL ? TEXT : TEXT);
        return b;
    }

    public static LinearLayout buttonRow(Context c, TextView... btns) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < btns.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(7);
            btns[i].setLayoutParams(lp);
            r.addView(btns[i]);
        }
        return r;
    }

    public static Switch sw(Context c, boolean checked) {
        Switch s = new Switch(c);
        s.setChecked(checked);
        return s;
    }

    /**
     * 分段控件 —— v3 修复：带点击回调。
     * onSel 回传选中索引。
     */
    public static LinearLayout segmented(Context c, String[] items, int sel,
                                         final SegListener onSel) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(rounded(CARD_HI, dp(9), 0, 0));
        box.setPadding(dp(2), dp(2), dp(2), dp(2));
        final TextView[] tabs = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView tv = new TextView(c);
            tv.setText(items[i]);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(3), dp(9), dp(3), dp(9));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            styleTab(tv, i == sel);
            tv.setOnClickListener(v -> {
                for (int j = 0; j < tabs.length; j++) styleTab(tabs[j], j == idx);
                if (onSel != null) onSel.onSelect(idx, items[idx]);
            });
            tabs[i] = tv;
            box.addView(tv);
        }
        return box;
    }

    private static void styleTab(TextView tv, boolean sel) {
        if (sel) {
            tv.setTextColor(0xFFFFFFFF);
            tv.setBackground(rounded(ACCENT, dp(7), 0, 0));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            tv.setTextColor(TEXT_DIM);
            tv.setBackgroundColor(Color.TRANSPARENT);
            tv.setTypeface(Typeface.DEFAULT);
        }
    }

    public interface SegListener {
        void onSelect(int index, String label);
    }

    public static LinearLayout sliderRow(Context c, String label, int max, int progress,
                                         SeekBar.OnSeekBarChangeListener l) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(5), 0, dp(3));
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView lt = new TextView(c);
        lt.setText(label);
        lt.setTextSize(13);
        lt.setTextColor(TEXT);
        TextView vt = new TextView(c);
        vt.setTextSize(13);
        vt.setTextColor(ACCENT);
        vt.setTypeface(Typeface.DEFAULT_BOLD);
        vt.setGravity(Gravity.END);
        vt.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(lt);
        top.addView(vt);
        SeekBar sb = new SeekBar(c);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                if (u) {
                    vt.setText(String.valueOf(v));
                    if (l != null) l.onProgressChanged(s, v, u);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {
                if (l != null) l.onStopTrackingTouch(s);
            }
        });
        vt.setText(String.valueOf(progress));
        col.addView(top);
        col.addView(sb);
        return col;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(SEP);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    public static void space(Context c, LinearLayout col, float dpv) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpv)));
        col.addView(s);
    }

    public static ScrollView scroll(Context c, View content) {
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.addView(content);
        return sv;
    }

    public static void fadeIn(View v, long delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(8));
        v.animate().alpha(1f).translationY(0).setDuration(200)
                .setStartDelay(delayMs)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    /** 状态色：根据百分比给颜色 */
    public static int levelColor(int pct) {
        if (pct >= 85) return DANGER;
        if (pct >= 65) return WARN;
        return OK;
    }

    /** 温度色 */
    public static int tempColor(long t) {
        if (t >= 70) return DANGER;
        if (t >= 55) return WARN;
        return OK;
    }
}
