package com.voicecalc.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史记录页面 —— 仿「计算器 Pro」HistoryActivity。
 * 每条：时间戳 + 右对齐算式 + 绿色结果 + 铅笔图标 + 分隔线；底部「清空 / 返回」胶囊按钮。
 * 历史通过 SharedPreferences 持久化，跨 Activity / 重启保留，最多 10 条。
 */
public class HistoryActivity extends Activity {

    static final String PREF = "calcprefs";
    static final String KEY_HISTORY = "history";
    static final String KEY_THEME = "theme";
    static final String KEY_VOICE_STYLE = "voiceStyle"; // 0=女人声音 1=男人声音
    static final String EXTRA_RESULT = "result";
    // 字段 / 记录分隔符（表达式与结果中不会出现 ::: 和 ;;;）
    private static final String FIELD = ":::";
    private static final String ROW = ";;;";
    private static final int MAX = 10;

    private SharedPreferences prefs;
    private List<String[]> all = new ArrayList<>();   // {time, expr, result}
    private LinearLayout listBox;
    private String filter = "";

    private int bg, stampColor, exprColor, resultColor, lineColor, iconColor, btnBg, btnText;

    private void applyTheme() {
        int t = prefs.getInt(KEY_THEME, -1);
        boolean dark;
        if (t >= 0) {
            dark = t == 1;
        } else {
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = night == Configuration.UI_MODE_NIGHT_YES;
        }
        if (dark) {
            bg         = Color.parseColor("#2A2A2A");
            stampColor = Color.parseColor("#8C8C8C");
            exprColor  = Color.parseColor("#F0F0F0");
            resultColor= Color.parseColor("#6DBE8C");
            lineColor  = Color.parseColor("#3A3A3A");
            iconColor  = Color.parseColor("#9A9A9A");
            btnBg      = Color.parseColor("#5FAE86");
            btnText    = Color.parseColor("#FFFFFF");
        } else {
            bg         = Color.parseColor("#EFEFEF");
            stampColor = Color.parseColor("#888888");
            exprColor  = Color.parseColor("#303030");
            resultColor= Color.parseColor("#3E9168");
            lineColor  = Color.parseColor("#D8D8D8");
            iconColor  = Color.parseColor("#777777");
            btnBg      = Color.parseColor("#4A9B72");
            btnText    = Color.parseColor("#FFFFFF");
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 前台保亮：浏览历史期间屏幕不熄灭，退出后跟随系统息屏设置
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        applyTheme();
        load();
        buildUI();
    }

    // ---------- 持久化（供 MainActivity 静态调用） ----------
    private static List<String[]> readAll(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, MODE_PRIVATE);
        String raw = p.getString(KEY_HISTORY, "");
        List<String[]> list = new ArrayList<>();
        if (raw.isEmpty()) return list;
        for (String rec : raw.split(ROW)) {
            String[] f = rec.split(FIELD);
            if (f.length >= 3) list.add(new String[]{f[0], f[1], f[2]});
        }
        return list;
    }

    private static String serialize(List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(ROW);
            String[] e = list.get(i);
            sb.append(e[0]).append(FIELD).append(e[1]).append(FIELD).append(e[2]);
        }
        return sb.toString();
    }

    /** 新增一条历史（expr=算式，result=结果），自动带时间戳、保留最近 10 条 */
    static void addEntry(Context c, String expr, String result) {
        if (result == null || result.isEmpty() || result.equals("错误")) return;
        List<String[]> list = readAll(c);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        list.add(new String[]{time, expr, result});
        while (list.size() > MAX) list.remove(0);
        c.getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString(KEY_HISTORY, serialize(list)).apply();
    }

    static void clearEntries(Context c) {
        c.getSharedPreferences(PREF, MODE_PRIVATE).edit().remove(KEY_HISTORY).apply();
    }

    private void load() {
        all = readAll(this);
    }

    // ---------- 界面 ----------
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        // 顶栏：右侧搜索图标
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(8), dp(12), dp(8));
        top.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(spacer);
        ImageView search = new ImageView(this);
        search.setImageDrawable(loadIcon("ic_search"));
        search.setPadding(dp(8), dp(8), dp(8), dp(8));
        search.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSearch(); }
        });
        top.addView(search, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(top);

        // 列表
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        refreshList();

        // 底栏：清空 / 返回
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(16), dp(10), dp(16), dp(16));
        bottom.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(74)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        bp.setMargins(dp(6), 0, dp(6), 0);
        TextView clearBtn = pillButton("清空");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        bottom.addView(clearBtn, bp);
        TextView backBtn = pillButton("返回");
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bottom.addView(backBtn, bp);
        root.addView(bottom);

        setContentView(root);
    }

    private TextView pillButton(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(btnText);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(btnBg);
        gd.setCornerRadius(dp(23));
        t.setBackgroundDrawable(gd);
        return t;
    }

    private void refreshList() {
        listBox.removeAllViews();
        int shown = 0;
        for (int i = all.size() - 1; i >= 0; i--) {   // 最新在上
            String[] e = all.get(i);
            if (!filter.isEmpty() && !e[1].contains(filter) && !e[2].contains(filter)) continue;
            listBox.addView(makeRow(e[0], e[1], e[2]));
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(filter.isEmpty() ? "暂无历史记录" : "没有匹配的记录");
            empty.setTextColor(stampColor);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(60), dp(16), dp(60));
            listBox.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private View makeRow(String time, String expr, String result) {
        final String value = result;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(10));
        row.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextColor(stampColor);
        tvTime.setTextSize(12);
        tvTime.setGravity(Gravity.START);
        row.addView(tvTime);

        TextView tvExpr = new TextView(this);
        tvExpr.setText(expr);
        tvExpr.setTextColor(exprColor);
        tvExpr.setTextSize(19);
        tvExpr.setGravity(Gravity.END);
        row.addView(tvExpr, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout resRow = new LinearLayout(this);
        resRow.setOrientation(LinearLayout.HORIZONTAL);
        resRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView tvRes = new TextView(this);
        tvRes.setText(groupDigits(result));
        tvRes.setTextColor(resultColor);
        tvRes.setTextSize(24);
        tvRes.setGravity(Gravity.END);
        resRow.addView(tvRes, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ImageView pencil = new ImageView(this);
        pencil.setImageDrawable(loadIcon("ic_edit"));
        pencil.setPadding(dp(10), dp(2), 0, dp(2));
        resRow.addView(pencil, new LinearLayout.LayoutParams(dp(34), dp(34)));
        row.addView(resRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 点击整条：把该结果回填到计算器
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent data = new Intent();
                data.putExtra(EXTRA_RESULT, value);
                setResult(RESULT_OK, data);
                finish();
            }
        });

        View line = new View(this);
        line.setBackgroundColor(lineColor);
        row.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return row;
    }

    /** 给整数部分加千分位（原版结果 2,304 样式） */
    private String groupDigits(String s) {
        if (s == null) return "";
        int dot = s.indexOf('.');
        int e = s.toLowerCase(Locale.ROOT).indexOf('e');
        int cut = s.length();
        if (dot >= 0) cut = Math.min(cut, dot);
        if (e >= 0) cut = Math.min(cut, e);
        String intPart = s.substring(0, cut);
        String tail = s.substring(cut);
        boolean neg = intPart.startsWith("-");
        if (neg) intPart = intPart.substring(1);
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            sb.append(intPart.charAt(i));
            if (++c % 3 == 0 && i > 0) sb.append(',');
        }
        return (neg ? "-" : "") + sb.reverse().toString() + tail;
    }

    private void showSearch() {
        final EditText et = new EditText(this);
        et.setHint("输入算式或结果关键字");
        et.setText(filter);
        et.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("搜索历史")
                .setView(et)
                .setPositiveButton("搜索", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        filter = et.getText().toString().trim();
                        refreshList();
                    }
                })
                .setNeutralButton("全部", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        filter = "";
                        refreshList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空历史")
                .setMessage("确定清空全部历史记录吗？此操作不可恢复。")
                .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        clearEntries(HistoryActivity.this);
                        load();
                        refreshList();
                        Toast.makeText(HistoryActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 原版图标资源（按主题着色） ----------
    private Drawable loadIcon(String resName) {
        Drawable d = getResources().getDrawable(
                getResources().getIdentifier(resName, "drawable", getPackageName()));
        d.mutate();
        d.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_ATOP);
        return d;
    }

    @Override public void onBackPressed() { finish(); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
