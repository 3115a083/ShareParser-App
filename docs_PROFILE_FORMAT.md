# ShareParser profile format

Profiles are plain JSON wrapped in a versioned `ProfileBundle`. Current schema version: **2**.

Built-in values available to every action:

- `{{subject}}` — Android share subject, especially useful with mail apps such as FairEmail
- `{{text}}` — shared body text
- `{{input}}` — subject and body combined

Extractors add arbitrary named variables. An extractor chooses `COMBINED`, `TEXT` or `SUBJECT`, applies a regular expression, selects a capture group and can then run transformation blocks in order.

Supported transformation blocks:

- trim
- regex replace / remove
- prefix
- suffix
- lower / upper case

Templates can use `{{name}}` and modifiers such as `{{name|url}}`, `{{name|trim}}`, `{{name|lower}}` and `{{name|upper}}`.

## Example

```json
{
  "schemaVersion": 2,
  "profile": {
    "id": "example",
    "name": "FairEmail booking",
    "enabled": true,
    "matchers": [
      { "regex": "Buchung|Booking", "ignoreCase": true }
    ],
    "extractors": [
      {
        "key": "booking",
        "regex": "Buchungsnummer[:\\s]+([A-Z0-9-]+)",
        "group": 1,
        "required": true,
        "source": "COMBINED",
        "transforms": [{ "type": "trim" }]
      }
    ],
    "actions": [
      {
        "type": "url",
        "id": "open-booking",
        "friendlyName": "Buchung öffnen",
        "icon": "link",
        "urlTemplate": "https://example.com/booking?id={{booking|url}}"
      }
    ]
  }
}
```

Profiles are intentionally human-readable and contain no executable code. The advanced mode edits this JSON directly.
