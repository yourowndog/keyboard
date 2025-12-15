# OmniBoard Usage Harvest

This file is written to by the keyboard during use.
Review periodically to update dictionary, ignore lists, etc.

Copy to repo: `cp /sdcard/Documents/usage_harvest.md ~/vault/projects/keyboard/`

---

<!-- 
Format of entries:

[ACCEPTED] timestamp | typed → corrected | ctx: "previous_word"
  - User accepted an autocorrection (continued typing after correction)
  - These words should probably stay corrected

[REJECTED] timestamp | typed ← correction (reverted) | ctx: "previous_word"  
  - User backspaced to revert an autocorrection
  - Consider adding 'typed' to dictionary, or 'typed → correction' to ignore list

[NEW_WORD] timestamp | word | ctx: "previous_word"
  - User typed a word not in dictionary
  - Consider adding to dictionary if seen multiple times

[INSISTED] timestamp | word | ctx: "previous_word"
  - User explicitly picked their typed word over suggestions
  - Strong signal this word should be in dictionary

[PICKED] timestamp | typed → picked (manual) | ctx: "previous_word"
  - User manually picked a different suggestion from smartbar
-->

