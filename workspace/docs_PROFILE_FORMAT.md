# ShareParser profile format v1

A portable profile is a JSON object with `schemaVersion: 1` and a `profile` payload.

Important concepts:

- `matchers`: regex rules used to decide whether a shared input is relevant.
- `extractors`: regex rules producing named values.
- `actions`: one or more user-facing processing choices.
- templates: plain text with variables such as `{{value}}` and modifiers such as `{{value|url}}`.

Supported action types in schema v1:

- `calendar`
- `url`
- `share`

Supported template modifiers:

- `url`
- `trim`
- `lower`
- `upper`
