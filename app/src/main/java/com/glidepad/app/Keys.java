package com.glidepad.app;

/** HID keyboard usages (US layout) and Mac modifier bits. */
final class Keys {
    private Keys() { }

    // Modifier bits. On a Mac, GUI is ⌘ and Alt is ⌥.
    static final int CTRL = 0x01, SHIFT = 0x02, ALT = 0x04, CMD = 0x08;

    static final int ENTER = 0x28, ESC = 0x29, BACKSPACE = 0x2A, TAB = 0x2B, SPACE = 0x2C,
            MINUS = 0x2D, EQUAL = 0x2E, DELETE = 0x4C, F11 = 0x44,
            RIGHT = 0x4F, LEFT = 0x50, DOWN = 0x51, UP = 0x52;

    static final int KEY_C = 0x06, KEY_Q = 0x14, KEY_V = 0x19, KEY_W = 0x1A, KEY_Z = 0x1D, KEY_4 = 0x21;

    // Consumer page usages (media keys).
    static final int PLAY_PAUSE = 0xCD, NEXT_TRACK = 0xB5, PREV_TRACK = 0xB6,
            MUTE = 0xE2, VOLUME_UP = 0xE9, VOLUME_DOWN = 0xEA;

    private static final String UNSHIFTED = "-=[]\\;'`,./";
    private static final String SHIFTED = "_+{}|:\"~<>?";
    private static final int[] SYMBOL_CODES = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
    private static final String SHIFTED_DIGITS = "!@#$%^&*()";

    /** Returns (modifiers << 8) | usage for a typed character, or -1 if it can't be typed. */
    static int forChar(char c) {
        switch (c) {
            case '\n': return ENTER;
            case '\t': return TAB;
            case ' ': return SPACE;
            case '‘': case '’': c = '\''; break; // smart quotes from some keyboards
            case '“': case '”': c = '"'; break;
        }
        if (c >= 'a' && c <= 'z') return 0x04 + (c - 'a');
        if (c >= 'A' && c <= 'Z') return (SHIFT << 8) | (0x04 + (c - 'A'));
        if (c >= '1' && c <= '9') return 0x1E + (c - '1');
        if (c == '0') return 0x27;
        int i = SHIFTED_DIGITS.indexOf(c);
        if (i >= 0) return (SHIFT << 8) | (0x1E + i);
        i = UNSHIFTED.indexOf(c);
        if (i >= 0) return SYMBOL_CODES[i];
        i = SHIFTED.indexOf(c);
        if (i >= 0) return (SHIFT << 8) | SYMBOL_CODES[i];
        return -1;
    }
}
