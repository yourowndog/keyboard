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


[REJECTED] 2025-12-15 18:12:04 | Bb ← By (reverted) | ctx: "Bb"
[REJECTED] 2025-12-15 18:12:17 | fuckin ← fucking (reverted) | ctx: "fuckin"
[ACCEPTED] 2025-12-15 18:13:36 | breaki → breaking | ctx: "breaki"
[ACCEPTED] 2025-12-15 18:13:41 | do → down | ctx: "do"
[REJECTED] 2025-12-15 18:13:54 | Ilysm ← Ills (reverted) | ctx: "Ilysm"
[ACCEPTED] 2025-12-15 18:15:36 | failu → failure | ctx: "failu"
[ACCEPTED] 2025-12-15 19:50:40 | est → eat | ctx: "est"
[ACCEPTED] 2025-12-15 19:50:52 | finner → dinner | ctx: "finner"
[REJECTED] 2025-12-15 20:07:21 | Yoj ← To (reverted) | ctx: "Yoj"
[REJECTED] 2025-12-16 07:28:16 | gal ← gap (reverted) | ctx: "gal"
[REJECTED] 2025-12-16 07:53:47 | termux ← Term (reverted) | ctx: "termux"
[REJECTED] 2025-12-16 08:01:01 | ur ← urdu (reverted) | ctx: "ur"
[ACCEPTED] 2025-12-16 08:52:02 | chrom → chromium | ctx: "chrom"
[REJECTED] 2025-12-16 08:53:47 | pacman ← layman (reverted) | ctx: "pacman"
[REJECTED] 2025-12-16 08:54:07 | S ← SO (reverted)
[REJECTED] 2025-12-16 08:54:10 | S ← SO (reverted)
[REJECTED] 2025-12-16 08:54:14 | S ← SO (reverted)
[REJECTED] 2025-12-16 08:54:21 | S ← SO (reverted)
[REJECTED] 2025-12-16 11:35:50 | ambien ← ambient (reverted) | ctx: "ambien"
[ACCEPTED] 2025-12-16 11:36:02 | adha → adhd | ctx: "adha"
[REJECTED] 2025-12-16 11:36:05 | meds ← mess (reverted) | ctx: "meds"
[REJECTED] 2025-12-16 11:36:21 | ambien ← ambient (reverted) | ctx: "ambien"
[ACCEPTED] 2025-12-16 11:38:04 | getti → getting | ctx: "getti"
[REJECTED] 2025-12-16 11:38:29 | filyer ← filter (reverted) | ctx: "filyer"
[REJECTED] 2025-12-16 11:40:09 | abien ← alien (reverted) | ctx: "abien"
[REJECTED] 2025-12-16 11:40:13 | ambien ← ambient (reverted) | ctx: "ambien"
[REJECTED] 2025-12-16 11:43:42 | Ily ← Ilyich (reverted) | ctx: "Ily"
[ACCEPTED] 2025-12-16 11:44:18 | insursnce → insurance | ctx: "insursnce"
[ACCEPTED] 2025-12-16 12:56:25 | noe → now | ctx: "noe"
[REJECTED] 2025-12-16 12:56:28 | noe ← now (reverted) | ctx: "noe"
[ACCEPTED] 2025-12-16 12:56:30 | no → now | ctx: "no"
[REJECTED] 2025-12-16 13:00:17 | thid ← this (reverted) | ctx: "thid"
[ACCEPTED] 2025-12-16 13:00:22 | libk → link | ctx: "libk"
[REJECTED] 2025-12-16 13:00:24 | libk ← link (reverted) | ctx: "libk"
[ACCEPTED] 2025-12-16 13:00:25 | libk → link | ctx: "libk"
[REJECTED] 2025-12-16 13:00:27 | libk ← link (reverted) | ctx: "libk"
[ACCEPTED] 2025-12-16 13:00:47 | screendhot → screenshot | ctx: "screendhot"
[REJECTED] 2025-12-16 13:00:51 | screendhot ← screenshot (reverted) | ctx: "screendhot"
[REJECTED] 2025-12-16 13:01:11 | vd ← vdsl (reverted) | ctx: "vd"
[REJECTED] 2025-12-16 13:01:21 | s ← so (reverted) | ctx: "s"
[REJECTED] 2025-12-16 13:01:24 | s ← so (reverted) | ctx: "s"
[REJECTED] 2025-12-16 13:01:41 | s ← so (reverted) | ctx: "s"
[REJECTED] 2025-12-16 13:02:06 | heybosrd ← keyboard (reverted) | ctx: "heybosrd"
[ACCEPTED] 2025-12-16 13:02:15 | keyb → keyboard | ctx: "keyb"
[ACCEPTED] 2025-12-16 13:08:38 | im → I'm | ctx: "im"
[REJECTED] 2025-12-16 13:08:42 | fu ← full (reverted) | ctx: "fu"
[ACCEPTED] 2025-12-16 13:08:45 | fuckin → fucking | ctx: "fuckin"
[REJECTED] 2025-12-16 13:08:57 | Wheres ← Where (reverted) | ctx: "Wheres"
[ACCEPTED] 2025-12-16 13:09:13 | dtsrt → start | ctx: "dtsrt"
[REJECTED] 2025-12-16 13:09:45 | pdy ← pay (reverted) | ctx: "pdy"
[REJECTED] 2025-12-16 13:10:10 | idk ← i'd (reverted) | ctx: "idk"
[REJECTED] 2025-12-16 13:10:16 | itd ← it'd (reverted) | ctx: "itd"
[REJECTED] 2025-12-16 13:27:27 | elde ← elder (reverted) | ctx: "elde"
[ACCEPTED] 2025-12-16 13:27:34 | lesving → leaving | ctx: "lesving"
[REJECTED] 2025-12-16 15:41:33 | AC ← ACT (reverted) | ctx: "AC"
[REJECTED] 2025-12-16 15:41:36 | AC ← ACT (reverted) | ctx: "AC"
[REJECTED] 2025-12-16 17:10:30 | mend ← Mend (reverted) | ctx: "mend"
[ACCEPTED] 2025-12-16 17:10:41 | doft → soft | ctx: "doft"
[REJECTED] 2025-12-16 17:10:47 | doft ← soft (reverted) | ctx: "doft"
[REJECTED] 2025-12-16 17:10:52 | Mens ← Mensa (reverted) | ctx: "Mens"
[REJECTED] 2025-12-16 17:14:34 | vaush ← Cause (reverted) | ctx: "vaush"
[REJECTED] 2025-12-17 04:14:07 | ac ← Act (reverted) | ctx: "ac"
[REJECTED] 2025-12-17 04:36:38 | sudo ← Sudoku (reverted) | ctx: "sudo"
[REJECTED] 2025-12-17 11:16:45 | nigga ← biggs (reverted) | ctx: "nigga"
[ACCEPTED] 2025-12-17 11:16:50 | tsble → table | ctx: "tsble"
[ACCEPTED] 2025-12-17 11:25:24 | ti → to | ctx: "ti"
[ACCEPTED] 2025-12-17 11:25:29 | wsll → wall | ctx: "wsll"
[ACCEPTED] 2025-12-17 11:25:47 | ritalin → rivaling | ctx: "ritalin"
[REJECTED] 2025-12-17 11:25:54 | tomorrowd ← tomorrow (reverted) | ctx: "tomorrowd"
[REJECTED] 2025-12-17 11:25:58 | tomorrows ← tomorrow (reverted) | ctx: "tomorrows"
[REJECTED] 2025-12-17 11:26:28 | i5d ← I (reverted)
[REJECTED] 2025-12-17 11:26:31 | itd ← it'd (reverted) | ctx: "itd"
[ACCEPTED] 2025-12-17 11:26:33 | it → its | ctx: "it"
[REJECTED] 2025-12-18 15:31:31 | ac ← Act (reverted) | ctx: "ac"
[REJECTED] 2025-12-20 03:46:09 | oned ← one (reverted) | ctx: "oned"
[REJECTED] 2025-12-20 03:46:14 | wof ← wow (reverted) | ctx: "wof"
[REJECTED] 2025-12-20 04:10:27 | ac ← Act (reverted) | ctx: "ac"
[ACCEPTED] 2025-12-20 11:28:33 | yall → y'all | ctx: "yall"
[ACCEPTED] 2025-12-20 11:28:36 | and → And | ctx: "and"
[ACCEPTED] 2025-12-20 11:28:37 | that's → that'd
[ACCEPTED] 2025-12-20 11:28:43 | ill → I'll | ctx: "ill"
[ACCEPTED] 2025-12-20 11:28:50 | do → Do | ctx: "do"
[ACCEPTED] 2025-12-20 11:28:52 | kiddos → kids | ctx: "kiddos"
[ACCEPTED] 2025-12-20 11:28:58 | kiddos → kids | ctx: "kiddos"
[ACCEPTED] 2025-12-20 11:29:04 | kiddos → kids | ctx: "kiddos"
[REJECTED] 2025-12-20 11:29:07 | kiddos ← kids (reverted) | ctx: "kiddos"
[ACCEPTED] 2025-12-20 11:29:13 | whats → What'd | ctx: "whats"
[ACCEPTED] 2025-12-20 11:43:07 | dont → don't | ctx: "dont"
[ACCEPTED] 2025-12-20 11:43:09 | aboyut → about | ctx: "aboyut"
[ACCEPTED] 2025-12-20 11:43:11 | mefs → mets | ctx: "mefs"
[ACCEPTED] 2025-12-20 11:43:13 | meds → mess | ctx: "meds"
[ACCEPTED] 2025-12-20 11:43:29 | theyre → they're | ctx: "theyre"
[ACCEPTED] 2025-12-20 11:44:07 | i → I | ctx: "i"
[ACCEPTED] 2025-12-20 11:44:08 | i → I | ctx: "i"
[ACCEPTED] 2025-12-20 11:44:12 | i → I | ctx: "i"
[ACCEPTED] 2025-12-20 11:44:13 | couldnt → couldn't | ctx: "couldnt"
[ACCEPTED] 2025-12-20 11:44:27 | well → Well | ctx: "well"
[ACCEPTED] 2025-12-20 11:44:32 | i → I | ctx: "i"
[ACCEPTED] 2025-12-20 11:44:39 | i → I | ctx: "i"
[ACCEPTED] 2025-12-20 11:44:50 | dude → Dude | ctx: "dude"
[ACCEPTED] 2025-12-20 11:44:52 | fuckin → fucking | ctx: "fuckin"
[ACCEPTED] 2025-12-20 11:45:09 | its → it's | ctx: "its"
[ACCEPTED] 2025-12-20 11:45:16 | its → it's | ctx: "its"
[ACCEPTED] 2025-12-20 11:46:12 | like → Like | ctx: "like"
[ACCEPTED] 2025-12-20 11:46:16 | its → it's | ctx: "its"
[ACCEPTED] 2025-12-20 11:46:31 | and → And
[ACCEPTED] 2025-12-20 11:46:35 | he's → he'd
[ACCEPTED] 2025-12-20 11:47:03 | but → But | ctx: "but"
[ACCEPTED] 2025-12-20 12:12:34 | what's → What'd
[ACCEPTED] 2025-12-20 12:12:46 | wrap → Wrap | ctx: "wrap"
[ACCEPTED] 2025-12-20 12:12:52 | fuggin → tugging | ctx: "fuggin"
[ACCEPTED] 2025-12-20 12:13:56 | i → I | ctx: "i"
[ACCEPTED] 2025-12-20 12:14:09 | they → They
[ACCEPTED] 2025-12-20 12:15:49 | Ill → I'll | ctx: "Ill"
[ACCEPTED] 2025-12-20 12:16:05 | al → all | ctx: "al"
[ACCEPTED] 2025-12-20 14:08:52 | accedd → Access | ctx: "accedd"
