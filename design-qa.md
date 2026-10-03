# Adaptive Android app design QA

final result: passed

## Reference and rendering evidence

- Selected visual target: option 3, `preview/3.png` (1487 × 1058).
- Final implementation: `app/build/outputs/design/wide.png`, also copied to
  `preview/app-wide.png` (1487 × 1058 at mdpi, 1 pixel per dp).
- These are native Android Compose views drawn with Robolectric native Skia,
  not HTML reproductions or emulator screenshots. No CSS viewport applies.
- The reference and final wide render were opened together in the same image
  comparison input. Both show the file screen with the PDF selected and the same
  four Chinese filenames. The fixture service is starting, whereas the concept
  shows a running service; the real running state adds its QR action.
- Additional renders: `preview/app-phone.png` (390 × 844),
  `preview/app-landscape.png` (720 × 480), `preview/app-dark.png` (1280 × 800),
  and `preview/app-large-font.png` (390 × 844, 1.6× font scale).
- All screenshots are local verification artifacts; they are not APK resources.
  Tests regenerate implementation captures under `app/build/outputs/design/`.

## Findings and comparison history

1. [P2, fixed] Initial native layout was too dense relative to the chosen wide
   concept and used an overly bright selected-row background. The first native
   captures are retained locally in `/tmp/feiya-app-before/`. Increased spacious
   sidebar width, file row typography and glyph sizing, and the detail glyph;
   switched selection to the softer secondary container and used rounded
   rectangular detail actions. Final wide capture was compared again against
   the reference at matching dimensions.
2. [P2, fixed] At 1.6× text scale, bottom navigation labels were clipped. Evidence:
   `/tmp/feiya-app-large-font-before.png`. Large-text compact navigation now uses
   icons with accessible destination labels. The final large-font capture shows
   every navigation control unobstructed, and file removal remains reachable by
   scrolling in both large-text and short-landscape tests.
3. [P1, fixed] Compose cannot load an adaptive launcher resource with
   `painterResource`. Reused the existing raster brand asset for the sidebar;
   all wide/rail renders now complete successfully.

## Fidelity surfaces

- Typography: native Android sans-serif and Chinese fallback, bold page/detail
  hierarchy, larger file labels in spacious windows, readable body copy. Platform
  font metrics differ from the image mock intentionally.
- Layout: persistent sage navigation, a distinct selectable file list, and an
  unboxed detail pane preserve option 3. Compact windows navigate between list
  and detail. Detail content scrolls instead of hiding actions on short windows.
- Colors: existing green primary and warm off-white surface, subdued selection,
  green-gray dark surfaces, and red/blue/ochre file-type glyphs. Dynamic Android
  color remains supported; captures use the deterministic app palette.
- Assets: actual Feiya logo and licensed Phosphor vector paths, not approximated
  mock imagery. Native library icons intentionally replace illustrative Adobe
  and file-art glyphs. Raster logo and vector icons are sharp at tested sizes.
- Copy/data: only actual available name/URI data is shown. Mock sizes, dates, and
  invented download paths are deliberately omitted. Existing per-file viewing
  behavior opens its QR code, so the action explicitly says “File QR code”.
  Local saving and removing a share remain available.
- Focused inspection: the full-resolution comparison made the sidebar, selected
  row, glyph, typography and action area readable; these regions were inspected
  in the same image input without needing a separate cropped artifact.

## Interaction verification and limits

- 74 JVM tests passed, including URI selection across reorder/removal/restore,
  live width changes, compact Back-to-files, large-font and short-height action
  reachability, Markdown draft preservation across pane changes, and original
  Markdown sending. Existing HID immediate-edit/task tests passed.
- QR Host tests cover cancelling stale encoding on address changes, shutdown,
  discovery failures and empty-network retry. Discovery/encoding stays outside
  Compose rendering and is cancelled when its dialog leaves composition.
- Debug APKs built successfully; all five architecture outputs retain the exact
  16 npm-built web resources.
- No physical device/emulator run, soft-keyboard interaction, fold hinge, or real
  Bluetooth end-to-end test was performed. This is a native rendering and
  component-interaction QA result, not an end-to-end runtime certification.

No actionable P0/P1/P2 findings remain in the captured states. A future device
pass should include rotation with the software keyboard open and TalkBack.

---

# Shared browser UI design QA

final result: passed

## Reference and evidence

- Visual target: selected option 2, `/tmp/feiya-design-options/option-2.png`.
- Implementation: `web/test/artifacts/files-desktop.png`.
- Side-by-side comparison: `web/test/artifacts/comparison.png` (reference left,
  implementation right); focused header/first-row comparison:
  `web/test/artifacts/comparison-top.png`.
- Source and implementation: 1487 × 1058 pixels; browser viewport 1487 × 1058 CSS
  pixels, device scale factor 1. Both captures use the same four file names and
  connected state. No density conversion or browser frame was included.
- Additional browser captures: `files-mobile.png`, `files-mobile-long.png`,
  `chat-mobile.png`, `chat-desktop.png`, `login-mobile.png`, `login-desktop.png`
  in `web/test/artifacts/`. Mobile viewport: 390 × 844 CSS pixels at scale 1.
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
- Assets: Phosphor SVGs supplied by the npm build, retained MIT license, no external requests.
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
