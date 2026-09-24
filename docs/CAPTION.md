# Desktop Caption (Floating Subtitle)

The desktop caption is a floating overlay that shows the sentence currently
being played, on top of every other app — like the desktop lyrics of a music
player.

## Behaviour

- Toggled by the 📺 button in the player bar (and by the caption action in the
  notification).
- Shows the **current sentence**, updating in real time as playback advances
  (next lesson too).
- **Multi-line**: long sentences wrap onto as many lines as needed and are never
  truncated (`maxLines = Int.MAX_VALUE`).
- **Touch-through** (`FLAG_NOT_TOUCHABLE`) and non-focusable
  (`FLAG_NOT_FOCUSABLE`), so it never interferes with other apps.
- **Preference is remembered**: the on/off state is persisted, and after restart
  plus playback start the caption appears again without user action.
- Hidden automatically when playback stops.

## Implementation

`playback/DesktopCaptionOverlay.kt`:

- Creates (or reuses) a `TextView` and adds it through `WindowManager` with
  `TYPE_APPLICATION_OVERLAY`.
- Position: `gravity = TOP or CENTER_HORIZONTAL`, `y` derived from the style's
  `yPercent` (0 = top, 100 = bottom) over `heightPixels - 200`.
- Style is applied on every `show()` call: text, text colour, background colour,
  text size. This matters because the service may have restarted and because the
  caption can be re-shown after the user changed settings.
- `hide()` removes the view and clears the manager reference.

## Style model

`data/CaptionStyleStore.kt` persists the style and the on/off flag in the
`caption_style` `SharedPreferences` file:

| Key | Meaning | Default |
| --- | --- | --- |
| `y_percent` | vertical position, 5–85 | `12` |
| `text_size` | font size in sp, 12–32 | `16` |
| `text_color` | ARGB text colour | `0xFFFFFFFF` (white) |
| `bg_color` | ARGB background colour | `0xB3000000` (translucent black) |
| `caption_enabled` | caption switched on | `false` |

Values are clamped on both read and write.

## Settings screen

`ui/screens/CaptionSettingsScreen.kt`, reached from **☰ Menu → 字幕设置**:

- live preview card;
- position slider (5–85 %), font-size slider (12–32 sp);
- text colour choices: white / light yellow / light cyan / light orange / light
  green / light pink;
- background choices: translucent black / translucent white / dark green / dark
  blue / dark orange / none.

Every change is saved and then pushed to the service with
`ACTION_UPDATE_CAPTION_STYLE` so an already-visible caption updates live.

The screen follows the app's visual language (green rounded header, card
content) and reads its colours from `MaterialTheme.colorScheme`, so it works in
both light and dark mode.

## Permission handling

Drawing over other apps requires `SYSTEM_ALERT_WINDOW`. When the user enables
the caption without the permission:

1. the service rolls the toggle back and persists `false`;
2. it broadcasts `ACTION_NEED_OVERLAY_PERMISSION`;
3. `MainActivity` receives it and opens
   `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for the package.

The same check happens when the caption is enabled from within the app.

## Pitfalls encountered (keep in mind when touching this code)

- **An all-transparent background looks like "the caption is broken"**: white
  text on a white desktop is invisible. The default is a translucent black
  capsule.
- **A restarted service used to reset the style to defaults**: the service now
  loads the stored style in `onCreate()` *and* on every `show()`.
- **Toggling the caption only updated the notification**: the caption is
  rendered by the service, so it must be shown/hidden from the service, and the
  in-app toggle must notify the service (via `startService` with
  `ACTION_TOGGLE_CAPTION`).
