# ShareParser

ShareParser is a privacy-first Android utility for turning shared text into useful actions.

It receives text from Android's Sharesheet, selects a reusable profile, extracts structured values and transforms the result into a calendar event, parameterized URL or rebuilt message for another app.

## Current MVP

The repository contains the first functional profile-builder milestone.

### Share flow

- receives `ACTION_SEND` for `text/*` and JSON
- keeps mail subject and body separate as `{{subject}}` and `{{text}}`
- combines both as `{{input}}`
- matches enabled profiles using regular expressions
- shows every shared field before processing
- lets the user choose among matching profiles
- automatically opens an action picker when a profile has multiple processing actions
- supports creating a new profile directly from an unmatched shared example
- handles new shares while the activity is already open (`singleTop` / `onNewIntent`)

### Profile builder

- profile name and enable/disable switch
- optional profile recognition regex
- any number of named extraction fields
- extraction source can be subject, body, or both
- regex capture-group extraction
- required/optional fields
- live extraction preview when editing from a shared example
- transformation blocks: trim, regex replace/remove, prefix, suffix, upper/lower case
- any number of processing actions
- friendly action names
- selectable Material icons
- direct JSON editor for advanced users
- profile JSON can be copied, shared or saved as a file
- profile JSON can be imported through Android's document picker

### Processing actions

**Calendar** uses Android's calendar insert intent. No calendar permission is required. Title, description, location, start and end can be templated. Common German and ISO date formats are recognized automatically, or an explicit `DateTimeFormatter` pattern can be configured.

**URL** builds a URL from variables and opens it through Android. Allowed schemes are `http`, `https`, `geo`, `mailto` and `tel`.

**Share** builds a new subject/body and sends it through the Android Sharesheet to another app.

Template examples:

```text
{{subject}}
{{booking|trim}}
https://example.com/?id={{booking|url}}
Termin: {{customer}}
{{text}}
```

### Failure handling

Processing errors create an immediate Toast plus a silent low-priority notification. The notification expires after 20 seconds and opens a local diagnostic report. From that report the affected profile can be opened with the failing field highlighted.

## Architecture

```text
Android Sharesheet
      |
      v
SharedPayload(subject, text)
      |
Profile matcher
      |
Extractor rules -> transformation blocks -> named values
      |
Action picker
      |-------------------|------------------|
Calendar insert       URL intent       transformed Share
```

Profiles and failure reports are stored locally as JSON. No server is required.

## Privacy and energy use

ShareParser has no `INTERNET` permission, analytics, ads, trackers, Play Services dependency, background service or periodic jobs. Processing only runs when the user opens ShareParser or explicitly shares content to it. Android backup is disabled so locally derived profile and failure data is not uploaded through app backup.

## Android support

- minimum: Android 8.0 / API 26
- target/compile SDK: 36
- Jetpack Compose + Material 3
- dynamic color on Android 12+

## Build

Requirements:

- JDK 21
- Android SDK 36
- Gradle 8.13

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

GitHub Actions runs unit tests, lint and creates a debug APK for pushes and pull requests.

## F-Droid direction

The project is deliberately structured for F-Droid:

- Apache-2.0 source license
- no proprietary SDKs
- no telemetry
- no remote runtime dependency
- plain, inspectable profile files
- Fastlane-compatible store metadata under `fastlane/metadata/android`

Before the first F-Droid submission, release signing, reproducible release builds, screenshots, changelogs, tagged releases and final metadata still need to be completed.

## Provisional icon

The adaptive launcher icon combines Android's share-node motif with a processing gear to represent “share → parse/transform”. It is intentionally provisional and can be replaced without changing app identity.

## License

Apache-2.0
