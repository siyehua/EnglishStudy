# 构建、发版与调试

## Android 应用

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd Android

./gradlew :app:assembleRelease      # release APK
./gradlew :app:assembleDebug        # debug APK (installable, logs kept)
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

### Release 配置

`app/build.gradle.kts`:

- `minSdk = 29`, `targetSdk = 36`, `compileSdk` from AGP 9.2.1, Kotlin 2.2.10,
  Compose BOM 2026.06.00.
- Signed with the debug keystore so the APK can be installed directly for
  testing — replace `signingConfig` before publishing anywhere.
- R8 is enabled:

```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("debug")
        optimization { enable = true }
    }
}
```

`gradle.properties` must contain:

```properties
android.r8.gradual.support=true
```

Without it AGP 9 fails with *"Cannot use optimization.enable=true without
setting android.r8.gradual.support flag"*.

Effect: the APK shrinks from ≈ 47 MB (all of Compose, Ktor, OkHttp and
material-icons-extended shipped unshrunk) to ≈ 2.9 MB.

If R8 ever strips something needed at runtime, add keep rules via
`proguardFiles(...)` in the same `optimization` block rather than disabling R8.

### APK 内容大小

- `classes.dex` should be a few MB, not tens of MB. A huge dex means minification
  got turned off.
- Material icons are only pulled in per icon that is referenced, so adding a new
  icon means adding an import, not a dependency.

## 后端

```bash
cd Backend
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python -m app.main          # → http://127.0.0.1:8000
./.venv/bin/python -m unittest discover # tests
```

Production deployment (supervisor + Caddy path route) is documented in
[../../Backend/README.md](../../Backend/README.md). Summary:

- directory `/opt/EnglishStudy/Backend` on the Guangzhou server
  (`tencentCloud-guangzhou`, `43.139.205.128`),
- virtualenv `.venv`, process managed by supervisor as `english-study-api`,
- listening on `127.0.0.1:8000`,
- exposed to the internet as **`https://handwriter.asia/english`** through
  Caddy's `handle_path /english/*`.

```bash
supervisorctl -c /etc/supervisor/supervisord.conf status english-study-api
supervisorctl -c /etc/supervisor/supervisord.conf restart english-study-api
tail -f /opt/EnglishStudy/Backend/log/stdout.log
```

Validate Caddy before reloading:

```bash
/usr/local/bin/caddy-duckdns validate --config /etc/caddy/Caddyfile --adapter caddyfile
systemctl reload caddy
```

## 模拟器调试

```bash
$ANDROID_HOME/emulator/emulator -avd TingApi29 -no-snapshot-save -no-audio &
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.siyehua.egnlishstudy/.MainActivity

# crash / logs
adb logcat -d | grep -A20 "FATAL EXCEPTION"

# UI tree (text + bounds)
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml

# playback / notification state
adb shell dumpsys media_session | grep PlaybackState
adb shell dumpsys notification --noredact | grep -A14 "id=42"

# search the current screen
adb shell cat /sdcard/ui.xml | grep -oE 'text="[^"]{2,40}"'
```

Notes:

- `uiautomator dump` occasionally returns stale or empty trees immediately after
  an animation; re-run it after a short delay.
- Buttons inside a `bottomBar` may report `bounds="[0,0][0,0]"` even though they
  are visible and clickable — verify with `dumpsys window` frames or by tapping.
- The overlay caption shows up in `dumpsys window windows` as a window owned by
  the app other than `MainActivity`.

## 版本号

`versionCode` / `versionName` live in `app/build.gradle.kts`. Bump them when
sending an APK to a tester: the version is visible under
*Settings → Apps → English Study* and makes it obvious which build is installed.

## 悬浮窗权限

- The app requests it automatically the first time the caption is enabled.
- To grant it manually for emulator testing:

```bash
adb shell appops set com.siyehua.egnlishstudy SYSTEM_ALERT_WINDOW allow
```
