# Feiya

[![Test](https://github.com/storytellerF/Feiya/actions/workflows/test.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/test.yml)
[![Build](https://github.com/storytellerF/Feiya/actions/workflows/release.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/release.yml)

## Overview

Requires Android 13 (API 33) or later.

## Bluetooth keyboard

Pair and connect the target computer from the HID screen. Select the keyboard layout
currently enabled on that computer: US QWERTY (default), Dvorak, or Colemak. Turn off
Caps Lock and use the corresponding input source on the computer before sending text.
The selection applies to all text sent by Feiya, including file-sharing URLs, for the
current activity session.

Text supports printable ASCII, space, Tab, Enter, Backspace, Escape, and Delete.
Windows CRLF line endings send one Enter. If a text contains unsupported characters
(such as Chinese or emoji), Feiya reports an error without sending any of that text.
Connection or permission failures stop the current message; check the connection
before retrying. Text already sent before a connection failure cannot be undone.

The HID screen shows queued, sending, sent, failed, and cancelled tasks, with the
target computer and completed-key count. Cancel one task or all pending work;
cancellation does not undo keys already sent. The latest ten completed tasks remain
visible, including after disconnecting. “Sent” means the Bluetooth API accepted the
press/release reports, not that the computer confirmed text entry.

Each task stays bound to the connection present when submitted. Disconnecting,
switching computers, or reconnecting to the same computer stops old tasks instead
of forwarding their remaining keys to the new connection. Check the destination
before submitting again. Editing the draft while sending does not change queued text.

For macOS Keyboard Setup Assistant, use the calibration controls available whenever
a computer is connected, regardless of its Bluetooth name:

1. Choose ANSI or ISO to match the keyboard type you want macOS to identify.
2. When asked for the key next to the left Shift key, send physical Z (ANSI) or the
   ISO extra key (ISO).
3. When asked for the key next to the right Shift key, send the physical `/` key.

Calibration sends physical keys directly and is unaffected by the text layout selection.
ISO calibration does not add international text layouts; text mapping still uses the
selected US QWERTY, Dvorak, or Colemak layout.

See [DEVELOPMENT.md](DEVELOPMENT.md) for build and test instructions.
