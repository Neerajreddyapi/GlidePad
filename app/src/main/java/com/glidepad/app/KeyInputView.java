package com.glidepad.app;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Invisible view that receives soft-keyboard input and forwards each keystroke. */
public class KeyInputView extends View {

    public interface Listener {
        void onChar(char c);

        void onKey(int hidCode);
    }

    private Listener listener;

    public KeyInputView(Context c, AttributeSet a) {
        super(c, a);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    void setListener(Listener l) { listener = l; }

    @Override public boolean onCheckIsTextEditor() { return true; }

    @Override public InputConnection onCreateInputConnection(EditorInfo out) {
        // "Visible password" turns off autocorrect and word composing on most keyboards,
        // so every key press arrives straight away.
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_NONE;
        return new Connection();
    }

    private void text(char c) { if (listener != null) listener.onChar(c); }

    private void key(int code) { if (listener != null) listener.onKey(code); }

    private class Connection extends BaseInputConnection {
        private String composing = "";

        Connection() { super(KeyInputView.this, false); }

        @Override public boolean commitText(CharSequence t, int pos) {
            replaceComposing(t.toString());
            composing = "";
            return true;
        }

        @Override public boolean setComposingText(CharSequence t, int pos) {
            replaceComposing(t.toString());
            composing = t.toString();
            return true;
        }

        @Override public boolean finishComposingText() {
            composing = "";
            return true;
        }

        @Override public boolean deleteSurroundingText(int before, int after) {
            for (int i = 0; i < before; i++) key(Keys.BACKSPACE);
            for (int i = 0; i < after; i++) key(Keys.DELETE);
            return true;
        }

        @Override public boolean performEditorAction(int action) {
            key(Keys.ENTER);
            return true;
        }

        @Override public boolean sendKeyEvent(KeyEvent e) {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DEL: key(Keys.BACKSPACE); break;
                case KeyEvent.KEYCODE_FORWARD_DEL: key(Keys.DELETE); break;
                case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: key(Keys.ENTER); break;
                case KeyEvent.KEYCODE_TAB: key(Keys.TAB); break;
                case KeyEvent.KEYCODE_ESCAPE: key(Keys.ESC); break;
                case KeyEvent.KEYCODE_DPAD_LEFT: key(Keys.LEFT); break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: key(Keys.RIGHT); break;
                case KeyEvent.KEYCODE_DPAD_UP: key(Keys.UP); break;
                case KeyEvent.KEYCODE_DPAD_DOWN: key(Keys.DOWN); break;
                default:
                    int u = e.getUnicodeChar(e.getMetaState());
                    if (u > 0) text((char) u);
            }
            return true;
        }

        /** Keyboards that compose words resend the whole word; only send the difference. */
        private void replaceComposing(String next) {
            int common = 0;
            while (common < composing.length() && common < next.length()
                    && composing.charAt(common) == next.charAt(common)) common++;
            for (int i = common; i < composing.length(); i++) key(Keys.BACKSPACE);
            for (int i = common; i < next.length(); i++) text(next.charAt(i));
        }
    }
}
