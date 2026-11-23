package com.voicecalc.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单位转换 —— 长度 / 面积 / 体积 / 质量 / 数据 / 速度 / 时间 / 温度 八大类。
 * 与主界面同一套深浅主题（跟随系统或手动锁定），零权限、完全离线。
 */
public class ConvertActivity extends Activity {

    // ===== 双主题配色（与 MainActivity 保持一致）=====
    private int bg, panelBg, keyBg, keyBgPress, funcBg, line;
    private int numText, funcText, opText, cText, equalBg, equalPress, equalText, iconColor;
    private boolean isDark = false;
    private int themeOverride = -1;

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
            opText     = Color.parseColor("#5FAE86");
            cText      = Color.parseColor("#E8734A");
            equalBg    = Color.parseColor("#4A9B72");
            equalPress = Color.parseColor("#3D7E5D");
            equalText  = Color.parseColor("#FFFFFF");
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
            opText     = Color.parseColor("#4A9B72");
            cText      = Color.parseColor("#959595");
            equalBg    = Color.parseColor("#4A9B72");
            equalPress = Color.parseColor("#276443");
            equalText  = Color.parseColor("#FFFFFF");
            iconColor  = Color.parseColor("#959595");
        }
    }

    // ===== 转换类别定义 =====
    // 线性类别：factors 为各单位换算到基准单位的倍率；温度类特殊处理
    // voices 为各单位朗读语音资源名（GPT-SoVITS 合成的女声素材，unit_*.ogg）
    private static class Cat {
        final String name;
        final String[] units;
        final String[] voices;
        final double[] factors;
        final boolean temperature;
        Cat(String name, String[] units, String[] voices, double[] factors) {
            this(name, units, voices, factors, false);
        }
        Cat(String name, String[] units, String[] voices, double[] factors, boolean temperature) {
            this.name = name;
            this.units = units;
            this.voices = voices;
            this.factors = factors;
            this.temperature = temperature;
        }
    }

    private static final String[] V_LEN = {
            "unit_haomi", "unit_limi", "unit_mi", "unit_qianmi",
            "unit_yingcun", "unit_yingchi", "unit_ma", "unit_yingli", "unit_haili",
            "unit_shili", "unit_shichi", "unit_shicun"};
    private static final String[] V_AREA = {
            "unit_pingfang_haomi", "unit_pingfang_lim", "unit_pingfangmi", "unit_pingfang_qianmi",
            "unit_gongqing", "unit_mu", "unit_pingfang_yingcun", "unit_pingfang_yingchi", "unit_pingfang_yingli"};
    private static final String[] V_VOL = {
            "unit_haosheng", "unit_sheng", "unit_lifangmi", "unit_lifang_lim",
            "unit_meizhi_jialun", "unit_yingzhi_jialun", "unit_pint", "unit_yeangsi"};
    private static final String[] V_MASS = {
            "unit_haoke", "unit_ke", "unit_qianke", "unit_dun",
            "unit_angsi", "unit_bang", "unit_shijin", "unit_shiliang", "unit_kela"};
    private static final String[] V_DATA = {
            "unit_bite", "unit_zijie", "unit_qianzijie", "unit_zhaozijie",
            "unit_jizijie", "unit_taizijie", "unit_paizijie"};
    private static final String[] V_SPEED = {
            "unit_mi_meimiao", "unit_gongli_meixiaoshi", "unit_yingli_meixiaoshi",
            "unit_jie", "unit_yingchi_meimiao"};
    private static final String[] V_TIME = {
            "unit_miaomiao", "unit_miao", "unit_fenzhong", "unit_xiaoshi",
            "unit_tian", "unit_zhou", "unit_yue", "unit_nian"};
    private static final String[] V_TEMP = {
            "unit_sheshidu", "unit_huashidu", "unit_kaierwen"};

    private static final Cat[] CATS = {
            new Cat("长度", new String[]{
                    "毫米 mm", "厘米 cm", "米 m", "千米 km",
                    "英寸 in", "英尺 ft", "码 yd", "英里 mi", "海里 nmi",
                    "市里", "市尺", "市寸"}, V_LEN,
                    new double[]{0.001, 0.01, 1, 1000,
                    0.0254, 0.3048, 0.9144, 1609.344, 1852,
                    500, 1.0 / 3.0, 1.0 / 30.0}),
            new Cat("面积", new String[]{
                    "平方毫米 mm²", "平方厘米 cm²", "平方米 m²", "平方千米 km²",
                    "公顷 ha", "亩", "平方英寸 in²", "平方英尺 ft²", "平方英里 mi²"}, V_AREA,
                    new double[]{1e-6, 1e-4, 1, 1e6,
                    1e4, 2000.0 / 3.0, 6.4516e-4, 0.09290304, 2589988.110336}),
            new Cat("体积", new String[]{
                    "毫升 mL", "升 L", "立方米 m³", "立方厘米 cm³",
                    "美制加仑 gal", "英制加仑 gal", "美制品脱 pt", "美制液盎司 fl oz"}, V_VOL,
                    new double[]{0.001, 1, 1000, 0.001,
                    3.785411784, 4.54609, 0.473176473, 0.0295735295625}),
            new Cat("质量", new String[]{
                    "毫克 mg", "克 g", "千克 kg", "吨 t",
                    "盎司 oz", "磅 lb", "市斤", "市两", "克拉 ct"}, V_MASS,
                    new double[]{1e-6, 0.001, 1, 1000,
                    0.028349523125, 0.45359237, 0.5, 0.05, 0.0002}),
            new Cat("数据", new String[]{
                    "比特 bit", "字节 B", "KB", "MB", "GB", "TB", "PB"}, V_DATA,
                    new double[]{0.125, 1, 1024, 1048576, 1073741824.0,
                    1099511627776.0, 1125899906842624.0}),
            new Cat("速度", new String[]{
                    "米/秒 m/s", "公里/小时 km/h", "英里/小时 mph", "节 kn", "英尺/秒 ft/s"}, V_SPEED,
                    new double[]{1, 1.0 / 3.6, 0.44704, 1852.0 / 3600.0, 0.3048}),
            new Cat("时间", new String[]{
                    "毫秒 ms", "秒 s", "分钟 min", "小时 h",
                    "天 d", "周", "月（30天）", "年（365天）"}, V_TIME,
                    new double[]{0.001, 1, 60, 3600,
                    86400, 604800, 2592000, 31536000}),
            new Cat("温度", new String[]{
                    "摄氏度 °C", "华氏度 °F", "开尔文 K"}, V_TEMP,
                    new double[]{1, 1, 1}, true),
    };

    // ===== 状态（重建界面时保留）=====
    private int catIndex = 0;
    private int fromIdx = 0;
    private int toIdx = 2;
    private String inputText = "1";
    private String lastResult = "0";   // 最近一次换算结果数值（供语音播报）

    private EditText input;
    private TextView formulaView;
    private TextView resultView;
    private Spinner spinFrom;
    private Spinner spinTo;
    private final ArrayList<Button> tabButtons = new ArrayList<>();
    private boolean suppressSpinner = false;   // 程序化 setSelection 时不触发重算循环

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    // ===== 声音（与主界面共享音量档位偏好）=====
    private static final float[] VOLUME_STEPS = {0f, 0.3f, 0.6f, 1f};
    private static final String KEY_VOICE_LEVEL = "voiceLevel";
    private int voiceLevel = 3;
    private int voiceStyle = 0;
    private String voiceRes(String n) { return voiceStyle == 1 ? "old_" + n : n; }
    private int voiceGen = 0;
    private MediaPlayer curPlayer;
    private final ArrayList<MediaPlayer> voicePlayers = new ArrayList<>();
    private AudioTrack tickTrack;   // 操作提示音，程序合成不占素材

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 前台保亮 + 恢复主题偏好（与主界面一致）
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        themeOverride = getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE)
                .getInt(HistoryActivity.KEY_THEME, -1);
        voiceLevel = getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE)
                .getInt(KEY_VOICE_LEVEL, 3);
        voiceStyle = getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE)
                .getInt(HistoryActivity.KEY_VOICE_STYLE, 0);
        applyTheme();
        buildUI();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTheme();
        buildUI();
    }

    // ---------- 界面 ----------
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(8), 0, dp(8), dp(8));

        buildToolbar(root);
        buildTabs(root);
        buildInputRow(root);
        buildResultCard(root);

        setContentView(root);
        refreshAll();
    }

    private void buildToolbar(LinearLayout root) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(10), 0, dp(6));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 返回按钮
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(iconColor);
        back.setPadding(dp(8), dp(4), dp(16), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("单位转换");
        title.setTextSize(19);
        title.setTextColor(numText);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 音量档位（与主界面一致：静音→低→中→高 循环，切换后朗读当前输入）
        final ImageView sound = toolIcon(voiceLevel == 0 ? "ic_mute" : "ic_sound");
        sound.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                voiceLevel = (voiceLevel + 1) % VOLUME_STEPS.length;
                getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE).edit()
                        .putInt(KEY_VOICE_LEVEL, voiceLevel).apply();
                sound.setImageDrawable(iconDrawable(voiceLevel == 0 ? "ic_mute" : "ic_sound"));
                if (voiceLevel == 0) {
                    stopVoice();
                    Toast.makeText(ConvertActivity.this, "语音音量：静音", Toast.LENGTH_SHORT).show();
                } else {
                    String label = voiceLevel == 1 ? "低" : (voiceLevel == 2 ? "中" : "高");
                    Toast.makeText(ConvertActivity.this, "语音音量：" + label, Toast.LENGTH_SHORT).show();
                    speakConversion();
                }
            }
        });
        bar.addView(sound);

        // 音色切换（与主界面共享偏好）：女人声音 / 男人声音
        final ImageView voiceStyleIcon = toolIcon("ic_voice");
        voiceStyleIcon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                voiceStyle = voiceStyle == 0 ? 1 : 0;
                getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE).edit()
                        .putInt(HistoryActivity.KEY_VOICE_STYLE, voiceStyle).apply();
                Toast.makeText(ConvertActivity.this,
                        "音色：" + (voiceStyle == 0 ? "女人声音" : "男人声音"),
                        Toast.LENGTH_SHORT).show();
                if (voiceLevel > 0) speakConversion();
            }
        });
        bar.addView(voiceStyleIcon);

        // 主题切换（跟随系统 → 浅色 → 深色 循环，与主界面联动）
        ImageView theme = toolIcon(isDark ? "ic_moon" : "ic_sun");
        theme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleTheme(); }
        });
        bar.addView(theme);

        root.addView(bar);
    }

    // 循环切换主题并保存偏好（与主界面共享同一偏好键）
    private void cycleTheme() {
        if (themeOverride == -1) {
            int night = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            themeOverride = (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) ? 0 : 1;
        } else if (themeOverride == 0) {
            themeOverride = 1;
        } else {
            themeOverride = -1;
        }
        getSharedPreferences(HistoryActivity.PREF, MODE_PRIVATE).edit()
                .putInt(HistoryActivity.KEY_THEME, themeOverride).apply();
        String label = themeOverride == -1 ? "跟随系统" : (themeOverride == 0 ? "浅色主题" : "深色主题");
        Toast.makeText(this, "主题：" + label, Toast.LENGTH_SHORT).show();
    }

    // ---------- 声音 ----------
    // 操作提示音：60ms 轻柔"嘀"声（1250Hz 正弦 + 淡入淡出），音量随档位，静音档不响
    private void beep() {
        if (voiceLevel == 0) return;
        try {
            int sr = 44100;
            int n = (int) (sr * 0.06);
            short[] pcm = new short[n];
            double vol = VOLUME_STEPS[voiceLevel] * 0.5;   // 整体减半，轻而不吵
            for (int i = 0; i < n; i++) {
                double env = Math.min(1.0, Math.min(i / (0.005 * sr), (n - 1 - i) / (0.010 * sr)));
                double v = Math.sin(2 * Math.PI * 1250.0 * i / sr) * env * vol;
                pcm[i] = (short) Math.max(-32767, Math.min(32767, v * 32767));
            }
            if (tickTrack == null) {
                tickTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sr,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        n * 2, AudioTrack.MODE_STATIC);
            }
            try { tickTrack.stop(); } catch (Exception ignored) {}
            tickTrack.reloadStaticData();
            tickTrack.write(pcm, 0, n);
            tickTrack.play();
        } catch (Exception ignored) {}
    }

    private void stopVoice() {
        voiceGen++;
        for (MediaPlayer mp : voicePlayers) {
            try { mp.setOnCompletionListener(null); mp.setOnErrorListener(null); mp.stop(); mp.release(); } catch (Exception ignored) {}
        }
        voicePlayers.clear();
        curPlayer = null;
    }

    // 与主界面同款 gapless 播放链：预 prepare 全部片段、setNextMediaPlayer 无缝衔接、原速播放
    private void playNames(List<String> names) {
        stopVoice();
        if (voiceLevel == 0) return;
        final float vol = VOLUME_STEPS[voiceLevel];
        final int gen = voiceGen;

        List<String> list = (names instanceof ArrayList) ? names : new ArrayList<>(names);
        ArrayList<MediaPlayer> chain = new ArrayList<>();
        for (String n : list) {
            int id = getResources().getIdentifier(voiceRes(n), "raw", getPackageName());
            if (id == 0) continue;
            try {
                MediaPlayer mp = new MediaPlayer();
                AssetFileDescriptor afd = getResources().openRawResourceFd(id);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mp.prepare();
                mp.setVolume(vol, vol);
                chain.add(mp);
            } catch (Exception ignored) {}
        }
        if (chain.isEmpty()) return;
        voicePlayers.addAll(chain);

        for (int i = 0; i < chain.size(); i++) {
            final MediaPlayer cur = chain.get(i);
            final MediaPlayer next = (i < chain.size() - 1) ? chain.get(i + 1) : null;
            final boolean[] gapless = {false};
            if (next != null) {
                try { cur.setNextMediaPlayer(next); gapless[0] = true; } catch (Exception ignored) {}
            }
            final boolean linked = gapless[0];
            cur.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) {
                    if (gen != voiceGen) return;
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

    private void speakNumber(String s) {
        if (TextUtils.isEmpty(s)) return;
        List<String> l = numberToVoice(s.trim());
        if (!l.isEmpty()) playNames(l);
    }

    // 完整播报：数值 + 源单位 + 等于 + 结果 + 目标单位（GPT-SoVITS 合成单位语音 + 真人数字语音同一播放链）
    private void speakConversion() {
        Cat c = CATS[catIndex];
        if (fromIdx >= c.units.length || toIdx >= c.units.length) return;
        if (TextUtils.isEmpty(lastResult) || lastResult.equals("错误")) return;
        List<String> l = new ArrayList<>();
        l.addAll(numberToVoice(TextUtils.isEmpty(inputText) ? "0" : inputText.trim()));
        l.add(c.voices[fromIdx]);
        l.add("unit_dengyu");
        l.addAll(numberToVoice(lastResult));
        l.add(c.voices[toIdx]);
        playNames(l);
    }

    // 数字 → 真人语音序列（与主界面同一套中文读法），非法数字串不朗读
    private List<String> numberToVoice(String s) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(s)) return out;
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

        List<String> groups = new ArrayList<>();
        for (int end = num.length(); end > 0; end = Math.max(0, end - 4)) {
            groups.add(num.substring(Math.max(0, end - 4), end));
        }
        Collections.reverse(groups);   // 高位在前

        String[] bigUnit = {"", "tenthousand", "hundredmillion", "tenthousand+hundredmillion"};
        boolean lastWritten = false;
        boolean needZero = false;
        for (int gi = 0; gi < groups.size(); gi++) {
            String g = groups.get(gi);
            int bigIdx = groups.size() - 1 - gi;
            int z = 0;
            while (z < g.length() && g.charAt(z) == '0') z++;
            String stripped = g.substring(z);
            if (stripped.isEmpty()) {
                if (lastWritten) needZero = true;
                continue;
            }
            if (gi > 0 && stripped.length() < 4 && lastWritten) needZero = true;
            if (needZero) { out.add("zero"); needZero = false; }
            addGroup(out, stripped, gi == 0);
            if (bigIdx > 0) {
                String[] units = bigUnit[bigIdx].split("\\+");
                for (String u : units) out.add(u);
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
                    out.add("ten");
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

    @Override
    protected void onStop() {
        super.onStop();
        stopVoice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVoice();
        if (tickTrack != null) {
            try { tickTrack.stop(); tickTrack.release(); } catch (Exception ignored) {}
            tickTrack = null;
        }
    }

    // 分类标签：单行横向滚动，选中项高亮
    private void buildTabs(LinearLayout root) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(2), dp(4), dp(2), dp(4));

        tabButtons.clear();
        for (int i = 0; i < CATS.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(CATS[i].name);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setPadding(dp(14), dp(6), dp(14), dp(6));
            b.setStateListAnimator(null);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(b, lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectCategory(idx); }
            });
            tabButtons.add(b);
        }
        scroll.addView(row);
        root.addView(scroll);
    }

    private void selectCategory(int idx) {
        beep();
        catIndex = idx;
        Cat c = CATS[idx];
        fromIdx = 0;
        toIdx = c.units.length > 2 ? 2 : c.units.length - 1;
        refreshAll();
    }

    // 输入行：数值输入 + 源单位 + 交换 + 目标单位
    private void buildInputRow(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        input = new EditText(this);
        input.setText(inputText);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setTextSize(16);
        input.setTextColor(numText);
        input.setHintTextColor(funcText);
        input.setHint("输入数值");
        input.setBackgroundDrawable(flatDrawable(funcBg, line));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c2) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c2) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                inputText = s.toString();
                refreshResult();
            }
        });
        row.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

        spinFrom = makeSpinner();
        row.addView(spinFrom, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f));

        // 交换单位按钮
        TextView swap = new TextView(this);
        swap.setText("⇄");
        swap.setTextSize(20);
        swap.setTextColor(opText);
        swap.setGravity(Gravity.CENTER);
        swap.setPadding(dp(6), dp(8), dp(6), dp(8));
        swap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                beep();
                int t = fromIdx; fromIdx = toIdx; toIdx = t;
                suppressSpinner = true;
                spinFrom.setSelection(fromIdx);
                spinTo.setSelection(toIdx);
                suppressSpinner = false;
                refreshResult();
            }
        });
        row.addView(swap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        spinTo = makeSpinner();
        row.addView(spinTo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f));

        root.addView(row);
    }

    private Spinner makeSpinner() {
        Spinner sp = new Spinner(this);
        sp.setBackgroundDrawable(flatDrawable(funcBg, line));
        sp.setPadding(dp(8), dp(6), dp(8), dp(6));
        sp.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (suppressSpinner) return;
                beep();
                if (parent == spinFrom) fromIdx = pos;
                else if (parent == spinTo) toIdx = pos;
                refreshResult();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return sp;
    }

    // 结果卡片：换算公式小字 + 结果大字 + 复制按钮
    private void buildResultCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(flatDrawable(keyBg, line));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, dp(6), 0, 0);
        root.addView(card, clp);

        formulaView = new TextView(this);
        formulaView.setTextSize(14);
        formulaView.setTextColor(funcText);
        formulaView.setGravity(Gravity.START);
        card.addView(formulaView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout resRow = new LinearLayout(this);
        resRow.setOrientation(LinearLayout.HORIZONTAL);
        resRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, dp(8), 0, 0);
        resRow.setLayoutParams(rlp);

        resultView = new TextView(this);
        resultView.setTextSize(32);
        resultView.setTextColor(opText);
        resultView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resultView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resultView.setText("0");
        resRow.addView(resultView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView copy = toolIcon("ic_copy");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                beep();
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("convert", resultView.getText().toString()));
                Toast.makeText(ConvertActivity.this, "已复制结果", Toast.LENGTH_SHORT).show();
            }
        });
        resRow.addView(copy);

        card.addView(resRow);

        // 说明小字
        TextView tip = new TextView(this);
        tip.setText("支持 长度 / 面积 / 体积 / 质量 / 数据 / 速度 / 时间 / 温度");
        tip.setTextSize(12);
        tip.setTextColor(funcText);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, dp(14), 0, 0);
        root.addView(tip, tlp);
    }

    // ---------- 刷新 ----------
    private void refreshAll() {
        // 分类标签高亮
        for (int i = 0; i < tabButtons.size(); i++) {
            boolean sel = i == catIndex;
            tabButtons.get(i).setTextColor(sel ? equalText : funcText);
            tabButtons.get(i).setBackgroundColor(sel ? equalBg : keyBg);
        }
        Cat c = CATS[catIndex];
        suppressSpinner = true;
        ArrayAdapter<String> ad = makeUnitAdapter(c);
        spinFrom.setAdapter(ad);
        spinTo.setAdapter(makeUnitAdapter(c));
        if (fromIdx >= c.units.length) fromIdx = 0;
        if (toIdx >= c.units.length) toIdx = c.units.length - 1;
        spinFrom.setSelection(fromIdx);
        spinTo.setSelection(toIdx);
        suppressSpinner = false;
        refreshResult();
    }

    private void refreshResult() {
        Cat c = CATS[catIndex];
        if (fromIdx >= c.units.length || toIdx >= c.units.length) return;
        double v = parseInput();
        String out;
        if (c.temperature) {
            out = convertTemperature(v, fromIdx, toIdx);
        } else {
            double base = v * c.factors[fromIdx];
            out = fmt(base / c.factors[toIdx]);
        }
        String inStr = TextUtils.isEmpty(inputText) ? "0" : inputText.trim();
        formulaView.setText(inStr + " " + c.units[fromIdx] + " =");
        String full = out + " " + c.units[toIdx];
        resultView.setText(full);
        lastResult = out;   // 缓存结果数值供完整播报使用
        // 结果字号自适应：内容越长字号越小，避免多行换行
        resultView.setTextSize(full.length() > 18 ? 22 : (full.length() > 13 ? 27 : 32));
    }

    private ArrayAdapter<String> makeUnitAdapter(final Cat c) {
        ArrayAdapter<String> ad = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, c.units) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(numText);
                tv.setTextSize(14);
                return tv;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(numText);
                tv.setTextSize(14);
                tv.setBackgroundColor(isDark ? Color.parseColor("#262626") : Color.WHITE);
                tv.setPadding(dp(12), dp(10), dp(12), dp(10));
                return tv;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return ad;
    }

    // ---------- 计算 ----------
    private double parseInput() {
        try { return Double.parseDouble(inputText.trim()); }
        catch (Exception e) { return 0; }
    }

    // 温度经摄氏度中转：0=°C 1=°F 2=K
    private String convertTemperature(double v, int from, int to) {
        double c;
        if (from == 0) c = v;
        else if (from == 1) c = (v - 32) * 5.0 / 9.0;
        else c = v - 273.15;
        double r;
        if (to == 0) r = c;
        else if (to == 1) r = c * 9.0 / 5.0 + 32;
        else r = c + 273.15;
        return fmt(r);
    }

    private String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "错误";
        // 先按 12 位有效数字舍入，消除浮点乘除带来的尾差（如 100mm²=0.00010000000000000002m²）
        BigDecimal bd = new BigDecimal(Double.toString(v)).round(MC).stripTrailingZeros();
        if (bd.scale() < 0) bd = bd.setScale(0);
        String s = bd.toPlainString();
        return s;
    }

    // ---------- 通用 ----------
    private android.graphics.drawable.GradientDrawable flatDrawable(int fill, int stroke) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);
        d.setStroke(dp(1), stroke);
        d.setCornerRadius(dp(4));
        return d;
    }

    private ImageView toolIcon(String resName) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(iconDrawable(resName));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return iv;
    }

    // 裁边+缩放+着色后的图标 Drawable（供 ImageView 换图复用）
    private Drawable iconDrawable(String resName) {
        int id = getResources().getIdentifier(resName, "drawable", getPackageName());
        android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeResource(getResources(), id);
        android.graphics.Bitmap glyph = trimTransparent(src);
        int target = dp(18);
        float ratio = Math.min((float) target / glyph.getWidth(), (float) target / glyph.getHeight());
        int nw = Math.max(1, Math.round(glyph.getWidth() * ratio));
        int nh = Math.max(1, Math.round(glyph.getHeight() * ratio));
        android.graphics.Bitmap out = android.graphics.Bitmap.createScaledBitmap(glyph, nw, nh, true);
        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), out);
        // 与主界面一致：保留美化图标原色，深色主题按比例提亮
        float brighten = isDark ? 2.3f : 1f;
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setScale(brighten, brighten, brighten, 1f);
        bd.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        return bd;
    }

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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
