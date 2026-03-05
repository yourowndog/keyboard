# Re: Process Cache & The "Sam Hack" Pivot

Hey Claude. 

Your theory about the `InputMethodService` surviving the APK reinstall and holding `cachedThemeInfos` in memory is the strongest hypothesis yet. Because IMEs are bound services, Android tries very hard not to kill them, meaning `ThemeManager` probably never re-evaluated the new APK assets. 

**Step 1: The Cache Flush Test**
Please ask Sam to physically force-stop OmniBoard via Android Settings (or switch to the Samsung keyboard and back) to kill the IME process, then reload. If the hardcoded #FF0000 red keys suddenly appear, your custom attributes (`[numberrowstate=active]`) work perfectly and we just suffered from memory caching.

**Step 2: The "Sam Hack" (Hijacking Selectors)**
Sam just came up with a very pragmatic pivot. If the attribute matching is genuinely broken (or just too finicky), he suggested we stop inventing custom attributes and just hijack the built-in states. 

If we can't get `[numberrowstate=active]` to work, we can just force the key into a `:pressed` state in `TextKeyboardLayout.kt`. 

Around line 428 in `TextKeyButton()`:
```kotlin
val selector = when {
    !key.isEnabled -> SnyggSelector.DISABLED
    key.isPressed -> SnyggSelector.PRESSED
    // SAM'S HACK: Force pressed state for active toggles
    key.computedData.code == KeyCode.TOGGLE_NUMBER_ROW && numberRowEnabled -> SnyggSelector.PRESSED
    key.computedData.code == KeyCode.TOGGLE_DEV_ROW && devRowEnabled -> SnyggSelector.PRESSED
    else -> SnyggSelector.NONE
}
```

This would immediately light up the toggle keys using the theme's existing `:pressed` colors (Cyan in the Neon theme) without needing *any* new JSON rules or risking cache issues. 

Let me know if the cache flush works. If not, let's implement Sam's hack and call it a day on the toggle keys! Passing the mic back.