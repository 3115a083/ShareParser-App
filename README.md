# ShareParser

<p align="center">
  <img src="app/src/main/res/mipmap-nodpi/app_logo_1.webp" width="160" alt="ShareParser logo">
</p>

ShareParser is a privacy-focused Android app for parsing and transforming text, emails and text files shared from other apps.

A reusable profile can recognize the incoming content, extract named variables and turn them into calendar events, URLs, rebuilt messages or generated text files. The normal workflow is visual. Regex remains available for advanced users, but is not required for common profiles.

## Main use cases

- Share an email from FairEmail and create a prefilled calendar event.
- Extract a booking ID and open a website with the ID as a URL parameter.
- Rebuild a message from selected fields and forward it to another app.
- Parse TXT, Markdown, HTML, JSON, XML and similar text files.
- Transform text and share, open or save the result again as a text file.
- Reuse and exchange profiles as JSON files.

## Profiles

A normal app launch opens the profile overview. Profiles can be created, enabled or disabled, edited, deleted, imported and exported.

Each profile contains three main parts:

1. **Recognition criteria** decide whether the profile fits the shared content.
2. **Variables** extract and transform reusable values.
3. **Actions** decide what ShareParser should do with the result.

When multiple profiles match, ShareParser asks which one to use. If exactly one profile with exactly one action matches, that action can run directly.

### Editing mode

Opening a profile for editing activates editing mode. While that editor is open, new emails, messages or text files shared to ShareParser are loaded directly into the profile as a fresh example.

This is useful when a rule worked for one email but needs to be refined for a slightly different example. The profile does not need to be recreated.

## Visual extraction without Regex

With a shared example, the user can select a value directly in the subject or message body and create a named variable from that selection. ShareParser builds a reusable extraction rule from the surrounding text.

The editor also suggests common structures such as:

```text
Datum: 14.12.2026
Ort: Dortmund
Buchungsnummer: ICE-612
```

Already configured variables are highlighted in the example text with different colors. Example values can be copied by tapping them or with the copy button.

### Built-in variables

These values are available without creating an extractor:

| Variable | Meaning |
| --- | --- |
| `{{input}}` | subject and text combined |
| `{{subject}}` | shared subject |
| `{{text}}` | message or file content |
| `{{source_app}}` | display name of the sharing app, when Android provides it |
| `{{source_package}}` | package name of the sharing app, when Android provides it |
| `{{file_name}}` | name of a shared file |
| `{{mime_type}}` | MIME type of the shared content |

The sharing application can be used as an additional profile criterion. ShareParser checks Android referrer information, calling activity/package information, common share extras and the authority of a shared content URI. Android does not guarantee that the originating app is disclosed, so this criterion is useful for matching but must not be treated as a security boundary.

## Profile recognition

Profiles can use several criteria at once. All selected criteria must match.

Examples:

- stable text such as `Ihre Terminbestätigung`
- a selected fragment from the shared example
- an extracted variable that must exist
- an extracted variable whose value must match another rule
- sharing app/package, for example FairEmail
- file name or MIME type

Multiple stable fragments can be selected to reduce false matches.

## Parsing direction

Each profile can parse from **top to bottom** or **bottom to top**.

Bottom-to-top mode is useful for replies or forwarded email chains where the original message is quoted below the newest reply. If a field such as `Datum:` occurs several times, ShareParser can use the last match instead of the first.

## Variable transformations

Extracted variables can be transformed in sequence with building blocks:

- trim whitespace
- remove or replace literal text
- advanced regex replacement
- add a prefix
- add a suffix
- convert to lower case
- convert to upper case

Literal replacement treats characters such as `(`, `)`, `[`, `]`, `.` and `*` as normal text unless advanced Regex mode is explicitly enabled.

### Deriving variables from variables

A variable can also use another variable as its source.

For example, start with:

```text
PLZ_ort = 59000 Lünen
```

Then split it into:

```text
PLZ = 59000
Ort = Lünen
```

The editor provides an **Aufteilen** action for this common case. Derived variables can themselves be used in profile criteria, templates and later transformations.

## Templates

Action fields can combine fixed text with variables:

```text
Termin in {{Ort}}
```

```text
https://example.com/booking?id={{booking|url}}
```

Variables written manually as `{{name}}` are recognized the same way as variables inserted through the UI. Variable chips insert at the current cursor position.

Supported modifiers include:

- `{{name|url}}` for URL encoding
- `{{name|trim}}`
- `{{name|lower}}`
- `{{name|upper}}`

## Calendar actions

Calendar actions support:

- title
- description
- location
- start
- end
- duration
- all-day events
- multiple recognized dates
- target calendar selection

### Flexible dates and times

The date/time locale defaults to the Android system setting and can be changed under **Settings → Datum und Uhrzeit**.

Supported regional presets include Germany, United States, United Kingdom, ISO/international and system default.

German examples include:

```text
14.12.2026
14/12/26
14.12.
morgen
übermorgen
nächsten Montag
12-14
12 Uhr bis 14 Uhr
12:00 Uhr bis 14:00 Uhr
```

Duration examples include:

```text
1.5 Stunden
1,5h
2h
90 Minuten
eine Stunde
```

Multiple dates such as these can be interpreted as repeated occurrences:

```text
16.10., 27.11. & 11.12.26
11.2.26 und 2.3.2027
```

If ShareParser cannot interpret a value with sufficient confidence, it warns the user instead of silently inventing data.

### Target calendar

Two modes exist:

- **Calendar app editor:** opens the installed calendar app with prefilled fields. Android calendar apps may ignore the suggested calendar ID.
- **Binding target calendar:** writes the event directly to the selected calendar and then opens the saved event for editing. This mode requires calendar write permission and guarantees the selected local calendar ID.

## URL actions

URL actions can open the generated URL in:

- the standard browser or matching Android app
- ShareParser's restricted in-app WebView

Supported external schemes are `http`, `https`, `geo`, `mailto` and `tel`.

The WebView accepts only HTTP(S). JavaScript, DOM storage, file/content access, geolocation, mixed content and third-party cookies are disabled.

## Rebuilt text and text files

A text action can generate a new subject and body from variables and fixed text.

The result can be:

- shared as normal Android text
- generated as a file and shared through the Sharesheet
- generated as a file and opened directly in another app
- saved to the Android file system

Supported output MIME types include plain text, Markdown and HTML, for example:

```text
text/plain
text/markdown
text/html
```

File names and subfolders can contain variables:

```text
{{datum}}-{{Ort}}.md
```

```text
Termine/{{jahr}}
```

A default destination folder can be selected in Settings. ShareParser uses Android's Storage Access Framework and keeps the persisted folder permission. No broad storage permission is required. If the preset destination is unavailable, Android's normal save dialog is shown.

## Shared text files

ShareParser accepts Android `ACTION_SEND` content for text MIME types and common textual file formats, including:

- `.txt`
- `.md` / `.markdown`
- `.html` / `.htm` / `.xhtml`
- `.json`
- `.xml`
- `.csv` / `.tsv`
- `.log`
- `.ics`
- `.yaml` / `.yml`

Shared files are read through their Android content URI. Binary files are not intentionally parsed as text.

## Action selection

When more than one profile/action is possible, the user can choose how the selector appears:

- inside ShareParser
- as a centered overlay over the sharing app
- as an actionable Android notification

Overlay permission is optional. The overlay is centered, dims the background and automatically disappears after at most one minute.

The notification mode has its own notification channel so Android can control sound, vibration or silent behavior independently.

## App icons

ShareParser includes four selectable launcher icons under Settings. **Logo 1 is the default** and is also explicitly assigned to the Android Sharesheet receiver.

The compact `ic_launcher_foreground.webp` graphic is used next to the ShareParser title inside the app and in the overlay.

## Failure handling

Processing failures create:

- a longer Toast message
- an optional local notification
- a local diagnostic report

The report can reopen the affected profile and highlight the relevant field. Crash diagnostics remain on the device and are not uploaded automatically.

## Privacy and energy use

ShareParser has no analytics, ads, trackers, Play Services dependency or periodic background synchronization.

Processing happens only when the user opens ShareParser or explicitly shares content to it. Profiles, settings and failure reports are stored locally.

Permissions are feature-specific:

- `INTERNET` is used only for the optional in-app WebView.
- `READ_CALENDAR` is requested when calendar discovery is needed.
- `WRITE_CALENDAR` is requested only for binding target-calendar mode.
- `SYSTEM_ALERT_WINDOW` is optional and only needed for overlay selection.
- `POST_NOTIFICATIONS` is optional for notifications.

Android app backup is disabled. Broad storage permissions are not requested.

See [SECURITY.md](SECURITY.md) for repository and runtime security details.

## Architecture

```text
Android Sharesheet / text file
            |
            v
SharedPayload
 subject, text, source app, file metadata
            |
            v
Profile recognition
            |
            v
Extractors -> derived variables -> transformations
            |
            v
Action selection
   |            |              |
Calendar       URL       Text / text file
```

## Example: FairEmail to calendar

Given an email such as:

```text
Terminbestätigung
Datum: 14.12.2026
Uhrzeit: 12 Uhr bis 14 Uhr
Straße Hausnummer  Teststraße 151
PLZ Ort: 59000 Lünen
```

A profile can:

1. require the stable text `Terminbestätigung`
2. optionally require `source_package` to match FairEmail
3. extract `datum`
4. extract `zeit`
5. extract `adresse`
6. extract `PLZ_ort`
7. split `PLZ_ort` into `PLZ` and `Ort`
8. build a calendar location from `{{adresse}}, {{PLZ}} {{Ort}}`
9. open or save the event in the selected calendar

## Example: booking URL

Input:

```text
Buchungsnummer: ICE-612
```

Extract `booking = ICE-612` and use:

```text
https://example.com/manage?booking={{booking|url}}
```

## Example: generate Markdown

A profile can create:

```markdown
# {{subject}}

Termin: {{datum}}
Ort: {{Ort}}
Adresse: {{adresse}}
```

and save it as:

```text
{{datum}}-{{Ort}}.md
```

## Profile sharing

Profiles use a versioned JSON format. They can be copied, exported, shared and imported on another device. Unknown future fields are ignored where possible for forward compatibility.

## Android support

- minimum Android 8.0, API 26
- target/compile SDK 36
- Kotlin
- Jetpack Compose
- Material 3
- dynamic colors on Android 12+

## Build

Requirements:

- JDK 17
- Android SDK 36
- Gradle 9.5.1

The repository intentionally does not vendor `gradle-wrapper.jar`. Install/use Gradle 9.5.1 directly:

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

GitHub Actions runs repository security checks, unit tests, Android Lint and builds the debug APK for pull requests and pushes to `main`.

## F-Droid direction

The project is designed to remain suitable for F-Droid:

- Apache-2.0 license
- no proprietary SDK
- no telemetry
- no remote profile service
- local and inspectable profile format
- Fastlane-compatible metadata

Before an initial F-Droid submission, release signing, reproducible release verification, screenshots, changelogs, tags and final store metadata still need to be completed.

## License

Apache-2.0
