package com.storyteller_f.feiya

/** One physical key plus the modifier bit mask, matching keyboard report ID 2. */
data class HidKey(val usage: Int, val modifier: Int = 0) {
    init {
        require(usage in 0x04..0x65)
        require(modifier in 0..0xff)
    }

    companion object {
        const val SHIFT = 0x02
    }
}

interface KeyboardLayout {
    fun keyFor(char: Char): HidKey?
}

enum class TargetKeyboardLayout(val mapping: KeyboardLayout) {
    QWERTY(UsQwerty), DVORAK(Dvorak), COLEMAK(Colemak)
}

// Characters produced by each physical US key, from the number row downwards.
// The inverse lookup translates a requested character into the target OS's key position.
private class AnsiKeyboardLayout(normal: String, shifted: String) : KeyboardLayout {
    private val usages = listOf(0x35) + (0x1e..0x27) + listOf(0x2d, 0x2e) +
        listOf(0x14, 0x1a, 0x08, 0x15, 0x17, 0x1c, 0x18, 0x0c, 0x12, 0x13, 0x2f, 0x30, 0x31) +
        listOf(0x04, 0x16, 0x07, 0x09, 0x0a, 0x0b, 0x0d, 0x0e, 0x0f, 0x33, 0x34) +
        listOf(0x1d, 0x1b, 0x06, 0x19, 0x05, 0x11, 0x10, 0x36, 0x37, 0x38)

    init {
        require(normal.length == usages.size && shifted.length == usages.size)
    }

    private val keys = normal.mapIndexed { index, char -> char to HidKey(usages[index]) }.toMap() +
        shifted.mapIndexed { index, char -> char to HidKey(usages[index], HidKey.SHIFT) }.toMap() +
        mapOf(' ' to HidKey(0x2c), '\n' to HidKey(0x28), '\r' to HidKey(0x28),
            '\t' to HidKey(0x2b), '\b' to HidKey(0x2a), '\u001b' to HidKey(0x29),
            '\u007f' to HidKey(0x4c))

    override fun keyFor(char: Char): HidKey? = keys[char]
}

object UsQwerty : KeyboardLayout by AnsiKeyboardLayout(
    "`1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./",
    "~!@#$%^&*()_+QWERTYUIOP{}|ASDFGHJKL:\"ZXCVBNM<>?"
)

object Dvorak : KeyboardLayout by AnsiKeyboardLayout(
    "`1234567890[]',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwvz",
    "~!@#$%^&*(){}\"<>PYFGCRL?+|AOEUIDHTNS_:QJKXBMWVZ"
)

object Colemak : KeyboardLayout by AnsiKeyboardLayout(
    "`1234567890-=qwfpgjluy;[]\\arstdhneio'zxcvbkm,./",
    "~!@#$%^&*()_+QWFPGJLUY:{}|ARSTDHNEIO\"ZXCVBKM<>?"
)

/** Returns null before anything is sent if any character is unsupported. */
fun KeyboardLayout.keysFor(text: String): List<HidKey>? {
    // A Windows line ending represents one Enter press.
    return text.replace("\r\n", "\n").map { keyFor(it) ?: return null }
}

enum class KeyboardCalibration(val leftShiftNeighbor: HidKey) {
    ANSI(HidKey(0x1d)), ISO(HidKey(0x64));

    val rightShiftNeighbor: HidKey get() = HidKey(0x38)
}
