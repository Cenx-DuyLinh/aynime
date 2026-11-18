# Aynime (v0.2)

A small Android browser for watching anime on the Ayn Thor's top screen. It finds Japanese subtitles for the
episode you are watching and shows them in a box over the video. The real PlayTranslate app on the bottom screen
reads that box (auto mode + capture region) for translation, word lookup and Anki.

## Install on the Thor

1. Copy `Aynime-0.2.apk` to the Thor and open it (allow installing from your file manager / browser).
2. If Play Protect complains, choose "Install anyway" (or turn scanning off briefly, as for PlayTranslate).

## Use

- Top bar: back, home, address/search bar, blocked counter, ☆ save site, ⋮ menu.
- Type an anime name: search page with anime results (AniList), whether Japanese subtitles exist, buttons to
  open the anime on your saved sites, and "Search Google".
- Type an address (e.g. `example.com`): opens it directly. Tap ☆ to keep the site on the home page.
- On an episode page the app guesses the anime and episode from the page title and loads matching Japanese
  subtitles from the kitsunekko mirror. If it guesses wrong, tap **Subtitles…** and pick the anime / file.
- `−0.5 s` / `+0.5 s`: move the subtitles earlier / later.
- ⋮ → **Subtitle box style…**: darkness, text size, distance from bottom, width (with a live preview).
- ⋮ → **Subtitle file on device…**: load your own .srt / .ass / .vtt.
- ⋮ → **Update ad block list** (downloads a large block list) and **Update subtitle catalog**.

### Thor buttons (while a video with subtitles is playing)

| Button | Action |
|---|---|
| L1 | previous line |
| R1 | next line |
| X | replay current line |
| Y | hide / show the subtitle box |

### PlayTranslate setup

Open PlayTranslate on the bottom screen, set its capture region on the subtitle box on the top screen, and turn
on auto mode.

## Not in v0.1

- Automatic sync from the audio (planned for v0.2). Use the −/+ buttons for now.
- Zip files from the subtitle mirror, and Jimaku.
- Videos inside Shadow DOM players or DRM sites may not report their time.

## Build

Needs JDK 17 and the Android SDK (platform 35, build-tools 35). `keystore.properties` and the `.jks` file are
local only (not shared). Then:

```
gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Build from source

- Android Studio / JDK 17 and the Android SDK. Create `local.properties` with `sdk.dir=...` (not in the repo).
- `gradlew assembleRelease` builds the APK in `app/build/outputs/apk/release/`.
- Signing: the release key (`anime_thor_release.jks` + `keystore.properties`) is NOT in the repo. Copy both
  privately into the project root, otherwise the build is unsigned and cannot update the installed app.
