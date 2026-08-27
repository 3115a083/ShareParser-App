# ShareParser

Privacy-first Android text transformation utility.

ShareParser receives text from Android's Sharesheet, matches it against reusable profiles, extracts structured values and lets the user hand the transformed result to another Android feature or app.

## Current development preview

The repository contains the first runnable architecture and UI foundation for version `0.1.0`.

Implemented foundation:

- `ACTION_SEND` / `text/*` receiver
- Material 3 / Jetpack Compose UI with dynamic color on Android 12+
- Local profiles, no account and no cloud service
- Profile matchers using regular expressions
- Named extractors with required/optional semantics
- Template variables such as `{{title}}`, `{{customer|url}}`, `{{value|trim}}`
- Processing engine supporting multiple actions per profile
- Calendar hand-off using `CalendarContract.ACTION_INSERT`, without calendar permission
- Parameterized URL hand-off
- Rebuilt text hand-off through Android Sharesheet
- Friendly names and Material icon identifiers for actions
- Versioned JSON profile import/export model
- Failure reports stored locally
- Toast + silent low-priority notification on processing failure
- Failure notification expires after 20 seconds and opens diagnostics
- No `INTERNET` permission, analytics, ads, trackers or background service
- Adaptive provisional share + gear launcher icon

The guided editor in this first checkpoint intentionally exposes a small subset of the model. The data model and execution engine already support multiple extractors and Calendar, URL and Share actions. The next UI milestone is the visual block editor for adding/reordering all of these without editing JSON.

## Architecture

```text
Android Sharesheet
      |
      v
Profile matcher -> Extractors -> named values -> selected ProcessingAction
                                            |-> Calendar intent
                                            |-> URL intent
                                            `-> Share intent
```

Profiles are serialized as versioned JSON and can therefore be shared, downloaded, imported and reviewed as plain text.

## Build

Requirements:

- JDK 21
- Android SDK 36
- Gradle 8.13

```bash
gradle :app:assembleDebug
```

Android Studio can import the repository directly and use Gradle 8.13.

## F-Droid direction

The app is deliberately designed for F-Droid compatibility:

- Apache-2.0 licensed source
- no proprietary SDKs
- no Play Services dependency
- no telemetry
- no remote build-time code generation service
- source-only profile format

Before an F-Droid submission, versioned release tags, changelogs, reproducible release builds and final store metadata still need to be completed.

## Privacy

Shared text and profiles remain on-device. ShareParser itself does not request network access. Data only leaves ShareParser when the user explicitly launches a URL, calendar insertion or shares transformed text to another app.

## License

Apache-2.0
