package com.voicecalc.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaPlayer;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 语音计算器 —— 界面仿「计算器 Pro」深色主题。
 * 图标、55 段真人语音取自原版素材；无广告、无网络权限、完全离线。
 */
public class MainActivity extends Activity {

    // ===== 双主题配色（跟随系统深浅模式）=====
    private int bg, panelBg, keyBg, keyBgPress, funcBg, line;
    private int numText, funcText, opText, cText, opBg, delText;
    private int equalBg, equalPress, equalText, cursorColor, iconColor;
    private boolean isDark = false;   // 当前生效的主题（深色=true），供图标选择用

    // 主题模式：-1=跟随系统（默认），0=强制浅色，1=强制深色。太阳/月亮图标点击循环切换。
    private int themeOverride = -1;

    // 根据系统 uiMode / 手动覆盖 选择浅色/深色主题
    private void applyTheme() {
        boolean dark;
        if (themeOverride >= 0) {
            dark = themeOverride == 1;
        } else {
            int night = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            dark = night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        isDark = dark;
        if (dark) {
            bg         = Color.parseColor("#191919");
            panelBg    = Color.parseColor("#141414");
            keyBg      = Color.parseColor("#262626");
            keyBgPress = Color.parseColor("#3A3A3A");
            funcBg     = Color.parseColor("#202020");
            line       = Color.parseColor("#333333");
            numText    = Color.parseColor("#F2F2F2");
            funcText   = Color.parseColor("#C0C0C0");
            opBg       = Color.parseColor("#202020");
            opText     = Color.parseColor("#5FAE86");
            cText      = Color.parseColor("#E8734A");
            delText    = Color.parseColor("#5FAE86");
            equalBg    = Color.parseColor("#4A9B72");
            equalPress = Color.parseColor("#3D7E5D");
            equalText  = Color.parseColor("#FFFFFF");
            cursorColor= Color.parseColor("#5FAE86");
            iconColor  = Color.parseColor("#9A9A9A");
        } else {
            bg         = Color.parseColor("#ECECEC");
            panelBg    = Color.parseColor("#ECECEC");
            keyBg      = Color.parseColor("#FFFFFF");
            keyBgPress = Color.parseColor("#E0E0E0");
            funcBg     = Color.parseColor("#FFFFFF");
            line       = Color.parseColor("#D0D0D0");
            numText    = Color.parseColor("#454545");
            funcText   = Color.parseColor("#959595");
            opBg       = Color.parseColor("#4A9B72");
            opText     = Color.parseColor("#FFFFFF");
            cText      = Color.parseColor("#959595");
            delText    = Color.parseColor("#6A6A6A");
            equalBg    = Color.parseColor("#4A9B72");
            equalPress = Color.parseColor("#276443");
            equalText  = Color.parseColor("#FFFFFF");
            cursorColor= Color.parseColor("#4A9B72");
            iconColor  = Color.parseColor("#959595");
        }
    }

    private TextView display;      // 当前值（大字）
    private TextView expression;   // 算式（小字）
    private View cursor;
    private LinearLayout keyContainer;
    private ImageView soundIcon;   // 语音开关图标（开=喇叭 / 关=静音喇叭）

    private boolean scienceMode = false;   // 普通 / 科学 模式
    private boolean degreeMode = true;     // true=DEG 角度, false=RAD 弧度
    private boolean secondFunc = false;    // 2nd 反函数

    private final StringBuilder current = new StringBuilder("0");
    private final StringBuilder expr = new StringBuilder();
    private String pendingOp = null;
    private double leftOperand = 0;
    private boolean startNewInput = true;
    private boolean lastWasOperator = false;

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    // ===== 语音播放（预 prepare 全部片段 + setNextMediaPlayer 无缝串播，间隙极小）=====
    private MediaPlayer curPlayer;
    private final List<MediaPlayer> voicePlayers = new ArrayList<>(); // 本次播放链，统一释放
    private int voiceGen = 0;
    // 语音音量档位：0=静音 1=低 2=中 3=高，点音量图标循环切换
    private int voiceLevel = 3;
    private static final float[] VOLUME_STEPS = {0f, 0.3f, 0.6f, 1f};
    private static final String[] VOLUME_LABELS = {"静音", "低", "中", "高"};
    private static final String KEY_VOICE_LEVEL = "voiceLevel";
    // 音色：0=女人声音（资源原名）1=男人声音（资源 old_ 前缀）
    private int voiceStyle = 0;
    private ImageView voiceStyleIcon;
    private String voiceRes(String n) { return voiceStyle == 1 ? "old_" + n : n; }

    private final Handler cursorHandler = new Handler();
    private final Runnable cursorBlink = new Runnable() {
        @Override public void run() {
            if (cursor != null) cursor.setVisibility(cursor.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            cursorHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 前台保亮：计算器打开期间屏幕不熄灭，退出后完全跟随系统息屏设置
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 恢复用户锁定的主题偏好（-1=跟随系统）与语音音量档位
        themeOverride = getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE)
                .getInt(HistoryActivity.KEY_THEME, -1);
        voiceLevel = getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE)
                .getInt(KEY_VOICE_LEVEL, 3);
        voiceStyle = getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE)
                .getInt(HistoryActivity.KEY_VOICE_STYLE, 0);
        applyTheme();
        buildUI();
        cursorHandler.post(cursorBlink);
        speakNumber(current.toString()); // 开机播报当前值「零」，替代原版"三角函数"提示音
    }

    // 系统主题 / 屏幕方向变化时重建界面（锁定主题时 applyTheme 仍用 themeOverride，互不影响）
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildUI();
    }

    private void rebuildUI() {
        applyTheme();
        cursorHandler.removeCallbacks(cursorBlink);
        buildUI();
        cursorHandler.post(cursorBlink);
    }

    // 太阳图标：跟随系统 -> 浅色 -> 深色 -> 跟随系统，循环切换
    private void cycleTheme() {
        if (themeOverride == -1) {
            // 当前跟随系统，切到与当前相反的固定主题
            int night = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            themeOverride = (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) ? 0 : 1;
        } else if (themeOverride == 0) {
            themeOverride = 1;
        } else {
            themeOverride = -1;
        }
        // 保存主题偏好，供历史记录页读取保持一致
        getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE).edit()
                .putInt(HistoryActivity.KEY_THEME, themeOverride).apply();
        rebuildUI();
        String label = themeOverride == -1 ? "跟随系统" : (themeOverride == 0 ? "浅色主题" : "深色主题");
        Toast.makeText(this, "主题：" + label, Toast.LENGTH_SHORT).show();
    }

    // ---------- 语音 ----------
    private void stopVoice() {
        voiceGen++;
        for (MediaPlayer mp : voicePlayers) {
            try { mp.setOnCompletionListener(null); mp.setOnErrorListener(null); mp.stop(); mp.release(); } catch (Exception ignored) {}
        }
        voicePlayers.clear();
        curPlayer = null;
    }

    // 预先 prepare 全部片段，用 setNextMediaPlayer 串成 gapless 链：
    // 当前段播完由底层音频引擎无缝切到下一段，无 onCompletion 回调延迟，段间无停顿、无 click。
    // 原速播放（不设 PlaybackParams），避免变速 time-stretch 造成的爆音，听感与原版 Pro 一致。
    private void playNames(List<String> names) {
        stopVoice();
        if (voiceLevel == 0) return;   // 静音档直接不播
        final float vol = VOLUME_STEPS[voiceLevel];
        final int gen = voiceGen;

        List<MediaPlayer> chain = new ArrayList<>();
        for (String n : names) {
            int id = getResources().getIdentifier(voiceRes(n), "raw", getPackageName());
            if (id == 0) continue;
            try {
                MediaPlayer mp = new MediaPlayer();
                AssetFileDescriptor afd = getResources().openRawResourceFd(id);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mp.prepare();
                mp.setVolume(vol, vol);   // 应用当前音量档位
                chain.add(mp);
            } catch (Exception ignored) {}
        }
        if (chain.isEmpty()) return;
        voicePlayers.addAll(chain);

        for (int i = 0; i < chain.size(); i++) {
            final MediaPlayer cur = chain.get(i);
            final MediaPlayer next = (i < chain.size() - 1) ? chain.get(i + 1) : null;
            // 尝试无缝链接；采样率不一致等原因失败时回退为 onCompletion 手动切换
            final boolean[] gapless = {false};
            if (next != null) {
                try { cur.setNextMediaPlayer(next); gapless[0] = true; } catch (Exception ignored) {}
            }
            final boolean linked = gapless[0];
            cur.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) {
                    if (gen != voiceGen) return;
                    // gapless 成功时中间段的 onCompletion 不会触发，仅回退路径需要手动续播
                    if (next != null && !linked) {
                        try { next.start(); } catch (Exception ignored) {}
                    } else if (next == null) {
                        if (curPlayer == m) curPlayer = null;
                    }
                }
            });
        }
        curPlayer = chain.get(0);
        try { curPlayer.start(); } catch (Exception e) { stopVoice(); }
    }

    private void speakKey(String name) {
        List<String> l = new ArrayList<>();
        l.add(name);
        playNames(l);
    }

    private void speakNumber(String s) {
        playNames(numberToVoice(s));
    }

    // 提示音 + 数字合并为一次播放队列，避免相互掐断
    private void speakKeyThenNumber(String keyName, String numStr) {
        List<String> l = new ArrayList<>();
        if (!TextUtils.isEmpty(keyName)) l.add(keyName);
        l.addAll(numberToVoice(numStr));
        playNames(l);
    }

    private List<String> numberToVoice(String s) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(s)) return out;
        // 非数字串（如"错误"）无法朗读，跳过数字部分，只保留按键提示音
        if (!s.matches("-?[0-9.]+")) return out;
        if (s.startsWith("-")) { out.add("fuhao"); s = s.substring(1); }
        int dot = s.indexOf('.');
        String intPart = dot >= 0 ? s.substring(0, dot) : s;
        String decPart = dot >= 0 ? s.substring(dot + 1) : null;
        addChineseInt(out, intPart);
        if (decPart != null && decPart.length() > 0) {
            out.add("point");
            for (int i = 0; i < decPart.length(); i++) out.add(digitName(decPart.charAt(i)));
        }
        return out;
    }

    // 整数部分按中文习惯每4位一节、从高位到低位读：万/亿/兆，节内按个十百千，正确处理零。
    private void addChineseInt(List<String> out, String num) {
        int p = 0;
        while (num.length() > 1 && num.charAt(p) == '0') p++;
        num = num.substring(p);
        if (num.equals("0")) { out.add("zero"); return; }

        // 从高位到低位切成4位一节（最高节可能不足4位）
        List<String> groups = new ArrayList<>();
        for (int end = num.length(); end > 0; end = Math.max(0, end - 4)) {
            groups.add(num.substring(Math.max(0, end - 4), end));
        }
        // 反转为 高位在前
        Collections.reverse(groups);

        String[] bigUnit = {"", "tenthousand", "hundredmillion", "tenthousand+hundredmillion"};
        boolean lastWritten = false;   // 是否已经读过某个非零节
        boolean needZero = false;      // 节间是否需要补一个"零"
        for (int gi = 0; gi < groups.size(); gi++) {
            String g = groups.get(gi);
            int bigIdx = groups.size() - 1 - gi;   // 该节对应的 BIG 单位下标
            int z = 0;
            while (z < g.length() && g.charAt(z) == '0') z++;
            String stripped = g.substring(z);
            if (stripped.isEmpty()) {
                if (lastWritten) needZero = true;   // 连续全零节只记一次
                continue;
            }
            // 非最高节不足4位（前面有前导零）且已读过非零节 → 节间补零
            if (gi > 0 && stripped.length() < 4 && lastWritten) needZero = true;
            if (needZero) { out.add("zero"); needZero = false; }
            addGroup(out, stripped, gi == 0);
            if (bigIdx > 0) {
                String[] units = bigUnit[bigIdx].split("\\+");
                for (String u : units) out.add(u);   // "兆"用 万+亿 组合读出
            }
            lastWritten = true;
        }
    }

    private void addGroup(List<String> out, String g, boolean isTop) {
        int n = g.length();
        String[] unit = {"", "ten", "hundred", "thousand"};
        boolean needZero = false;
        for (int i = 0; i < n; i++) {
            int digit = g.charAt(i) - '0';
            int remain = n - 1 - i;
            if (digit == 0) {
                needZero = true;
            } else {
                if (needZero) { out.add("zero"); needZero = false; }
                if (digit == 1 && remain == 1 && n == 2 && i == 0 && isTop) {
                    out.add("ten");   // 仅最高节 10-19 省略"一"
                } else {
                    out.add(digitName((char) ('0' + digit)));
                    if (remain > 0) out.add(unit[remain]);
                }
            }
        }
    }

    private String digitName(char c) {
        switch (c) {
            case '0': return "zero";  case '1': return "one";
            case '2': return "two";   case '3': return "three";
            case '4': return "four";  case '5': return "five";
            case '6': return "six";   case '7': return "seven";
            case '8': return "eight"; case '9': return "nine";
        }
        return "zero";
    }

    private String opVoice(String op) {
        if (op.equals("+")) return "plus";
        if (op.equals("−") || op.equals("-")) return "minus";
        if (op.equals("×")) return "multiply";
        if (op.equals("÷")) return "divide";
        if (op.equals("^")) return "pow";
        return op;
    }

    // ---------- 界面 ----------
    private void buildUI() {
        boolean land = isLandscape();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        buildToolbar(root);

        if (land) {
            // 横屏：左右布局 —— 左侧显示区（42%），右侧键盘（58%），比例协调
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout panel = createDisplayPanel();
            body.addView(panel, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 0.42f));

            keyContainer = new LinearLayout(this);
            keyContainer.setOrientation(LinearLayout.VERTICAL);
            keyContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 0.58f));
            body.addView(keyContainer);
            buildKeyboard();

            root.addView(body);
        } else {
            // 竖屏：上下布局 —— 显示区在上，键盘在下
            LinearLayout panel = createDisplayPanel();
            root.addView(panel, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.1f));

            keyContainer = new LinearLayout(this);
            keyContainer.setOrientation(LinearLayout.VERTICAL);
            keyContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            root.addView(keyContainer);
            buildKeyboard();
        }

        setContentView(root);
        // 初始构建后立即应用自适应字号，避免首帧显示字号偏小
        fitDisplayText();
    }

    private void buildToolbar(LinearLayout root) {
        boolean land = isLandscape();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(bg);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(land ? 6 : 10), dp(12), dp(land ? 2 : 4));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(land ? 46 : 60)));

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);

        // 图标全部取自原版「计算器 Pro」资源，按主题着色
        ImageView settings = toolIcon("ic_gear");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        bar.addView(settings, ip);

        // 主题图标：深色显示月亮，浅色显示太阳
        ImageView theme = toolIcon(isDark ? "ic_moon" : "ic_sun");
        theme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleTheme(); }
        });
        bar.addView(theme, ip);

        // 音量图标：静音档显示静音喇叭，其余档位显示喇叭；点击循环 静音→低→中→高
        soundIcon = toolIcon(voiceLevel == 0 ? "ic_mute" : "ic_sound");
        soundIcon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                voiceLevel = (voiceLevel + 1) % VOLUME_STEPS.length;
                getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE).edit()
                        .putInt(KEY_VOICE_LEVEL, voiceLevel).apply();
                soundIcon.setImageDrawable(loadIcon(voiceLevel == 0 ? "ic_mute" : "ic_sound"));
                if (voiceLevel == 0) stopVoice();
                else speakNumber(current.toString());   // 以当前音量播报一遍作反馈
                Toast.makeText(MainActivity.this,
                        "语音音量：" + VOLUME_LABELS[voiceLevel], Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(soundIcon, ip);

        // 音色切换图标：女人声音 / 男人声音，点击切换并即时播报反馈
        voiceStyleIcon = toolIcon("ic_voice");
        voiceStyleIcon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                voiceStyle = voiceStyle == 0 ? 1 : 0;
                getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE).edit()
                        .putInt(HistoryActivity.KEY_VOICE_STYLE, voiceStyle).apply();
                Toast.makeText(MainActivity.this,
                        "音色：" + (voiceStyle == 0 ? "女人声音" : "男人声音"),
                        Toast.LENGTH_SHORT).show();
                if (voiceLevel > 0) speakNumber(current.toString());
            }
        });
        bar.addView(voiceStyleIcon, ip);

        ImageView hist = toolIcon("ic_clock");
        hist.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHistory(); }
        });
        bar.addView(hist, ip);

        ImageView copy = toolIcon("ic_copy");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("result", current.toString()));
                Toast.makeText(MainActivity.this, "已复制结果", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(copy, ip);

        // 单位转换入口（长度/面积/体积/质量/数据/速度/时间/温度）
        ImageView convert = toolIcon("ic_edit");
        convert.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ConvertActivity.class));
            }
        });
        bar.addView(convert, ip);

        ImageView modeSwitch = toolIcon("ic_switch");
        modeSwitch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                scienceMode = !scienceMode;
                buildKeyboard();
                Toast.makeText(MainActivity.this, scienceMode ? "科学模式" : "标准模式", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(modeSwitch, ip);

        root.addView(bar);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    // 加载原版图标并按当前主题着色。
    // 源图标为 nodpi 资源，各文件字形实际像素差异极大（齿轮 71px、太阳仅 34px），
    // 直接绘制会大小不一。此处裁掉透明边距后统一缩放到固定目标尺寸。
    private Drawable loadIcon(String resName) {
        int id = getResources().getIdentifier(resName, "drawable", getPackageName());
        android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeResource(getResources(), id);
        android.graphics.Bitmap glyph = trimTransparent(src);
        int target = dp(18);
        float ratio = Math.min((float) target / glyph.getWidth(), (float) target / glyph.getHeight());
        int nw = Math.max(1, Math.round(glyph.getWidth() * ratio));
        int nh = Math.max(1, Math.round(glyph.getHeight() * ratio));
        android.graphics.Bitmap out = android.graphics.Bitmap.createScaledBitmap(glyph, nw, nh, true);
        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), out);
        // 美化图标自带设计色（深灰蓝），不再单色覆盖：
        // 浅色主题用原色；深色主题下按比例提亮，保留色相同时保证深底可辨
        float brighten = isDark ? 2.3f : 1f;
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setScale(brighten, brighten, brighten, 1f);
        bd.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        return bd;
    }

    // 裁掉位图四周的透明边距，只保留实际字形
    private android.graphics.Bitmap trimTransparent(android.graphics.Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((pixels[y * w + x] >>> 24) > 16) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0 || (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1)) return src;
        return android.graphics.Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private ImageView toolIcon(String resName) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(loadIcon(resName));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        // 字形已统一为 18dp，内边距只留呼吸空间
        iv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return iv;
    }

    // 构建显示面板（算式小字 + 当前值大字 + 光标），布局参数由调用方按方向设置
    private LinearLayout createDisplayPanel() {
        boolean land = isLandscape();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        // 横屏左右布局下显示区垂直撑满，上下留白加大；竖屏保持原有比例
        panel.setPadding(dp(16), dp(land ? 24 : 36), dp(20), dp(land ? 24 : 20));

        expression = new TextView(this);
        expression.setText("");
        expression.setTextColor(funcText);
        expression.setTextSize(land ? 16 : 20);
        expression.setGravity(Gravity.END | Gravity.BOTTOM);
        expression.setSingleLine(true);
        panel.addView(expression, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout valRow = new LinearLayout(this);
        valRow.setOrientation(LinearLayout.HORIZONTAL);
        valRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        valRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.4f));

        display = new TextView(this);
        display.setText("0");
        display.setTextColor(numText);
        display.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        display.setGravity(Gravity.END);
        display.setSingleLine(true);
        valRow.addView(display, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        cursor = new View(this);
        cursor.setBackgroundColor(cursorColor);
        cursor.setLayoutParams(new LinearLayout.LayoutParams(dp(2), dp(land ? 34 : 40)));
        valRow.addView(cursor);
        panel.addView(valRow);
        return panel;
    }

    // 主数字自适应字号：横屏左右布局下显示区高度充裕，基础字号更大；
    // 位数过长时按比例缩小，避免超出左侧显示区宽度
    private void fitDisplayText() {
        if (display == null) return;
        boolean land = isLandscape();
        float base = land ? 42f : 52f;
        int len = current.length();
        float size = base;
        if (len > 10) size = Math.max(base * 10f / len, 14f);
        display.setTextSize(size);
    }

    private void buildKeyboard() {
        keyContainer.removeAllViews();
        String[][] rows;
        if (scienceMode) {
            rows = new String[][] {
                    {"2nd", "deg", "sin", "cos", "tan"},
                    {"^",   "lg",  "ln",  "(",   ")"},
                    {"√",   "C",   "⌫",   "%",   "÷"},
                    {"n!",  "7",   "8",   "9",   "×"},
                    {"1/X", "4",   "5",   "6",   "−"},
                    {"π",   "1",   "2",   "3",   "+"},
                    {"e",   "00",  "0",   ".",   "="}
            };
        } else {
            rows = new String[][] {
                    {"C",  "⌫", "%", "÷"},
                    {"7",  "8", "9", "×"},
                    {"4",  "5", "6", "−"},
                    {"1",  "2", "3", "+"},
                    {"00", "0", ".", "="}
            };
        }
        for (String[] row : rows) {
            LinearLayout rl = new LinearLayout(this);
            rl.setOrientation(LinearLayout.HORIZONTAL);
            rl.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            for (String k : row) rl.addView(makeKey(k), rowParam());
            keyContainer.addView(rl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
    }

    private LinearLayout.LayoutParams rowParam() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        p.setMargins(1, 1, 1, 1);
        return p;
    }

    private Button makeKey(final String k) {
        Button b = new Button(this);
        b.setText(k);
        // 横屏左右布局下行高充裕，键文字与竖屏接近；位数长的键稍小
        boolean land = isLandscape();
        b.setTextSize(k.length() > 2 ? (land ? 14 : 18) : (land ? 20 : 26));
        b.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        b.setPadding(0, 0, 0, 0);
        b.setAllCaps(false);

        if (k.equals("=")) {
            b.setBackgroundDrawable(pressDrawable(equalBg, equalPress));
            b.setTextColor(equalText);
        } else if (k.equals("÷") || k.equals("×") || k.equals("−") || k.equals("+") || k.equals("^")) {
            b.setBackgroundDrawable(pressDrawable(opBg, keyBgPress));
            b.setTextColor(opText);
        } else if (k.equals("C")) {
            b.setBackgroundDrawable(pressDrawable(funcBg, keyBgPress));
            b.setTextColor(cText);
        } else if (k.equals("⌫")) {
            b.setBackgroundDrawable(pressDrawable(funcBg, keyBgPress));
            b.setTextColor(delText);
        } else if (isDigitKey(k)) {
            b.setBackgroundDrawable(pressDrawable(keyBg, keyBgPress));
            b.setTextColor(numText);
        } else {
            b.setBackgroundDrawable(pressDrawable(funcBg, keyBgPress));
            b.setTextColor(funcText);
        }
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onKey(k); }
        });
        return b;
    }

    private boolean isDigitKey(String k) {
        return k.equals("0") || k.equals("00") || k.equals("1") || k.equals("2") || k.equals("3")
                || k.equals("4") || k.equals("5") || k.equals("6") || k.equals("7")
                || k.equals("8") || k.equals("9") || k.equals(".");
    }

    private StateListDrawable pressDrawable(int normal, int pressed) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(pressed));
        d.addState(new int[]{}, new ColorDrawable(normal));
        return d;
    }

    // ---------- 计算逻辑 ----------
    private void onKey(String k) {
        if (isDigitKey(k)) {
            if (k.equals(".")) inputDot();
            else if (k.equals("00")) inputDoubleZero();
            else inputDigit(k);
        } else if (k.equals("+") || k.equals("−") || k.equals("×") || k.equals("÷") || k.equals("^")) {
            inputOperator(k);
        } else if (k.equals("=")) {
            calculate();
        } else if (k.equals("C")) {
            clearAll();
        } else if (k.equals("⌫")) {
            backspace();
        } else if (k.equals("%")) {
            percent();
        } else if (k.equals("2nd")) {
            secondFunc = !secondFunc;
            speakKey("arcfunction");
            Toast.makeText(this, secondFunc ? "反函数 开" : "反函数 关", Toast.LENGTH_SHORT).show();
        } else if (k.equals("deg")) {
            degreeMode = !degreeMode;
            speakKey(degreeMode ? "deg" : "rad");
            Toast.makeText(this, degreeMode ? "角度 DEG" : "弧度 RAD", Toast.LENGTH_SHORT).show();
        } else if (k.equals("sin") || k.equals("cos") || k.equals("tan") || k.equals("ln")
                || k.equals("lg") || k.equals("√") || k.equals("n!") || k.equals("1/X")
                || k.equals("π") || k.equals("e") || k.equals("(") || k.equals(")")) {
            scientific(k);
        }
    }

    private void inputDigit(String d) {
        if (startNewInput || current.toString().equals("0")) {
            current.setLength(0);
            current.append(d);
            startNewInput = false;
        } else {
            if (current.length() < 15) current.append(d);
        }
        lastWasOperator = false;
        refreshDisplay();
        speakNumber(d);
    }

    private void inputDoubleZero() {
        if (startNewInput || current.toString().equals("0")) {
            current.setLength(0);
            current.append("0");
            startNewInput = false;
        } else if (current.length() < 14) {
            current.append("00");
        }
        lastWasOperator = false;
        refreshDisplay();
        speakKey("zero");
    }

    private void inputDot() {
        if (startNewInput) {
            current.setLength(0);
            current.append("0.");
            startNewInput = false;
        } else if (!current.toString().contains(".")) {
            current.append(".");
        }
        lastWasOperator = false;
        refreshDisplay();
        speakKey("point");
    }

    private void inputOperator(String op) {
        if (lastWasOperator && pendingOp != null) {
            pendingOp = op;
            expr.delete(expr.length() - symbolOf(pendingOp).length(), expr.length());
            expr.append(symbolOf(op));
            refreshDisplay();
            speakKey(opVoice(op));
            return;
        }
        double value = parseCurrent();
        if (pendingOp != null && !startNewInput) {
            value = apply(leftOperand, value, pendingOp);
            current.setLength(0);
            current.append(format(value));
        } else {
            leftOperand = value;
        }
        pendingOp = op;
        expr.setLength(0);
        expr.append(format(value)).append(" ").append(symbolOf(op));
        startNewInput = true;
        lastWasOperator = true;
        refreshDisplay();
        speakKey(opVoice(op));
    }

    private void calculate() {
        if (pendingOp == null) {
            speakKeyThenNumber("equal", current.toString());
            return;
        }
        double right = parseCurrent();
        expr.append(" ").append(format(right)).append(" =");
        String exprSnapshot = expr.toString();          // 清空前保存算式（"5 + 3 ="）
        double result = apply(leftOperand, right, pendingOp);
        String resultStr = format(result);
        // 持久化到历史记录页（算式不含尾部等号，结果单独存）
        HistoryActivity.addEntry(this, exprSnapshot.replaceAll(" =\\s*$", ""), resultStr);

        expr.setLength(0);
        current.setLength(0);
        current.append(resultStr);
        pendingOp = null;
        leftOperand = result;
        startNewInput = true;
        lastWasOperator = false;
        setHistoryLine(exprSnapshot, resultStr);
        display.setText(resultStr);
        fitDisplayText();
        speakKeyThenNumber("equal", resultStr);
    }

    // 显示区上方历史小字：灰色算式 + 橙红结果（仿原版 Pro）
    private void setHistoryLine(String exprPart, String resultStr) {
        String e = exprPart == null ? "" : exprPart;
        String full = e + " " + resultStr;
        SpannableString ss = new SpannableString(full);
        ss.setSpan(new ForegroundColorSpan(funcText), 0, e.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(cText), e.length(), full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        expression.setText(ss);
    }

    private void clearAll() {
        current.setLength(0); current.append("0");
        expr.setLength(0);
        pendingOp = null; leftOperand = 0;
        startNewInput = true; lastWasOperator = false;
        refreshDisplay();
        speakKey("ac");
    }

    private void backspace() {
        String s = current.toString();
        if (startNewInput || s.length() <= 1) {
            current.setLength(0); current.append("0"); startNewInput = true;
        } else {
            current.deleteCharAt(s.length() - 1);
        }
        refreshDisplay();
        speakKey("del");
    }

    private void percent() {
        double v = parseCurrent() / 100.0;
        current.setLength(0);
        current.append(format(v));
        startNewInput = false;
        refreshDisplay();
        speakKey("percent");
    }

    private void scientific(String f) {
        if (f.equals("π") || f.equals("e")) {
            String val = f.equals("π") ? "3.141592653589793" : "2.718281828459045";
            current.setLength(0); current.append(val);
            startNewInput = true;
            refreshDisplay();
            speakKey(f.equals("π") ? "pi" : "e");
            return;
        }
        if (f.equals("(") || f.equals(")")) {
            speakKey(f.equals("(") ? "leftbracket" : "rightbracket");
            return;
        }
        double v = parseCurrent();
        double r = 0;
        boolean ok = true;
        String voice = f;
        if (f.equals("sin")) {
            double ang = degreeMode ? Math.toRadians(v) : v;
            r = secondFunc ? Math.asin(ang) : Math.sin(ang);
            if (secondFunc) r = degreeMode ? Math.toDegrees(r) : r;
            voice = secondFunc ? "arcsin" : "sin";
        } else if (f.equals("cos")) {
            double ang = degreeMode ? Math.toRadians(v) : v;
            r = secondFunc ? Math.acos(ang) : Math.cos(ang);
            if (secondFunc) r = degreeMode ? Math.toDegrees(r) : r;
            voice = secondFunc ? "arccos" : "cos";
        } else if (f.equals("tan")) {
            double ang = degreeMode ? Math.toRadians(v) : v;
            r = secondFunc ? Math.atan(ang) : Math.tan(ang);
            if (secondFunc) r = degreeMode ? Math.toDegrees(r) : r;
            voice = secondFunc ? "arctan" : "tan";
        } else if (f.equals("ln")) { if (v > 0) r = Math.log(v); else ok = false; }
        else if (f.equals("lg")) { if (v > 0) r = Math.log10(v); else ok = false; voice = "log"; }
        else if (f.equals("√")) { if (v >= 0) r = Math.sqrt(v); else ok = false; voice = "radical"; }
        else if (f.equals("n!")) { r = factorial(v); voice = "factorial"; if (Double.isNaN(r)) ok = false; }
        else if (f.equals("1/X")) { if (v != 0) r = 1.0 / v; else ok = false; voice = "reciprocal"; }
        if (ok) {
            current.setLength(0);
            current.append(format(r));
            startNewInput = true;
            refreshDisplay();
            String sciExpr = voice + "(" + format(v) + ")";
            setHistoryLine(sciExpr, format(r));
            HistoryActivity.addEntry(this, sciExpr, format(r));
            speakKeyThenNumber(voice, format(r));
        } else {
            Toast.makeText(this, "参数无效", Toast.LENGTH_SHORT).show();
        }
    }

    private double factorial(double x) {
        if (x < 0 || x != Math.floor(x) || x > 170) return Double.NaN;
        double r = 1;
        for (int i = 2; i <= (int) x; i++) r *= i;
        return r;
    }

    private double parseCurrent() {
        try { return Double.parseDouble(current.toString()); }
        catch (Exception e) { return 0; }
    }

    private double apply(double a, double b, String op) {
        try {
            if (op.equals("+")) return new BigDecimal(a).add(new BigDecimal(b), MC).doubleValue();
            if (op.equals("−") || op.equals("-")) return new BigDecimal(a).subtract(new BigDecimal(b), MC).doubleValue();
            if (op.equals("×")) return new BigDecimal(a).multiply(new BigDecimal(b), MC).doubleValue();
            if (op.equals("÷")) {
                if (b == 0) { Toast.makeText(this, "除数不能为零", Toast.LENGTH_SHORT).show(); return 0; }
                return new BigDecimal(a).divide(new BigDecimal(b), MC).doubleValue();
            }
            if (op.equals("^")) return Math.pow(a, b);
        } catch (Exception ignored) {}
        return b;
    }

    private String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "错误";
        BigDecimal bd = new BigDecimal(Double.toString(v)).stripTrailingZeros();
        if (bd.scale() < 0) bd = bd.setScale(0);
        String s = bd.toPlainString();
        if (s.length() > 15) s = bd.round(MC).toPlainString();
        return s;
    }

    private String symbolOf(String op) {
        if (op.equals("+")) return "+";
        if (op.equals("−")) return "−";
        if (op.equals("×")) return "×";
        if (op.equals("÷")) return "÷";
        if (op.equals("^")) return "^";
        return op;
    }

    private void refreshDisplay() {
        fitDisplayText();
        display.setText(current.toString());
        if (expr.length() > 0) expression.setText(expr.toString());
    }

    private void showSettings() {
        String base = "【为什么会有这个项目】\n"
                + "以前就喜欢「计算器 Pro」，用了好几年。最近它更新了，广告弹个不停，"
                + "让人十分不悦，于是决定自己动手，做一个纯净的替代品。\n\n"
                + "【与其他计算器的区别】\n"
                + "· 零广告、零追踪、零网络权限，不申请任何权限，数据不出设备\n"
                + "· 真人语音播报，按键即读、无缝衔接，不是冷冰冰的 TTS\n"
                + "· 界面借鉴了多款收费计算器的设计，兼顾美观与易用\n"
                + "· 完全离线，安装包仅约 2.4MB，无后台服务\n\n"
                + "【功能列表】\n"
                + "· 标准计算：加减乘除、百分比、正负、退位\n"
                + "· 科学计算：三角函数、对数、幂、阶乘、括号\n"
                + "· 单位转换：长度/面积/体积/质量/数据/速度/时间/温度，八大类 60+ 单位\n"
                + "· 历史记录：独立历史页，支持搜索、回填与清空\n"
                + "· 语音音量：静音/低/中/高 四档，转换页与主界面通用\n"
                + "· 主题：深浅双主题，跟随系统或手动锁定\n"
                + "· 横屏适配：横屏自动切换左右布局\n\n"
                + "【开源声明】\n"
                + "本项目基于 MIT License 开源，可自由下载、修改与再分发，"
                + "但请保留原作者署名与许可声明。\n"
                + "本项目的界面、图标与语音素材均为作者原创制作。\n\n"
                + "brawnman\n"
                + "2026.11.25";
        SpannableString ss = new SpannableString(base);
        int start = base.indexOf("MIT License");
        if (start >= 0) {
            ClickableSpan link = new ClickableSpan() {
                @Override public void onClick(View widget) { showLicense(); }
                @Override public void updateDrawState(TextPaint ds) {
                    ds.setColor(equalBg);            // 主题绿，模拟 [MIT License](LICENSE) 链接样式
                    ds.setUnderlineText(false);
                }
            };
            ss.setSpan(link, start, start + "MIT License".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("关于 · 语音计算器")
                .setMessage(ss)
                .setPositiveButton("关闭", null)
                .show();
        // 让"MIT License"可点击
        TextView msg = dlg.findViewById(android.R.id.message);
        if (msg != null) msg.setMovementMethod(LinkMovementMethod.getInstance());
    }

    // MIT License 许可证摘要（对应源码仓库 LICENSE 文件）
    private void showLicense() {
        new AlertDialog.Builder(this)
                .setTitle("LICENSE · MIT")
                .setMessage("Copyright (c) 2026 brawnman\n\n"
                        + "本软件依据 MIT License 授权，可免费下载、使用、复制、修改、"
                        + "合并、发布、分发和/或销售，但保留原作者署名与许可声明。\n\n"
                        + "本软件按“现状”提供，不附带任何担保。\n\n"
                        + "完整许可证文本见本项目源码仓库中的 LICENSE 文件。")
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showHistory() {
        // 跳转独立的 HistoryActivity（仿原版 Pro 的历史页面）
        Intent i = new Intent(this, HistoryActivity.class);
        startActivityForResult(i, REQ_HISTORY);
    }

    private static final int REQ_HISTORY = 1001;

    // 点击历史记录某条 -> 回填结果到计算器
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HISTORY && resultCode == RESULT_OK && data != null) {
            String v = data.getStringExtra(HistoryActivity.EXTRA_RESULT);
            if (v != null) {
                current.setLength(0);
                current.append(v);
                expr.setLength(0);
                pendingOp = null;
                leftOperand = 0;
                startNewInput = true;
                lastWasOperator = false;
                refreshDisplay();
                speakNumber(v);
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onStop() {
        super.onStop();
        // 离开界面时停止播报，避免后台继续发声
        stopVoice();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopVoice();
        cursorHandler.removeCallbacks(cursorBlink);
    }
}
