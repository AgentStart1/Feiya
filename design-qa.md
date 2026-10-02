# Shared browser UI design QA

final result: passed

## Reference and evidence

- Visual target: selected option 2, `/tmp/feiya-design-options/option-2.png`.
- Implementation: `tests/web/artifacts/files-desktop.png`.
- Side-by-side comparison: `tests/web/artifacts/comparison.png` (reference left,
  implementation right); focused header/first-row comparison:
  `tests/web/artifacts/comparison-top.png`.
- Source and implementation: 1487 × 1058 pixels; browser viewport 1487 × 1058 CSS
  pixels, device scale factor 1. Both captures use the same four file names and
  connected state. No density conversion or browser frame was included.
- Additional browser captures: `files-mobile.png`, `files-mobile-long.png`,
  `chat-mobile.png`, `chat-desktop.png`, `login-mobile.png`, `login-desktop.png`
  in `tests/web/artifacts/`. Mobile viewport: 390 × 844 CSS pixels at scale 1.
- Captures are local verification artifacts, not bundled application resources.

## Findings and comparison history

1. The first rendered capture used the environment's fallback Chinese font. After
   installing Noto Sans CJK for verification, a same-size combined comparison
   identified a P2 typography/icon-scale mismatch: file labels, navigation, and
   file glyphs appeared smaller than the selected reference. Pre-fix evidence:
   `/tmp/feiya-web-preview/comparison-before.png`.
2. Increased desktop file labels and navigation to 22px, heading to 34px, file
   glyph containers to 56px (including the upstream SVG's intrinsic whitespace),
   and adjusted icon-to-label spacing. Mobile sizes remain independently tuned.
3. Recaptured and inspected the combined full view and focused top region.
   No actionable P0/P1/P2 visual findings remain. Mobile long filenames wrap
   without hiding download actions or causing horizontal overflow.

## Required fidelity surfaces

- Typography: system sans-serif with Noto Sans CJK / Microsoft YaHei fallbacks;
  strong title, quieter column labels, legible body text and clear selected nav.
  Platform-dependent font metrics are an accepted difference.
- Layout: 278px desktop sidebar, 62px top bar, aligned table columns, roughly
  107px rows, restrained dividers and bottom-aligned service status. Mobile uses
  a top navigation row so both destinations remain directly available.
- Tokens: Feiya primary `#206c29`, page `#fcfdf6`, sage sidebar `#e8eedd`, muted
  text `#52634f`. Flat surfaces intentionally omit image-generation texture.
- Assets: vendored Phosphor 2.1.1 SVGs, retained MIT license, no external requests.
  Library file-type glyphs replace the mock's illustrative glyph shapes. They
  remain sharp at both sizes and preserve the reference's type colors.
- Copy: the file page preserves the selected Chinese hierarchy and labels.
  Login and chat extend the same shell; they were not separate selected mocks.
  Loading, empty, retry, disconnected and login-error text explain next actions.

## Interaction verification

Chromium browser checks passed for download, SSE refresh, empty/error/retry,
long filenames, literal untrusted text, message send, copy (secure API and LAN
HTTP compatibility branch), Shift+Enter, IME composition, disconnect/reconnect
without draft loss or replay, password errors, and passwordless entry.
No page JavaScript errors or external asset requests occurred in the passing run.
The browser check uses mock network endpoints. Real Ktor route coverage is a
separate JVM test; an installed Android-device browser flow was not tested.

## Implementation checklist

- [x] Inspect the selected visual target and rendered implementation together.
- [x] Fix typography and glyph scaling; repeat capture and comparison.
- [x] Verify desktop and mobile primary flows and state feedback.
- [x] Preserve local-only static assets and document development commands.

## Follow-up polish

P3: small glyph-shape and platform font differences are accepted. There are no
outstanding functional or visual blockers in the tested browser flow.
