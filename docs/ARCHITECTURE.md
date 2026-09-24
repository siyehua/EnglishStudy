# Architecture

## Module map

```
Android/app/src/main/java/com/siyehua/egnlishstudy/
├── MainActivity.kt                 Navigation host + Activity-scoped view models
├── model/
│   └── ContentModels.kt            Content / Dialogue / DialogueLine / Article / Blog / News
├── data/
│   ├── FetchDataManager.kt         Remote fetch + local cache façade
│   ├── ContentCacheDatabase.kt     SQLite cache (contents, favourites, audio cache)
│   ├── TtsAudioManager.kt          Audio download / TTS / caching
│   ├── SentenceSplitter.kt         Splits article bodies into sentences
│   ├── LessonQueueHolder.kt        Home-screen lesson order handed to playback
│   ├── CaptionStyleStore.kt        Desktop-caption style + on/off preference
│   └── wordform/                   Word-form API client, models, repositories
├── playback/
│   ├── PlaybackBus.kt              PlaybackInfo snapshot + command bus (singleton)
│   ├── PlaybackNotificationService.kt  Foreground media service, notification, MediaSession
│   └── DesktopCaptionOverlay.kt    Floating subtitle window (TYPE_APPLICATION_OVERLAY)
├── ui/
│   ├── ContentViewModel.kt         Home list state, filters, paging
│   ├── ContentAudioViewModel.kt    All playback logic (MediaPlayer, queue, loop, captions)
│   ├── components/GlobalPlayerBar.kt   Shared player bar used by every screen
│   ├── screens/                    List, Detail, Favourites, SentenceDetail, CaptionSettings
│   ├── wordinsight/                Word insight sheet + clickable reading text
│   └── theme/                      Color, Theme, Type
```

## Navigation

`MainNavigation` (in `MainActivity.kt`) holds a `NavHost` with the destinations:

| Route | Screen |
| --- | --- |
| `list` | `ContentListScreen` (home) |
| `detail` | `ContentDetailScreen` |
| `favorites` | `FavoritesScreen` |
| `sentence` | `SentenceDetailScreen` |
| `word` | `WordInsightScreen` |
| `captionSettings` | `CaptionSettingsScreen` |

`selectedContent` / `selectedWord` / `selectedFavorite` are `remember`ed state
in `MainNavigation`; screens read them when they are (re)composed.

## View model scoping

- **`ContentAudioViewModel` is Activity-scoped** (`viewModel(viewModelStoreOwner = activity)`).
  This is what makes audio survive navigation: leaving the detail screen no
  longer destroys the player. It is created once and passed into the screens
  that need it.
- `ContentViewModel` stays screen-scoped to the `list` destination.
- `WordInsightViewModel` is screen-scoped to the sheet/detail screens.

`ContentAudioViewModel` is the single source of truth for playback; the service
and the caption overlay are pure consumers.

## State flow

```
ContentAudioViewModel
   ├── uiState: StateFlow<AudioUiState>        Idle | Preparing | Playing | Paused | Error
   │       ├── currentSentenceIndex, position, duration
   │       ├── isLoopingSingle / isLoopingLesson / isMuted / isCaptionOn
   │       └── consumed by GlobalPlayerBar, the detail screen, PlaybackBus
   ├── currentLesson: StateFlow<Content?>      which lesson is loaded
   ├── currentSentenceFlow: StateFlow<String>  current line text (player bar subtitle)
   ├── captionOnFlow: StateFlow<Boolean>       caption toggle (persisted)
   └── loopingLessonFlow: StateFlow<Boolean>   lesson-loop toggle
```

### PlaybackBus

The ViewModel and the foreground service run in the same process but are
decoupled through `PlaybackBus`:

- `PlaybackBus.info: StateFlow<PlaybackInfo?>` — the service renders a
  notification from this. `null` means "stop the service".
- `PlaybackBus.commands: SharedFlow<Command>` — `PAUSE / RESUME / STOP / NEXT /
  PREV / TOGGLE_LESSON_LOOP / TOGGLE_CAPTION`.
- `PlaybackBus.ownerId` — only the ViewModel instance that last published
  non-null state reacts to commands. This prevents stale screen-scoped
  instances from hijacking the notification.
- A ViewModel only publishes `null` when it already owns the bus, so other
  screens' instances cannot accidentally dismiss a live notification.

The reverse direction (service → ViewModel) uses explicit actions:

- Notification buttons are `PendingIntent.getBroadcast` into a receiver
  registered by the service (`ACTION_PLAY/PAUSE/STOP/NEXT/PREV/TOGGLE_LOOP/
  TOGGLE_CAPTION`). Broadcasts are used instead of `startService` because they
  are not subject to background-service start restrictions.
- When the overlay permission is missing the service broadcasts
  `ACTION_NEED_OVERLAY_PERMISSION`; `MainActivity` listens and opens the system
  permission page.

## Content loading

`FetchDataManager`:

1. `loadCachedContent()` — read everything from SQLite first (instant start).
2. `refreshRemoteContent()` — `POST /contents`, then upsert into the cache.
3. `queryCachedContent(types, levels, sources, limit, offset)` — filtered,
   paged reads for the home list.

Note: Android's `SQLiteQueryBuilder` only accepts the `offset, count` form of a
LIMIT clause; `count OFFSET offset` throws `IllegalArgumentException` on API ≤ 29.

## Word insight

`WordInsightSheet` is fed by `WordInsightViewModel`, which calls the backend
through the repositories in `data/wordform/`:

- `/word-form` → headword, relation (e.g. *past tense of*), contraction expansion
- `/word-pronunciation` → IPA
- `/word-phonics` → syllable / phonics breakdown
- `/word-meaning` → Chinese meanings + sentence translation

A local-first resolver on the backend handles contractions and irregular forms
before any LLM fallback is used.

## Theming

`ui/theme/Color.kt` defines the palette (`StudyGreen`, `StudyMint`,
`StudyBackground`, `StudyDarkSurface`, …) and `Theme.kt` maps it to Material 3
light/dark schemes plus an 8 dp shape scale. Screens should read colours from
`MaterialTheme.colorScheme` (so dark mode works) and only use the raw `Study*`
colours for brand accents on the green header surfaces.
