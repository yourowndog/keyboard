### 2025-11-29
* **Task:** Generated a git diff for debugging rendering issues, excluding the frequency dictionary.
* **Files:** `diff_bughunt.txt`

### 2025-11-30
* **Task:** Added `agents.md` with richer index (layouts, overrides, smartbar, voice/Whisper, user dict), file tree, cognitive map, build/hardware notes, pending work.
* **Files:** `agents.md`
* **Notes:** Smartbar is plain Text with horizontal scroll, maxLines=1, overflow visible. Use `$` templates for layouts; ENTER text pattern documented. Whisper needs BuildConfig keys; ctrl/uninstall/backspace text still pending. – Codex

### 2025-12-01
* **Task:** Latched ctrl softkey until next key press and dispatch ctrl+key chords for character keys.
* **Files:** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
* **Notes:** Ctrl down keeps state; next key sends ctrl chord (letters/digits/space/enter) and then clears. Ctrl also applies to arrows and clears after use; ctrl-up no longer cancels. – Codex
