# ShareParser

<p align="center">
  <img src="app/src/main/res/mipmap-nodpi/app_logo_1.png" width="160" alt="ShareParser logo">
</p>

ShareParser is a privacy-focused Android app for parsing and transforming text, emails and text files shared from other apps.

A reusable profile recognizes incoming content, extracts named variables and turns the result into calendar events, URLs, rebuilt messages or generated text files. The normal workflow is visual. Regex remains available for advanced users, but is not required for common profiles.

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

Opening a profile for editing activates editing mode. While that editor is open, newly shared emails, messages or text files are loaded directly into the profile as a fresh example. This is useful for refining a rule against several real examples without recreating the profile.

Editor changes remain local until **Save profile** is used. The editor provides an undo button for recent changes. If the user leaves the editor with unsaved changes, ShareParser asks whether to apply them, discard them or continue editing. Processing-action cards are collapsed by default so large profiles remain manageable.

## Visual extraction without Regex

With a shared example, the user can select a value directly in the subject or message body and create a named variable from that selection. ShareParser builds a reusable extraction rule from the surrounding text.

The editor also suggests common structures such as:

```text
Datum: 14.12.2026
Ort: Dortmund
Buchungsnummer: ICE-612
Straße Hausnummer  Teststraße 151
```

Configured variables are highlighted in the example text with different colors. Example values can be copied for later transformations.

### Built-in variables

| Variable | Meaning |
| --- | --- |
| `{{input}}` | subject and text combined |
| `{{subject}}` | shared subject |
| `{{text}}` | message or file content |
| `{{source_app}}` | display name of the sharing app, when Android provides it |
| `{{source_package}}` | package name of the sharing app, when Android provides it |
| `{{file_name}}` | name of a shared file |
| `{{mime_type}}` | MIME type of the shared content |

The sharing application can be used as an additional profile criterion. ShareParser checks Android referrer information, calling activity/package information, common share extras and the authority of a shared content URI. Android does not guarantee that the originating app is disclosed, so this is a matching hint, not a security boundary.

## Profile recognition

Profiles can use several criteria at once. From the second criterion onward, each criterion can be connected to the previous result with **AND** or **OR**. This allows profiles to require several facts while still accepting alternative signatures.

Useful criteria include stable text fragments, sharing app/package, file name, MIME type and extracted variables. Variable criteria are kept collapsed in the editor until needed. One or more variables can be selected together and checked for:

- empty
- not empty
- content matching a guided rule

The guided rule builder covers common cases such as contains, starts with, ends with, exact text, digits only and a chosen number of digits. Advanced users can still enter a custom regular expression, which is validated before it is stored.

## Parsing direction

Each profile can parse from **top to bottom** or **bottom to top**. Bottom-to-top mode is useful for replies and forwarded email chains where the original message appears below the newest reply. If a field occurs several times, ShareParser can use the last match instead of the first.

## Variable transformations

Extracted variables can be transformed in sequence with building blocks:

- trim whitespace
- remove or replace literal text
- advanced regex replacement
- add a prefix or suffix
- convert to lower or upper case
- derive another variable from an existing variable

Literal replacement treats characters such as `(`, `)`, `[`, `]`, `.` and `*` as normal text unless advanced Regex mode is explicitly enabled.

### Deriving variables from variables

For example:

```text
PLZ_ort = 59000 Lünen
```

can be split into:

```text
PLZ = 59000
Ort = Lünen
```

Derived variables can themselves be used in profile criteria, templates and later transformations.

## Templates

Action fields combine fixed text with variables:

```text
Termin in {{Ort}}
```

```text
https://example.com/booking?id={{booking|url}}
```

Variables written manually as `{{name}}` are recognized the same way as variables inserted through the UI. Variable chips insert at the current cursor position.

Supported modifiers include `url`, `trim`, `lower` and `upper`.

## Calendar actions

Calendar actions support title, description, location, start, end, duration, all-day events, multiple recognized dates and target calendar selection.

The date/time locale defaults to the Android system setting and can be changed under **Settings → Datum und Uhrzeit**. Presets include Germany, United States, United Kingdom and ISO/international.

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

Multiple dates such as `16.10., 27.11. & 11.12.26` can be interpreted as repeated occurrences. If ShareParser cannot interpret a value confidently, it warns the user instead of silently inventing data.

### Target calendar

Two modes exist:

- **Calendar app editor:** opens the installed calendar app with prefilled fields. Android calendar apps may ignore the suggested calendar ID.
- **Binding target calendar:** writes the event directly to the selected calendar and opens the saved event for editing. This mode requires calendar write permission and guarantees the selected local calendar ID.

## URL actions

URL actions can open generated URLs in the standard browser/matching Android app or ShareParser's restricted in-app WebView. Supported external schemes are `http`, `https`, `geo`, `mailto` and `tel`.

The WebView accepts only HTTP(S). JavaScript, DOM storage, file/content access, geolocation, mixed content and third-party cookies are disabled.


## Webhook actions

A profile can send an HTTP POST webhook. URL and body fields support the same variables as other actions.

A webhook can either appear as a normal selectable action or fire automatically whenever its profile matches. Automatic webhooks are excluded from the action picker so they do not create duplicate choices. Empty webhook bodies can use a fallback body or stop with an error.

## Rebuilt text and text files

A text action can generate a new subject and body from variables and fixed text. The result can be shared as normal Android text, generated as a file and shared, opened directly in another app, or saved through Android's file system.

For file output, the user enters the file extension directly, for example `txt`, `md`, `html`, `json` or `xml`. ShareParser chooses the corresponding text MIME type automatically. Unknown extensions are allowed. ShareParser warns the user and continues with text content while keeping the requested extension, so an extension such as `.pdf` does not turn the generated text into a real PDF.

File names and subfolders can contain variables:

```text
{{datum}}-{{Ort}}
Termine/{{jahr}}
```

The selected extension is appended to the generated file name. Empty variables or invalid file-system characters can either use configured fallback values or stop the action with a visible error.

A default destination folder can be selected in Settings. ShareParser uses Android's Storage Access Framework and needs no broad storage permission. If the preset destination is unavailable, Android's normal save dialog is shown.

## Shared text files

ShareParser accepts Android `ACTION_SEND` content for text MIME types and common textual formats such as `.txt`, `.md`, `.html`, `.json`, `.xml`, `.csv`, `.tsv`, `.log`, `.ics`, `.yaml` and `.yml`. Shared files are read through their Android content URI. Binary files are not intentionally parsed as text.

## Action selection

When more than one profile/action is possible, the selector can appear inside ShareParser, as a centered overlay over the sharing app, or as an actionable Android notification. Overlay permission is optional. The notification mode has its own Android channel for sound/vibration settings. Temporary selectors expire automatically.

## App icons

ShareParser includes four selectable launcher icons. **Logo 1 is the default** and is explicitly assigned to the Android Sharesheet receiver. Launcher aliases are switched explicitly and synchronously to improve launcher refresh reliability.

The settings preview uses a robust bitmap decoder for the supplied PNG artwork. The compact ShareParser graphic is rendered without its dark source background and is displayed at a larger size next to the title and in the share overlay.

## Failure handling

Processing failures create a longer Toast, an optional local notification and a local diagnostic report. The report can reopen the affected profile and highlight the relevant field. Crash diagnostics remain on the device and are not uploaded automatically.

## Privacy and energy use

ShareParser has no analytics, ads, trackers, Play Services dependency or periodic background synchronization. Processing happens only when the user opens ShareParser or explicitly shares content to it. Profiles, settings and failure reports are stored locally.

Permissions are feature-specific:

- `INTERNET` for the optional in-app WebView
- `READ_CALENDAR` for calendar discovery
- `WRITE_CALENDAR` only for binding target-calendar mode
- `SYSTEM_ALERT_WINDOW` only for optional overlay selection
- `POST_NOTIFICATIONS` only for notifications

Android app backup is disabled. Broad storage permissions are not requested. See [SECURITY.md](SECURITY.md) for repository and runtime security details.

## Example: FairEmail to calendar

Given:

```text
Terminbestätigung
Datum: 14.12.2026
Uhrzeit: 12 Uhr bis 14 Uhr
Straße Hausnummer  Teststraße 151
PLZ Ort: 59000 Lünen
```

A profile can require stable text, optionally filter by FairEmail, extract date/time/address, split `PLZ_ort` into `PLZ` and `Ort`, build `{{adresse}}, {{PLZ}} {{Ort}}`, and open or save the event in the selected calendar.

## Example: booking URL

```text
Buchungsnummer: ICE-612
```

Extract `booking = ICE-612` and use:

```text
https://example.com/manage?booking={{booking|url}}
```

## Example: generate Markdown

```markdown
# {{subject}}

Termin: {{datum}}
Ort: {{Ort}}
Adresse: {{adresse}}
```

Save as `{{datum}}-{{Ort}}.md`.

## Profile sharing

Profiles use a versioned JSON format. They can be copied, exported, shared and imported on another device. Unknown future fields are ignored where possible for forward compatibility.

## Android support and build

- minimum Android 8.0, API 26
- target/compile SDK 36
- Kotlin, Jetpack Compose, Material 3
- JDK 17
- Gradle 9.5.1

The repository intentionally does not vendor `gradle-wrapper.jar`. Use Gradle 9.5.1 directly:

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

GitHub Actions runs security checks, icon-format checks, unit tests, Android Lint and a debug APK build for pull requests and pushes to `main`.

## F-Droid direction

The project is designed to remain suitable for F-Droid: Apache-2.0 license, no proprietary SDK, no telemetry, no remote profile service, local inspectable profile files and Fastlane-compatible metadata. Release signing, reproducible-release verification, screenshots, changelogs, tags and final store metadata remain before an initial F-Droid submission.

## License

Apache-2.0
