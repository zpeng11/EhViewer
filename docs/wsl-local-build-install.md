# EhViewer WSL (This Machine) Setup And Install Guide

This guide is verified for the current local environment:

- Project path: `/home/eleven/EhViewer`
- Android SDK path: `/home/eleven/Android`
- Wireless ADB target: `192.168.2.93:36729`
- Shell: `zsh`

## 1. Install Linux build dependencies

```bash
sudo apt-get update
sudo apt-get install -y \
  git make autoconf automake libtool libtool-bin pkg-config \
  unzip zip curl ca-certificates
```

## 2. Configure Android SDK environment variables

Append to `~/.zshrc`:

```bash
cat >> ~/.zshrc <<'EOF'
export ANDROID_HOME="$HOME/Android"
export ANDROID_SDK_ROOT="$HOME/Android"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
source ~/.zshrc
```

## 3. Install Android SDK packages required by this project

```bash
yes | "$HOME/Android/cmdline-tools/latest/bin/sdkmanager" --licenses
"$HOME/Android/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" \
  "build-tools;36.1.0" \
  "cmake;3.31.6" \
  "platform-tools" \
  "ndk;29.0.14206865"
```

Verify installed packages:

```bash
export ANDROID_HOME="$HOME/Android" ANDROID_SDK_ROOT="$HOME/Android"
"$HOME/Android/cmdline-tools/latest/bin/sdkmanager" --list_installed
```

## 4. Install Rust toolchain required by this project

```bash
curl https://sh.rustup.rs -sSf | sh -s -- -y --profile minimal
source "$HOME/.cargo/env"

rustup toolchain install nightly-2026-02-15 -c rustfmt -c clippy -c rust-src
rustup target add aarch64-linux-android x86_64-linux-android thumbv7neon-linux-androideabi \
  --toolchain nightly-2026-02-15
```

Verify:

```bash
source "$HOME/.cargo/env"
rustup toolchain list
rustup target list --toolchain nightly-2026-02-15 --installed
```

## 5. Set project sdk path

From project root:

```bash
cd /home/eleven/EhViewer
printf "sdk.dir=%s\n" "$HOME/Android" > local.properties
cat local.properties
```

Expected:

```text
sdk.dir=/home/eleven/Android
```

## 6. Full release build (both flavors)

```bash
cd /home/eleven/EhViewer
export ANDROID_HOME="$HOME/Android" ANDROID_SDK_ROOT="$HOME/Android"
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
source "$HOME/.cargo/env"

./gradlew :app:prepareLibraryDefinitionsDefaultRelease :app:prepareLibraryDefinitionsMarshmallowRelease --rerun -Prelease
./gradlew assembleRelease -Prelease
```

## 7. Build outputs

APKs:

```text
app/build/outputs/apk/default/release/app-default-arm64-v8a-release.apk
app/build/outputs/apk/default/release/app-default-x86_64-release.apk
app/build/outputs/apk/default/release/app-default-armeabi-v7a-release.apk
app/build/outputs/apk/default/release/app-default-universal-release.apk
app/build/outputs/apk/marshmallow/release/app-marshmallow-arm64-v8a-release.apk
app/build/outputs/apk/marshmallow/release/app-marshmallow-x86_64-release.apk
app/build/outputs/apk/marshmallow/release/app-marshmallow-armeabi-v7a-release.apk
app/build/outputs/apk/marshmallow/release/app-marshmallow-universal-release.apk
```

Mappings and native debug symbols:

```text
app/build/outputs/mapping/defaultRelease/mapping.txt
app/build/outputs/mapping/marshmallowRelease/mapping.txt
app/build/outputs/native-debug-symbols/defaultRelease/native-debug-symbols.zip
app/build/outputs/native-debug-symbols/marshmallowRelease/native-debug-symbols.zip
```

## 8. Install release APK to this wireless ADB device

Connect and install (default universal release):

```bash
"$HOME/Android/platform-tools/adb" connect 192.168.2.93:36729
"$HOME/Android/platform-tools/adb" -s 192.168.2.93:36729 install -r \
  /home/eleven/EhViewer/app/build/outputs/apk/default/release/app-default-universal-release.apk
```

Verify installed version:

```bash
"$HOME/Android/platform-tools/adb" -s 192.168.2.93:36729 shell \
  dumpsys package moe.tarsin.ehviewer | rg -n "versionName|versionCode|lastUpdateTime"
```

## 9. Optional: quick one-shot command

```bash
cd /home/eleven/EhViewer && \
export ANDROID_HOME="$HOME/Android" ANDROID_SDK_ROOT="$HOME/Android" JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64" && \
source "$HOME/.cargo/env" && \
./gradlew :app:prepareLibraryDefinitionsDefaultRelease :app:prepareLibraryDefinitionsMarshmallowRelease --rerun -Prelease && \
./gradlew assembleRelease -Prelease && \
"$HOME/Android/platform-tools/adb" connect 192.168.2.93:36729 && \
"$HOME/Android/platform-tools/adb" -s 192.168.2.93:36729 install -r \
  /home/eleven/EhViewer/app/build/outputs/apk/default/release/app-default-universal-release.apk
```
