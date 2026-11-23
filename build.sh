#!/usr/bin/env bash
set -e

# 签名密钥密码：优先读环境变量 KEYPASS，未设置时用默认值（仅本地开发用）
KEYPASS="${KEYPASS:-lUqAJru6urq3iBLGWiTfUHsv}"

SRC="/d/Program Files/AndroidBuild/sdk"
JDK="/d/Program Files/AndroidBuild/jdk-17"
BT="$SRC/build-tools/34.0.0"
ANDROID_JAR="$SRC/platforms/android-34/android.jar"
PROJ="E:/AiWork-TX/.workbuddy/2026-11-21-23-49-08/calculator-src"
OUT="$PROJ/build"

wpath() { cygpath -w "$1"; }
W_OUT=$(wpath "$OUT")
W_ANDROID_JAR=$(wpath "$ANDROID_JAR")

echo "===== 检查组件 ====="
for f in "$JDK/bin/javac.exe" "$BT/aapt2.exe" "$BT/lib/d8.jar" "$BT/lib/apksigner.jar" "$BT/zipalign.exe" "$ANDROID_JAR"; do
  if [ -e "$f" ]; then echo "OK  $f"; else echo "缺失 $f"; exit 1; fi
done

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/res"

echo "===== 1. 编译 Java ====="
find "$PROJ/java" -name "*.java" > "$OUT/sources.txt"
"$JDK/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "===== 2. d8 转 dex ====="
CLASS_ARGS=()
while IFS= read -r f; do
  CLASS_ARGS+=("$(cygpath -w "$f")")
done < <(find "$OUT/classes" -name "*.class")
"$JDK/bin/java.exe" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$W_ANDROID_JAR" --min-api 21 \
  --output "$OUT/dex" "${CLASS_ARGS[@]}"

echo "===== 3. aapt2 compile 资源 ====="
# 收集所有 res 下的文件，逐个编译为 .flat（目录参数 --dir 一次性编译）
"$BT/aapt2.exe" compile --dir "$PROJ/res" -o "$OUT/compiled.flata"

echo "===== 4. aapt2 link 生成含资源的 APK ====="
"$BT/aapt2.exe" link -I "$ANDROID_JAR" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -R "$OUT/compiled.flata" \
  --auto-add-overlay \
  --java "$OUT/gen" \
  --min-sdk-version 21 --target-sdk-version 34 \
  -o "$OUT/base.apk"
echo "  base.apk 大小: $(du -h "$OUT/base.apk" | cut -f1)"

echo "===== 5. 合并 dex 进 APK ====="
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && "$JDK/bin/jar.exe" uf "$(cygpath -w "$OUT/unsigned.apk")" classes.dex )

echo "===== 6. zipalign ====="
"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "===== 7. 生成签名密钥（放在 build 外，避免 rm -rf 丢失）====="
KEYSTORE="$PROJ/calc.jks"
W_KS=$(wpath "$KEYSTORE")
if [ ! -f "$KEYSTORE" ]; then
  "$JDK/bin/keytool.exe" -genkeypair -v -keystore "$W_KS" \
    -alias voicecalc -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KEYPASS" -keypass "$KEYPASS" \
    -dname "CN=VoiceCalc, OU=OpenSource, O=Personal, L=NA, ST=NA, C=CN" >/dev/null 2>&1
fi

echo "===== 8. 签名 ====="
"$JDK/bin/java.exe" -jar "$BT/lib/apksigner.jar" sign --ks "$W_KS" \
  --ks-key-alias voicecalc --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/voicecalc.apk" "$OUT/aligned.apk"

echo "===== 9. 验证 ====="
"$JDK/bin/java.exe" -jar "$BT/lib/apksigner.jar" verify --print-certs "$OUT/voicecalc.apk" 2>&1 | head -5
echo "=== APK 内 ogg 语音数量 ==="
unzip -l "$OUT/voicecalc.apk" 2>/dev/null | grep -c 'res/raw/.*\.ogg'
echo "=== APK 内容检查 ==="
"$BT/aapt2.exe" dump badging "$OUT/voicecalc.apk" 2>&1 | grep -iE "package|application-label|application-icon|launchable" | head -6
echo "=== 权限（应为空）==="
"$BT/aapt2.exe" dump badging "$OUT/voicecalc.apk" 2>&1 | grep -i "uses-permission" || echo "  无任何权限"
ls -lh "$OUT/voicecalc.apk"
echo "完成: $OUT/voicecalc.apk"
