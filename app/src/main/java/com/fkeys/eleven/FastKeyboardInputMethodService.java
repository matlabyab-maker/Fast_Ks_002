package com.fkeys.eleven;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.ExtractedTextRequest;
import android.os.Handler;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import android.os.SystemClock;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

public class FastKeyboardInputMethodService extends InputMethodService {
    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private static FastKeyboardInputMethodService instance;
    private FastKeyboardView keyboard;
    private LinearLayout relatedBar;
    private final TextView[] relatedViews = new TextView[3];
    private SpeechRecognizer speechRecognizer;
    private final LinkedList<String> clipboardHistory = new LinkedList<>();
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private static final int MAX_HISTORY = 100;
    private static final int MAX_UNDO = 200;
    private static final long TYPE_GROUP_MS = 60000L;
    private static class UndoOp {
        final String inserted;
        final String deleted;
        UndoOp(String inserted, String deleted) { this.inserted = inserted == null ? "" : inserted; this.deleted = deleted == null ? "" : deleted; }
    }
    private final ArrayDeque<UndoOp> undoStack = new ArrayDeque<>();
    private final ArrayDeque<UndoOp> redoStack = new ArrayDeque<>();
    private long lastTypeTime = 0L;
    private long lastDeleteTime = 0L;
    private boolean applyingHistory = false;
    private float keyboardScale = 1.0f;

    private void pushUndo(String inserted, String deleted, boolean mergeTyped) {
        if (applyingHistory || ((inserted == null || inserted.isEmpty()) && (deleted == null || deleted.isEmpty()))) return;
        long now = SystemClock.uptimeMillis();
        if (mergeTyped && inserted != null && !inserted.isEmpty() && !undoStack.isEmpty() && now - lastTypeTime <= TYPE_GROUP_MS) {
            UndoOp old = undoStack.pop();
            undoStack.push(new UndoOp(old.inserted + inserted, old.deleted));
        } else if (mergeTyped && deleted != null && !deleted.isEmpty() && !undoStack.isEmpty() && now - lastDeleteTime <= TYPE_GROUP_MS) {
            UndoOp old = undoStack.pop();
            undoStack.push(new UndoOp(old.inserted, old.deleted + deleted));
        } else {
            undoStack.push(new UndoOp(inserted, deleted));
        }
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
        lastTypeTime = inserted != null && !inserted.isEmpty() ? now : 0L;
        lastDeleteTime = deleted != null && !deleted.isEmpty() ? now : 0L;
    }

    private void pushRedo(UndoOp op) {
        redoStack.push(op);
        while (redoStack.size() > MAX_UNDO) redoStack.removeLast();
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        clipboardManager = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipListener = () -> captureClipboard();
        if (clipboardManager != null) clipboardManager.addPrimaryClipChangedListener(clipListener);
        captureClipboard();
    }

    public static FastKeyboardInputMethodService getInstance() { return instance; }

    public void updateRelatedSuggestions(){
        if(relatedBar==null || keyboard==null) return;
        String[] r=keyboard.getRelatedSuggestions();
        boolean any=false;
        for(int i=0;i<3;i++){
            String text=r[i]==null?"":r[i];
            relatedViews[i].setText(text);
            relatedViews[i].setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
            if(!text.isEmpty()) any=true;
        }
        relatedBar.setVisibility(any?View.VISIBLE:View.GONE);
        keyboard.requestLayout();
    }

    @Override public View onCreateInputView() {
        try {
            android.app.Dialog dialog = getWindow();
            if (dialog != null && dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        } catch (Exception ignored) {}
        keyboard = new FastKeyboardView(this);
        keyboard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(380)));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);

        relatedBar = new LinearLayout(this);
        relatedBar.setOrientation(LinearLayout.HORIZONTAL);
        relatedBar.setGravity(Gravity.CENTER_VERTICAL);
        relatedBar.setPadding(dp(4), dp(3), dp(4), dp(3));
        relatedBar.setVisibility(View.GONE);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView tv = new TextView(this);
            relatedViews[i] = tv;
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(android.graphics.Color.rgb(20,40,80));
            tv.setTextSize(13);
            tv.setSingleLine(true);
            tv.setBackgroundColor(android.graphics.Color.rgb(245,245,245));
            tv.setPadding(dp(6),0,dp(6),0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(34),1f);
            lp.setMargins(dp(3),0,dp(3),0);
            relatedBar.addView(tv,lp);
            tv.setOnClickListener(v -> {
                String t = relatedViews[index].getText().toString();
                if (!t.isEmpty()) replaceCurrentWord(t);
            });
        }
        root.addView(relatedBar, new LinearLayout.LayoutParams(-1,dp(40)));
        root.addView(keyboard);
        return root;
    }

    public void setKeyboardResizeMode(boolean open, float scale) {
        // Keep the IME window anchored by Android. Do not resize the window itself:
        // doing so can push the lower keyboard rows outside the available IME area.
        // The keyboard view keeps its normal height; Resize only changes the drawn
        // keyboard area from its top edge while its bottom edge stays fixed.
        keyboardScale = Math.max(0.70f, Math.min(1.0f, scale));
        if (keyboard == null) return;
        ViewGroup.LayoutParams kp = keyboard.getLayoutParams();
        if (kp == null) kp = new LinearLayout.LayoutParams(-1, dp(380));
        kp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        kp.height = dp(380);
        keyboard.setLayoutParams(kp);
        keyboard.requestLayout();
        keyboard.invalidate();
    }

    public void setKeyboardScale(float scale) {
        setKeyboardResizeMode(false, scale);
    }

    public float getKeyboardScale() { return keyboardScale; }

    @Override public boolean onEvaluateFullscreenMode() {
        // F Keys 11 is designed as an ordinary bottom-of-screen keyboard.
        return false;
    }

    @Override public void onDestroy() {
        if (speechRecognizer != null) { try { speechRecognizer.destroy(); } catch (Exception ignored) {} speechRecognizer = null; }
        if (instance == this) instance = null;
        if (clipboardManager != null && clipListener != null) clipboardManager.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    private void captureClipboard() {
        try {
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) return;
            ClipData d = clipboardManager.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence t = d.getItemAt(0).coerceToText(this);
            if (t == null) return;
            String s = t.toString();
            if (s.trim().isEmpty()) return;
            clipboardHistory.remove(s);
            clipboardHistory.addFirst(s);
            while (clipboardHistory.size() > MAX_HISTORY) clipboardHistory.removeLast();
        } catch (Exception ignored) {}
    }

    public java.util.List<String> getClipboardHistory() { return new java.util.ArrayList<>(clipboardHistory); }
    public void pasteHistory(String s) { if (s != null && !s.isEmpty()) { typeUnit(s); } }

    @Override public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restartingInputView) {
        super.onStartInputView(info, restartingInputView);
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void launchScreenMagnifier() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.setAction("com.fkeys.eleven.START_MAGNIFIER");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    public void requestQuickSettingsTiles() {
        // Android does not expose a public API that lets an app silently add
        // Quick Settings tiles. The two TileService entries are registered in
        // the manifest; the user adds them from the system Quick Settings editor.
        try {
            startActivity(new Intent("android.settings.ACTION_QUICK_SETTINGS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
    }



    public void typeEnglish(String letter, boolean forceUpper) {
        if(letter==null || letter.isEmpty()) return;
        String s=letter.substring(0,1);
        if(forceUpper) { type(s.toUpperCase(java.util.Locale.US)); return; }
        InputConnection ic=getCurrentInputConnection();
        if(ic==null) return;
        CharSequence before=ic.getTextBeforeCursor(80,0);
        boolean startOfWord = before==null || before.length()==0 || Character.isWhitespace(before.charAt(before.length()-1));
        String out=(startOfWord ? s.toUpperCase(java.util.Locale.US) : s.toLowerCase(java.util.Locale.US));
        type(out);
    }

    public void voiceSearch(String languageTag) {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                Intent i = new Intent(this, MainActivity.class);
                i.setAction("com.fkeys.eleven.REQUEST_VOICE_PERMISSION");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.putExtra("language", languageTag);
                startActivity(i);
            } catch (Exception ignored) {}
            return;
        }
        startVoiceSearchIfPermitted(languageTag);
    }

    public void startVoiceSearchIfPermitted(String languageTag) {
        if (android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "سرویس تشخیص گفتار روی دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(android.os.Bundle p) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float rms) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onError(int error) {
                    if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) Toast.makeText(FastKeyboardInputMethodService.this, "تشخیص گفتار انجام نشد", Toast.LENGTH_SHORT).show();
                }
                public void onResults(android.os.Bundle results) {
                    java.util.ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && r.get(0) != null) typeUnit(r.get(0));
                    if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
                }
                public void onPartialResults(android.os.Bundle p) {}
                public void onEvent(int a, android.os.Bundle b) {}
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag == null ? "fa-IR" : languageTag);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            speechRecognizer.startListening(i);
            Toast.makeText(this, "اکنون صحبت کنید…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
            Toast.makeText(this, "شروع جستجوی صوتی ممکن نشد", Toast.LENGTH_SHORT).show();
        }
    }

    public void voiceAssist() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST));
        } catch (Exception ignored) {}
    }

    public void showInputMethodPickerSafe() {
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        } catch (Exception ignored) {}
    }

    private boolean isSentenceEnd(String s) {
        if (s == null || s.isEmpty()) return false;
        char c=s.charAt(s.length()-1);
        return c=='.' || c=='!' || c=='?' || c=='؟' || c=='؛' || c=='»' || c=='»';
    }

    public void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || s == null || s.isEmpty()) return;
        ic.commitText(s, 1);
        if (" ".equals(s) && lastTypeTime == 0L && !undoStack.isEmpty()) {
            UndoOp old = undoStack.pop();
            undoStack.push(new UndoOp(old.inserted + s, old.deleted));
            lastTypeTime = SystemClock.uptimeMillis();
            redoStack.clear();
        } else {
            pushUndo(s, "", true);
        }
        if (isSentenceEnd(s)) lastTypeTime = 0L;
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    /** Inserts a complete logical unit (paste, emoji, symbol, voice result).
     * It is intentionally NOT merged with ordinary character typing so one Undo removes the
     * whole inserted unit rather than joining it to earlier text. */
    public void typeUnit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || s == null || s.isEmpty()) return;
        ic.commitText(s, 1);
        pushUndo(s, "", false);
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void typeTo(InputConnection ic, String s) {
        if (ic == null || s == null || s.isEmpty()) return;
        ic.commitText(s, 1);
        // A history/clipboard insertion is one complete undo unit.
        pushUndo(s, "", false);
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void replaceCurrentWord(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(80, 0);
        int n = 0;
        if (before != null) {
            String b = before.toString();
            int i = b.length() - 1;
            while (i >= 0 && !Character.isWhitespace(b.charAt(i))) { n++; i--; }
        }
        if (n > 0) ic.deleteSurroundingText(n, 0);
        ic.commitText(s + " ", 1);
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        String deleted = (before == null || before.length() == 0) ? "" : before.subSequence(before.length()-1, before.length()).toString();
        ic.deleteSurroundingText(1, 0);
        if (!deleted.isEmpty()) {
            pushUndo("", deleted, true);
        }
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void move(int keyCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    // Reliable text-cursor movement for left/right controls. Sending DPAD events
    // is not handled consistently by every Android editor, so use setSelection
    // when absolute cursor positions can be determined.
    public void moveCursorHorizontal(int direction) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || (direction != -1 && direction != 1)) return;
        try {
            CharSequence before = ic.getTextBeforeCursor(10000, 0);
            CharSequence after = ic.getTextAfterCursor(10000, 0);
            int beforeLen = before == null ? 0 : before.length();
            int afterLen = after == null ? 0 : after.length();
            int pos = beforeLen;
            int next = Math.max(0, Math.min(beforeLen + afterLen, pos + direction));
            ic.setSelection(next, next);
        } catch (Exception ignored) {
            move(direction < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
        }
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void copyAll() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.performContextMenuAction(android.R.id.copy);
        captureClipboard();
    }

    public void copyScreen() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            CharSequence before = ic.getTextBeforeCursor(10000, 0);
            CharSequence after = ic.getTextAfterCursor(10000, 0);
            String text = (before == null ? "" : before.toString()) + (after == null ? "" : after.toString());
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("F Keys 11", text));
                captureClipboard();
            }
        } catch (Exception ignored) {}
    }

    public void cut() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) { ic.performContextMenuAction(android.R.id.cut); refresh(); }
    }

    public void paste() {
        try {
            if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                ClipData d=clipboardManager.getPrimaryClip();
                if(d!=null && d.getItemCount()>0){ CharSequence t=d.getItemAt(0).coerceToText(this); if(t!=null && t.length()>0){ typeUnit(t.toString()); return; } }
            }
        } catch(Exception ignored){}
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) { ic.performContextMenuAction(android.R.id.paste); refresh(); }
    }

    public void undo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || undoStack.isEmpty()) return;
        UndoOp op = undoStack.pop();
        applyingHistory = true;
        try {
            if (!op.inserted.isEmpty()) {
                ic.deleteSurroundingText(op.inserted.length(), 0);
            } else if (!op.deleted.isEmpty()) {
                ic.commitText(op.deleted, 1);
            }
        } finally {
            applyingHistory = false;
        }
        pushRedo(op);
        lastTypeTime = 0L; lastDeleteTime = 0L;
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void redo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || redoStack.isEmpty()) return;
        UndoOp op = redoStack.pop();
        applyingHistory = true;
        try {
            if (!op.inserted.isEmpty()) {
                ic.commitText(op.inserted, 1);
            } else if (!op.deleted.isEmpty()) {
                ic.deleteSurroundingText(op.deleted.length(), 0);
            }
        } finally {
            applyingHistory = false;
        }
        undoStack.push(op);
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        lastTypeTime = 0L; lastDeleteTime = 0L;
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    /**
     * Move the active input connection to the beginning using the same
     * KEYCODE_MOVE_HOME method as the supplied reference code.
     */
    public void goToHome() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            try {
                CharSequence before = ic.getTextBeforeCursor(10000, 0);
                if (before != null) {
                    ic.setSelection(0, 0);
                    return;
                }
            } catch (Exception ignored) {}
        }
        FastKeyboardAccessibilityService.scrollToTop();
    }

    /**
     * Move the active input connection to the end using the same
     * KEYCODE_MOVE_END method as the supplied reference code.
     */
    public void goToEnd() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            try {
                CharSequence before = ic.getTextBeforeCursor(10000, 0);
                if (before != null) {
                    ic.setSelection(before.length(), before.length());
                    return;
                }
            } catch (Exception ignored) {}
        }
        FastKeyboardAccessibilityService.scrollToBottom();
    }

    public void scrollToTop() {
        if (FastKeyboardAccessibilityService.isEnabled()) FastKeyboardAccessibilityService.scrollToTop();
        else { InputConnection ic=getCurrentInputConnection(); if(ic!=null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MOVE_HOME)); if(ic!=null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MOVE_HOME)); }
    }

    public void scrollToBottom() {
        if (FastKeyboardAccessibilityService.isEnabled()) FastKeyboardAccessibilityService.scrollToBottom();
        else { InputConnection ic=getCurrentInputConnection(); if(ic!=null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MOVE_END)); if(ic!=null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MOVE_END)); }
    }

    public void scrollOneStepUp() {
        if (FastKeyboardAccessibilityService.isEnabled()) FastKeyboardAccessibilityService.scrollOneStepUp();
        else { InputConnection ic=getCurrentInputConnection(); if(ic!=null){ ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP)); ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP)); } }
    }

    public void scrollOneStepDown() {
        if (FastKeyboardAccessibilityService.isEnabled()) FastKeyboardAccessibilityService.scrollOneStepDown();
        else { InputConnection ic=getCurrentInputConnection(); if(ic!=null){ ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)); ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN)); } }
    }

    public void escape() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE));
    }

    private void refresh() { if (keyboard != null) keyboard.refreshSuggestions(); }
}
