#!/usr/bin/env bash
# Builds build/sunset-home.apk with the bare Android SDK tools (no Gradle, no libraries).
# Same approach as dumbtv home's launcher/build.sh. Needs JDK 17 and platforms;android-28 + build-tools.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
JAR=$SDK/platforms/android-28/android.jar
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
[[ -f $JAR ]] || { echo "error: $JAR missing" >&2; exit 1; }
[[ -x $JAVA_HOME/bin/javac ]] || { echo "error: no JDK 17 at $JAVA_HOME" >&2; exit 1; }

KEYSTORE=$PWD/release.keystore
KEYPASS=sunsethome
if [[ ! -f $KEYSTORE ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias home -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KEYPASS" -keypass "$KEYPASS" -dname "CN=Sunset Home" >/dev/null
  echo "created signing key $KEYSTORE (keep it: updates must be signed with the same key)"
fi

WORK=$(mktemp -d "${TMPDIR:-/tmp}/sunset-home.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" build

"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml "$WORK/res.zip" \
  --min-sdk-version 28 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$WORK/gen" -o "$WORK/app.apk"

find src "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Xlint:-options -encoding UTF-8 \
  -classpath "$JAR" -d "$WORK/classes" @"$WORK/sources.txt"

find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$BT/d8" --release --min-api 28 --lib "$JAR" --output "$WORK" @"$WORK/classes.txt"
(cd "$WORK" && zip -q -X app.apk classes.dex)

"$BT/zipalign" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
  --out build/sunset-home.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/sunset-home.apk
echo "permissions: $("$BT/aapt2" dump permissions build/sunset-home.apk | grep -c uses-permission || true)"
printf 'built build/sunset-home.apk  %s KB\n' "$(( $(wc -c < build/sunset-home.apk) / 1024 ))"
