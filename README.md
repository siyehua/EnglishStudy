# English Study

An English listening/reading app for EnglishPod-style lessons: a Kotlin +
Jetpack Compose Android client plus a small FastAPI backend that serves content,
word insight and audio.

```
EnglishStudy/
├── Android/   # Kotlin + Jetpack Compose app
└── Backend/   # FastAPI service (content, dictionary, TTS, sentence audio)
```

- 后端接口 / 配置 / 部署 → [Backend/README.md](Backend/README.md)
- **架构设计（SPEC）** → [docs/SPEC.md](docs/SPEC.md)
- **模块设计（cells）** → [docs/cells/](docs/cells/)
- 构建、发版与调试 → [docs/cells/build.md](docs/cells/build.md)

## 文档约定

| 文档 | 定位 |
| --- | --- |
| `README.md` | 总说明：项目是什么、怎么跑、有哪些功能 |
| `docs/SPEC.md` | 架构设计：分层、数据流、状态机、接口契约 |
| `docs/cells/*.md` | 每个模块 / 功能的设计，**唯一说明入口** |

**源码不写注释。** 代码本身即说明；设计意图、取舍原因与“不要这样做”的坑，统一写在
`docs/cells/` 对应文档中。改动功能时必须同步更新对应 cell。

---

## What The App Does

### Home

- Lesson list fetched from the backend, with type / level / source filters.
- Cards show title, level badge and a text preview (no avatar, no speaker name).
- Header actions (left → right): **Focus filters**, **Refresh**, **☰ Menu**.
  The menu contains **我的收藏 (Favourites)** and **字幕设置 (Caption settings)**.
- **Global player bar** pinned to the bottom (see below).

### Global player bar

One shared component (`GlobalPlayerBar`) rendered on **every** screen: home,
lesson detail and caption settings.

Layout intentionally mirrors a media notification:

```
┌───────────────────────────────────────────┐
│ 1: Difficult Customer                     │  ← title (lesson name)
│ Yes, we are. Today we're going to be…     │  ← subtitle = current sentence
│  🔁    ⏮    ▶/⏸    ⏭    📺               │  ← 5 buttons
│  ▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░               │  ← progress bar
└───────────────────────────────────────────┘
```

- Background extends edge-to-edge including the gesture navigation area;
  content stays above it via `navigationBarsPadding()`.
- Buttons: lesson loop · previous lesson · play/pause · next lesson · caption.
- The subtitle shows the **currently playing English sentence**, and falls back
  to a status string ("点播放开始收听") when nothing is playing.

### Lesson reading

- A lesson is a dialogue made of transcript lines, one card per line.
- **Tap a line** → seek into the lesson audio and keep playing; the active line
  is highlighted and auto-scrolled into view (only when it would otherwise be
  off-screen or hidden behind the player bar).
- **Double-tap a word** → word insight sheet (pronunciation, IPA, Chinese
  meanings, word form/relations, source sentence).
- Actions under the selected line:
  - ❤️ **收藏** – stores the sentence audio + English text + Chinese translation.
  - 🔁 **循环** – repeats that single sentence clip.
  - 🌐 **翻译** – expands/collapses the Chinese translation **inside the same
    bubble** as the English text. Collapses automatically when the selection
    moves to another line.
- Playback advances line by line; the selected line always follows playback.

### Playback

- Whole-lesson playback with per-line highlighting, driven by timestamps.
- **Lesson loop**: replays the same lesson when it ends.
- **Auto-advance**: when a lesson ends (and loop is off) the next lesson in the
  queue starts automatically.
- **Previous / next lesson** switch instantly and keep the UI in sync.
- Playback **continues when leaving the lesson detail screen** — going back to
  home does not stop it. Only an explicit pause/stop (in-app, notification or
  system media key) stops playback.
- Progress, speed and the current lesson are remembered; the lesson queue is
  taken from the order currently shown on the home screen.
- Playback is driven by a MediaPlayer owned by an Activity-scoped ViewModel and
  exposed to the system through a foreground service (see below).

### Notification & lock screen

- A foreground media service shows a standard `MediaStyle` notification with
  title, current sentence, progress and controls.
- MediaSession is registered, so lock-screen / headset / Bluetooth controls
  work.
- Buttons exposed to the notification are the **system-standard** media actions
  (previous / play-pause / next). Custom buttons are stripped by Android 13+
  for apps with `targetSdk >= 33` — see [docs/cells/notification.md](docs/cells/notification.md).

### Desktop caption (floating subtitle)

- Toggle with the 📺 button in the player bar (or the notification).
- Draws a floating overlay above all apps, showing the **current sentence**,
  auto-updating as playback advances.
- Multi-line: long sentences wrap and are never truncated.
- Touch-through: it never blocks taps on other apps.
- **The on/off preference is remembered** — after a restart the caption comes
  back automatically.
- Style is configurable — see below.

### Caption settings

Reached from **☰ Menu → 字幕设置** on the home screen.

- **Preview** card showing the current style.
- **Position** slider (5 %–85 % of screen height from the top).
- **Font size** slider (12–32 sp).
- **Text colour**: white / light yellow / light cyan / light orange / light
  green / light pink.
- **Background colour**: translucent black / translucent white / dark green /
  dark blue / dark orange / none.
- Changes apply **live** to a running caption and are persisted locally.
- The screen follows the app's design language (green rounded header, card
  layout) and adapts to the system dark theme.
- If the "display over other apps" permission is missing, the app redirects to
  the system settings page instead of silently failing.

### Sentence detail (from favourites)

- Immersive header with back, lesson title, 📄 *open original* and 🔁 loop.
- The sentence is tappable to play its clip; the *释义* block below shows the
  Chinese translation and can be collapsed.
- Double-tap words for the same word insight sheet as in the lesson.

### Word detail (from favourites)

- Full-screen version of the word insight sheet, with the source sentence at the
  bottom.

### Favourites

- Mixed list of favourited sentences and words, newest first, delete via the
  trash button.
- Tapping an entry opens the matching detail page; the list refreshes on resume.
- Sentences saved by older builds are back-filled with their translation from
  the locally cached lesson the first time they are opened.

---

## Content And Audio

- Source: the community `bitter999/EnglishPod` transcripts (365 lessons).
- Each transcript line keeps its original text, its own Chinese translation
  (`trans`) and timestamps — nothing is re-recognised or machine-translated.
- Per-sentence audio is cut server-side by `GET /ting/segment` and padded by
  200 ms on both sides, so playback does not depend on imprecise seeking over
  the untagged VBR source MP3.
- Content is cached locally (`ContentCacheDatabase`) so the app opens instantly
  and works offline for already-fetched lessons.

---

## Building The Android App

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd Android
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Release builds enable **R8** (`optimization { enable = true }` plus
`android.r8.gradual.support=true` in `gradle.properties`), which shrinks the APK
from ≈ 47 MB to ≈ 2.9 MB. See [docs/cells/build.md](docs/cells/build.md).

The backend base URL lives in
`Android/app/src/main/java/com/siyehua/egnlishstudy/data/wordform/WordFormApiClient.kt`
(`DEFAULT_BASE_URL`, currently `https://handwriter.asia/english`).

## Running The Backend

```bash
cd Backend
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python -m app.main          # binds 127.0.0.1:8000
```

Tests:

```bash
cd Backend && ./.venv/bin/python -m unittest discover
```

Production runs under supervisor on the Guangzhou server and is exposed as
`https://handwriter.asia/english` by Caddy (`handle_path /english/*`). Full
runbook: [Backend/README.md](Backend/README.md).
