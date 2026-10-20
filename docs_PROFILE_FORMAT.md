# ShareParser profile format

Profiles are plain JSON wrapped in a versioned `ProfileBundle`. Current schema version: **14**. Schemas 1 through 14 are accepted; the older example below remains valid.

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


## Schema 14 additions

All action variants have `confirmBeforeAutoRun: Boolean = true`. Missing fields in old profiles use this safe default; explicit `false` restores automatic behavior. Exports include default fields explicitly.

New discriminator `"chain"` uses the same ID, friendly name, icon, editor description, condition, ELSE reference and visibility fields as other actions.

| Field | Default | Meaning |
| --- | --- | --- |
| `actionIds` | `[]` | Ordered stable action IDs in this profile |
| `mode` | `"SEQUENTIAL"` | `"SEQUENTIAL"` or `"PARALLEL"` |
| `delayMs` | `0` | Sequential delay in milliseconds, 0 to 3600000; ignored in parallel mode |

References are selected visually. Duplicates get a new root ID and retain child references. Duplicate as ELSE links to the source action. Runtime validates the reachable graph before side effects: missing references, cycles, duplicate references/IDs, more than 16 nested chains or 256 expanded steps are rejected. Root confirmation covers children, whose conditions still apply.

Example, assuming `open-link` and `share-text` already exist:

```json
{"type":"chain","id":"follow-up","friendlyName":"Follow up","icon":"playlist_play","confirmBeforeAutoRun":true,"actionIds":["open-link","share-text"],"mode":"SEQUENTIAL","delayMs":1500}
```
