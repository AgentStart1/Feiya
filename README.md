# Feiya

[![Test](https://github.com/storytellerF/Feiya/actions/workflows/test.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/test.yml)
[![Build](https://github.com/storytellerF/Feiya/actions/workflows/release.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/release.yml)

## Overview

Tap the ➕ button to select files to share. Then tap the port number to display a QR code — scan it to access the files from any browser.

Instant messaging via WebSocket: `your-ip:your-port/messages`

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

For macOS Keyboard Setup Assistant, use the calibration controls available whenever
a computer is connected, regardless of its Bluetooth name:

1. Choose ANSI or ISO to match the keyboard type you want macOS to identify.
2. When asked for the key next to the left Shift key, send physical Z (ANSI) or the
   ISO extra key (ISO).
3. When asked for the key next to the right Shift key, send the physical `/` key.

Calibration sends physical keys directly and is unaffected by the text layout selection.
ISO calibration does not add international text layouts; text mapping still uses the
selected US QWERTY, Dvorak, or Colemak layout.
