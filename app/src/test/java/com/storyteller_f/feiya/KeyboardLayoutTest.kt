package com.storyteller_f.feiya

import org.junit.Assert.*
import org.junit.Test

class KeyboardLayoutTest {
    @Test fun uppercaseLettersUseTheSameUsageAsLowercaseWithShift() {
        for (char in 'a'..'z') {
            assertEquals(HidKey(char - 'a' + 4), UsQwerty.keyFor(char))
            assertEquals(HidKey(char - 'a' + 4, 2), UsQwerty.keyFor(char.uppercaseChar()))
        }
    }

    @Test fun everyPrintableAsciiCharacterHasADistinctKeyInEveryLayout() {
        for (layout in TargetKeyboardLayout.entries) {
            val keys = (' '..'~').map { layout.mapping.keyFor(it) }
            assertFalse("Missing ASCII in $layout", keys.contains(null))
            assertEquals(95, keys.toSet().size)
        }
    }

    @Test fun usPunctuationAndShiftedNumbersMatchPhysicalUsages() {
        val punctuation = mapOf(
            '-' to 0x2d, '=' to 0x2e, '[' to 0x2f, ']' to 0x30, '\\' to 0x31,
            ';' to 0x33, '\'' to 0x34, '`' to 0x35, ',' to 0x36, '.' to 0x37, '/' to 0x38,
        )
        val shifted = "_+{}|:\"~<>?"
        punctuation.entries.forEachIndexed { index, (char, usage) ->
            assertEquals(HidKey(usage), UsQwerty.keyFor(char))
            assertEquals(HidKey(usage, 2), UsQwerty.keyFor(shifted[index]))
        }
        "1234567890".forEachIndexed { index, char ->
            assertEquals(HidKey(0x1e + index), UsQwerty.keyFor(char))
            assertEquals(HidKey(0x1e + index, 2), UsQwerty.keyFor("!@#$%^&*()"[index]))
        }
    }

    @Test fun alternateLayoutsMapRequestedCharactersToTargetPositions() {
        assertEquals(HidKey(0x07), Dvorak.keyFor('e'))
        assertEquals(HidKey(0x07, 2), Dvorak.keyFor('E'))
        assertEquals(HidKey(0x2f), Dvorak.keyFor('/'))
        assertEquals(HidKey(0x2f, 2), Dvorak.keyFor('?'))
        assertEquals(HidKey(0x2d, 2), Dvorak.keyFor('{'))
        assertEquals(HidKey(0x14, 2), Dvorak.keyFor('"'))
        assertEquals(HidKey(0x38), Dvorak.keyFor('z'))
        assertEquals(HidKey(0x0e), Colemak.keyFor('e'))
        assertEquals(HidKey(0x0e, 2), Colemak.keyFor('E'))
        assertEquals(HidKey(0x13), Colemak.keyFor(';'))
        assertEquals(HidKey(0x13, 2), Colemak.keyFor(':'))
        assertEquals(HidKey(0x08), Colemak.keyFor('f'))
    }

    @Test fun commonControlsAndWindowsNewlinesAreSupported() {
        for (layout in TargetKeyboardLayout.entries) {
            assertEquals(
                listOf(0x2c, 0x2b, 0x28, 0x28, 0x28, 0x2a, 0x29, 0x4c).map(::HidKey),
                layout.mapping.keysFor(" \t\n\r\r\n\b\u001b\u007f"),
            )
        }
    }

    @Test fun unsupportedCharactersRejectTheEntireText() {
        assertNull(UsQwerty.keysFor("abc你好"))
        assertNull(Dvorak.keysFor("hello😀"))
        assertNull(Colemak.keysFor("\u0000"))
        assertEquals(emptyList<HidKey>(), UsQwerty.keysFor(""))
    }
}
