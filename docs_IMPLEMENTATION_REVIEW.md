# Confirmation and action-chain implementation review

Base: `feature/shareparser-v034`, commit `b2cd449b1b720818ba3d486018a46a8d0dc7309a`, draft PR #26.

## Differences from the private handover

- The 2026-09-11 handover named `68ee8de` and CI 515. The inspected branch had advanced to `b2cd449`, with baseline CI 527 and 528 successful.
- Both partial chain commits were reverted. The complete model/editor/execution integration is added here.
- Section-title wrapping was described as fixed, but `EditorSectionHeader` still used a weighted title. It now uses a wrapping FlowRow.
- There were four navigation items with an example. The three equal-width sections now include examples under Variables.
- The format document still named schema 2 while the model used schema 13. Current documentation and the model now use schema 14, retaining legacy imports.
- Removing the 12dp inset still left intrinsic pixel-sized centered bitmap rendering. Adaptive foreground now fills its bounds using the original built-in safe area. Colored artwork radius is 31.2dp in a 108dp layer, inside the 33dp safe circle. Both active adaptive and round icons use this foreground.

## Security review

Source-app metadata remains a hint. Confirmation is based on action settings and explicit action selection. Content reads and user regex processing use bounded workers, including when native calls ignore interruption. Timeout errors do not become successful NOT/ELSE or fallback matches. Webhook redirects are rejected, error reads bounded before allocation and CR/LF stripped. LAN destinations remain available. Temporary files get independent execution directories. Existing filename/path traversal checks, controlled MIME mapping, URI grants and NEW_TASK launches remain. No resolveActivity precheck is added.

WebView scheme/host/userinfo checks, redirect validation, renderer recovery and disabled scripts, DOM storage, file/content access, geolocation, mixed content and third-party cookies remain. Browser mode is available for sites requiring JavaScript. No permission, backup, tracking, sync, signing-secret or CI permission changes are introduced.

## Local validation

The full requested Gradle command completed successfully on 2026-09-20: both debug APKs assembled, 61 unit tests passed per flavor (122 total), and both lint tasks passed with zero errors. Lint reports 44 warnings for light and 42 for full. Workflow privacy/security invariants, runtime-safe icon checks, immutable action pins and minimal permissions also passed locally. CI is checked against the pushed commit before APK delivery.

Vendor-specific device behavior and external-app activity restrictions still require testing on the user's device.
