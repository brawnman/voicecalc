# 语音计算器（VoiceCalc）

一个纯净、零权限、完全离线的 Android 计算器。不申请任何权限、无广告、无网络、无后台服务，语音播报为内置音频，数据不出设备。

## 功能

- 标准计算：加减乘除、百分比、正负、退位
- 科学计算：三角函数、对数、幂、阶乘、括号
- 单位转换：长度/面积/体积/质量/数据/速度/时间/温度，八大类 60+ 单位
- 语音播报：按键即读、结果连读，支持两套人声（女人声音 / 男人声音）切换，四档音量
- 历史记录：独立历史页，支持搜索、回填与清空
- 深浅双主题，跟随系统或手动锁定
- 横屏自动切换左右布局

## 技术说明

非 Gradle 项目，用 Android SDK 命令行工具手工构建：JDK 17 + aapt2 + javac + d8 + apksigner。

- 包名：`com.voicecalc.app`
- 版本：2.0（versionCode 2）
- 最低 SDK：21（Android 5.0），目标 SDK：34
- 内置语音：236 段 OGG（32kHz 单声道），位于 `res/raw/`

## 构建

`build.sh` 中的 SDK / JDK 路径为作者本机的绝对路径，需按你的环境修改开头几行：

```bash
SRC="/d/Program Files/AndroidBuild/sdk"      # Android SDK 路径
JDK="/d/Program Files/AndroidBuild/jdk-17"   # JDK 17 路径
PROJ="..."                                   # 项目路径
```

依赖的 SDK 组件：`build-tools/34.0.0`（aapt2、d8、apksigner、zipalign）、`platforms/android-34/android.jar`。

签名密钥密码从环境变量 `KEYPASS` 读取，未设置时回退默认值（仅本地开发用）。首次构建会在项目根目录生成 `calc.jks`（已被 .gitignore 排除）。

```bash
export KEYPASS=你的密码
bash build.sh
```

产物：`build/voicecalc.apk`

## 目录结构

```
.
├── AndroidManifest.xml
├── build.sh          # 构建脚本
├── java/             # 3 个 Activity 源码
├── res/              # 布局、图标、236 段语音
├── tools/            # 图标制作辅助脚本（非构建必需）
├── LICENSE           # MIT License
└── README.md
```

## 许可证

MIT License —— 可自由下载、使用、修改、再分发与商用，唯一要求是保留原作者署名与许可声明。详见 [LICENSE](LICENSE)。

Copyright (c) 2026 brawnman
