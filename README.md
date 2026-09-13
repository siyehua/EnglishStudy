# English Study

An English listening/reading app for EnglishPod-style lessons: a Compose Android
client plus a small FastAPI backend that serves content, word insight and audio.

```
EnglishStudy/
├── Android/   # Kotlin + Jetpack Compose app
└── Backend/   # FastAPI service (content, dictionary, TTS, sentence audio)
```

See [Backend/README.md](Backend/README.md) for API details, configuration and
deployment.

## What The App Does

**Home**

- Lesson list fetched from the backend, with type/level/source filters.
- Cards show title, level badge and a text preview (no avatar, no speaker name).
- Favourite entry point in the header.

**Lesson reading**

- A lesson is a single article: one card per transcript line.
- **Tap a line** → seek into the lesson audio and keep playing, with the active
  line highlighted and auto-scrolled into view.
- **Double-tap a word** → word insight sheet (pronunciation, IPA, Chinese
  meanings, word form/relations, source sentence).
- **Tap a playing line** reveals two actions under it:
  - ❤️ **Favourite** – stores the sentence audio + English text + Chinese
    translation.
  - 🔁 **Loop** – repeats that single sentence clip.

**Sentence detail** (opened from the favourites list)

- Immersive header with back, lesson title, 📄 *open original* and 🔁 loop.
- The sentence is tappable to play its clip; the *释义* block below shows the
  Chinese translation and can be collapsed.
- Double-tap words for the same word insight sheet as in the lesson.

**Word detail** (opened from the favourites list)

- Full-screen version of the word insight sheet, with the source sentence at the
  bottom.

**Favourites**

- Mixed list of favourited sentences and words, newest first, swipe-free delete
  via the trash button.
- Tapping an entry opens the matching detail page; the list refreshes on resume.
- Sentences saved by older builds are back-filled with their translation from
  the locally cached lesson the first time they are opened.

## Content And Audio

- Source: the community `bitter999/EnglishPod` transcripts (365 lessons).
- Each transcript line keeps its original text, its own Chinese translation
  (`trans`) and timestamps — nothing is re-recognised or machine-translated.
- Per-sentence audio is cut server-side by `GET /ting/segment` and padded by
  200 ms on both sides, so playback does not depend on imprecise seeking over
  the untagged VBR source MP3.

## Building The Android App

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd Android
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The backend base URL lives in
`Android/app/src/main/java/com/siyehua/egnlishstudy/data/wordform/WordFormApiClient.kt`
(`DEFAULT_BASE_URL`).

## Running The Backend

```bash
cd Backend
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Tests:

```bash
cd Backend && ./.venv/bin/python -m unittest discover
```
