# ShareParser

<p align="center">
  <img src="app/src/main/res/mipmap-nodpi/app_logo_3.png" width="160" alt="ShareParser logo">
</p>

ShareParser is a privacy-focused Android app for parsing and transforming text, emails and text files shared from other apps.

This is a **vibecoded project** that was created primarily for the author's own everyday needs. It is shared with the community because the same workflows may be useful to other people. Contributions, testing and practical feedback are welcome, especially where generated code or Android-specific behavior needs additional review.

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

Editor changes remain local until **Save profile** is used. Undo and redo are available directly in the editor top bar. If the user leaves the editor with unsaved changes, ShareParser asks whether to apply them, discard them or continue editing. Deleting a profile requires confirmation.

The editor has a sticky section navigation row for profile recognition, variables from the example, variables and processing actions. It remains visible while scrolling and indicates the current section. The example section is shown only when an example is available. Processing-action cards are collapsed by default so large profiles remain manageable.

## Visual extraction without Regex

With a shared example, the user can select a value directly in the subject or message body and create a named variable from that selection. ShareParser builds a reusable extraction rule from the surrounding text.

The editor also suggests common structures such as:

```text
Datum: 14.12.2026
Ort: Dortmund
Buchungsnummer: ICE-612
Straße Hausnummer  Teststraße 151
```

Configured variables are highlighted in the example text with different colors. Highlighting is constrained to the extracted value so a broad extraction pattern cannot color the entire sample. Variable cards expose the recognition logic directly and include a guided regex assistant with common building blocks, colored syntax and warnings for invalid, overly broad or overly restrictive patterns. Example values can be copied for later transformations.

The suggestion engine also recognizes common web links, `mailto:` links, email addresses, telephone links and plain telephone numbers as candidate variables, including formatted Android URL spans and HTML link targets when the sharing app provides them. Semantic suggestions use reusable structure-based patterns instead of embedding the exact sample email, phone number or link into the generated rule.

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

Useful criteria include stable text fragments, the sharing app, file name, MIME type and extracted variables. The sharing-app criterion includes an app picker, accepts multiple apps at once and can match either "is one of these apps" or "is not one of these apps". Variable criteria are kept collapsed in the editor until needed. One or more variables can be selected together and checked for:

- empty
- not empty
- content matching a guided rule

The guided rule builder covers common cases such as contains, starts with, ends with, exact text, digits only and a chosen number of digits. Advanced users can still enter a custom regular expression, which is validated before it is stored.

## Parsing direction

Each profile can parse from **top to bottom** or **bottom to top**. Bottom-to-top mode is useful for replies and forwarded email chains where the original message appears below the newest reply. If a field occurs several times, ShareParser can use the last match instead of the first.

## Regex assistance

Variable extraction rules can be edited directly in each variable card. A built-in assistant offers reusable blocks for arbitrary text, digits, letters, mixed IDs, dates, times, email addresses, telephone numbers, addresses with optional house numbers, exclusions and line boundaries. Fixed text can be inserted before or after the variable as an escaped anchor.

The editor color-codes regex operators, character classes, anchors and groups. It also warns about invalid syntax, missing capture groups, overly broad patterns, patterns that look tied to one sample, literal sample values embedded in a rule and potentially greedy exclusions. The goal is tolerant reusable parsing rather than a rule that works only for one copied message.

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

Splitting is modular. A source variable can be divided into any number of subvariables using a chosen separator or whitespace.

Variable names are normalized to lowercase and must be unique inside a profile. Template references are resolved case-insensitively for compatibility with older profiles. If a new or renamed variable conflicts with an existing name, the editor offers to overwrite the existing variable, save the new variable with an incremented suffix, or discard the change. Duplicate names are also rejected by profile validation before saving.

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

Supported modifiers include `url`, `trim`, `lower`, `upper` and `json`. The `json` modifier escapes quotes, control characters and line breaks before a variable is inserted into JSON. The webhook editor uses this safe form for variable chips by default.

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

Automatic background webhooks use longer network timeouts and up to three bounded attempts. Only the final failure creates the user-facing failure notification, which links directly back into ShareParser.

## Rebuilt text and text files

A text action can generate a new subject and body from variables and fixed text. The result can be shared as normal Android text, generated as a file and shared, opened directly in another app, or saved through Android's file system.

For file output, the user enters the file extension directly, for example `txt`, `md`, `html`, `json` or `xml`. ShareParser chooses the corresponding text MIME type automatically. Unknown extensions are allowed. ShareParser warns the user and continues with text content while keeping the requested extension, so an extension such as `.pdf` does not turn the generated text into a real PDF.

File names and subfolders can contain variables. The editor shows a rendered example using the current sample values. File-system-invalid characters from variable content are converted to safe characters before the file is created, while path traversal segments such as `..` remain blocked:

```text
{{datum}}-{{Ort}}
Termine/{{jahr}}
```

The selected extension is appended to the generated file name. Empty variables or invalid file-system characters can either use configured fallback values or stop the action with a visible error.

A default destination folder can be selected in Settings. ShareParser uses Android's Storage Access Framework and needs no broad storage permission. If the preset destination is unavailable, Android's normal save dialog is shown.

## Shared text files

ShareParser accepts Android `ACTION_SEND` content for text MIME types and common textual formats such as `.txt`, `.md`, `.html`, `.json`, `.xml`, `.csv`, `.tsv`, `.log`, `.ics`, `.yaml` and `.yml`. Shared files are read through their Android content URI. Binary files are not intentionally parsed as text.

## Conditional processing

Processing actions can optionally have conditions. A condition can test whether a variable is empty, not empty or matches a text/regular-expression rule. Several tests can be combined with **AND**, **OR** and **NOT**.

A conditioned action is shown only when its condition is true. The editor can also create an **ELSE** branch from an existing conditioned action. The else action is a normal editable action with its own URL, calendar, share or webhook configuration and appears only when the parent condition is false.

Conditions are configured inside each processing action. IF/ELSE branches stay attached to the affected action, support AND/OR and NOT for content checks, and can be distinguished in the editor with an optional short editor-only description.

## Action selection

When more than one profile/action is possible, the selector can appear inside ShareParser, as a centered overlay over the sharing app, or as an actionable Android notification. Processing actions can be reordered in the profile editor, and that order is used by the selectors.

Each action can be included or excluded from the overlay and notification surfaces. The overlay shows at most four configured actions and offers to open the full list in ShareParser when more are available. Android notifications expose at most three configured action buttons and open the full picker in the app for the remaining choices. Overlay permission is optional. The notification mode has its own Android channel for sound/vibration settings. Temporary selectors expire automatically.

## Optional built-in share actions

Settings can enable additional share actions individually. They are disabled by default and appear only when ShareParser detects suitable content. In the in-app picker they are grouped in a compact submenu. Available options include opening a detected address in a maps app, opening a detected web link, phone number or email target, and recreating a shared text file with its detected extension for direct opening in a matching app.

These actions use the same Android selector surfaces as profile actions and do not require creating a profile first.

## App icon

ShareParser uses **Logo 3 exclusively** for the Android launcher, package icon and Sharesheet receiver. The adaptive foreground keeps a generous safe area so the full logo remains visible under circle, squircle and rounded-square launcher masks. The manifest references the supplied Logo 3 artwork directly so the package installer and share surfaces do not fall back to Android's generic application icon. The previous runtime launcher-icon switcher was removed because launcher caching and alias switching were not reliable enough across tested Android launchers. Logo 3 is also wired through the canonical Android launcher icon resources used by the application manifest.

The compact ShareParser graphic used inside the app and share overlay is rendered without a dark background and at a larger size than earlier builds.

## Failure handling

Processing failures create a longer Toast, an optional local notification and a local diagnostic report. The report can reopen the affected profile and highlight the relevant field. Crash diagnostics remain on the device and are not uploaded automatically.

## Input and output safety

ShareParser does not execute variable content as code. Generated external URLs are limited to supported Android schemes, the in-app WebView accepts only HTTP(S), webhook header values are stripped of line breaks, JSON templates support explicit escaping, and file names/path segments are sanitized before use. File-system traversal segments remain rejected instead of being silently interpreted.

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

Profiles use a versioned JSON format. The current profile schema is version 12. Profiles can be copied, exported, shared and imported on another device. Unknown future fields are ignored where possible for forward compatibility.

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
