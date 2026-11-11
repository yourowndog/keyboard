# Layout Pack bottomModRowCount Fix — Test Plan

**Defect:** `LayoutManager.computeKeyboardFromLayoutPack` hardcoded `bottomModRowCount = 2` for layout packs that have **zero** modifier rows.
**Fix:** `bottomModRowCount = 0` (see `LAYOUT_PACK_BOTTOMMODROWCOUNT_FIX.patch`)
**Verification Target:** `ConstructionSiteContractTest` + `GeometryFixtures` + device geometry check

---

## 1. Unit Test (JVM, no Android SDK required)

**File:** `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/ConstructionSiteContractTest.kt`

```kotlin
// Add after line 153 (after `layout pack rows that declare a role are taken at their word`)

@Test
fun `layout pack reports zero bottom mod rows`() {
    val keyboard = GeometryFixtures.lcarsHackerLayoutPack()
    
    // LCARS pack has 4 rows: 3 alpha + 1 primary_action, no modifier rows
    assertEquals(4, keyboard.rowCount)
    assertEquals(0, keyboard.bottomModRowCount, "layout packs have no modifier rows")
    
    // Semantic roles must be correct: last row is primary_action
    assertEquals(SemanticRowRole.PRIMARY_ACTION, keyboard.semanticRows.last().role)
    assertEquals("primary_action", keyboard.semanticRows.last().stableId)
}

@Test
fun `layout pack primary action row is not compressed by phantom mod rows`() {
    val keyboard = GeometryFixtures.lcarsHackerLayoutPack()
    
    // With bottomModRowCount=0, FlorisImeSizing partitions height across ALL 4 semantic rows
    // The primary action row gets its full policy height, not compressed by 2 phantom mod rows
    val primaryRow = keyboard.semanticRows.single { it.role == SemanticRowRole.PRIMARY_ACTION }
    assertEquals(3, keyboard.semanticRows.indexOf(primaryRow)) // 4th row (0-indexed)
    
    // The arrangement has 4 rows, matching semantics
    assertEquals(4, keyboard.arrangement.size)
    val spaceRowKeys = keyboard.arrangement.last()
    assertTrue(spaceRowKeys.any { it.computedData.code == KeyCode.SPACE })
}
```

---

## 2. Fixture (JVM, no Android SDK required)

**File:** `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/geometry/GeometryFixtures.kt`

```kotlin
// Add after `layoutPackWithDeclaredRoles()` (around line 316)

/** LCARS Hacker EN-US layout pack: 4 rows, fractional units, spacer, declared roles via row IDs. */
fun lcarsHackerLayoutPack(): TextKeyboard {
    val rowIds = listOf("row_letters_top", "row_letters_home", "row_letters_bottom", "row_space")
    return keyboard(
        rows = listOf(
            // Row 0: 12 keys, 1 unit each (QWERTYUIOP[]])
            Array(12) { key(code = 'q'.code + (it % 26), isAlpha = true, authoredUnits = 1f) },
            // Row 1: 11 keys (ASDFGHJKL;'\)
            Array(11) { key(code = 'a'.code + (it % 26), isAlpha = true, authoredUnits = 1f) },
            // Row 2: Shift(2) + ZXCVBNM,./ + Delete(1) = 12 units
            buildArray {
                add(key(KeyCode.SHIFT_LEFT, isAlpha = false, authoredUnits = 2f, type = KeyType.MODIFIER))
                for (i in 0..8) add(key(code = 'z'.code + i, isAlpha = true, authoredUnits = 1f))
                add(key(KeyCode.DEL, isAlpha = false, authoredUnits = 1f, type = KeyType.MODIFIER))
            },
            // Row 3: 123(2) Ctrl(2) Space(6) Menu(1) Enter(1) = 12 units — PRIMARY_ACTION
            arrayOf(
                key(KeyCode.SYM, isAlpha = false, authoredUnits = 2f, type = KeyType.MODIFIER),
                key(KeyCode.CTRL_LEFT, isAlpha = false, authoredUnits = 2f, type = KeyType.MODIFIER),
                key(KeyCode.SPACE, isAlpha = false, authoredUnits = 6f),
                key(KeyCode.UNSPECIFIED, isAlpha = false, authoredUnits = 1f, type = KeyType.MODIFIER), // MENU_TOGGLE
                key(KeyCode.ENTER, isAlpha = false, authoredUnits = 1f, type = KeyType.ENTER_EDITING),
            ).toTypedArray(),
        ),
        roles = rowIds.map { LayoutPackRowSemantics.resolve(it).first }, // All ALPHA+COMPATIBILITY_FALLBACK today
        bottomModRowCount = 0, // THE FIX
        provenance = { index ->
            RowProvenance.Pack(
                packId = "lcars_hacker_en_us",
                rowId = rowIds[index],
                sourceRowIndex = index,
                roleSource = LayoutPackRowSemantics.resolve(rowIds[index]).second,
            )
        },
    )
}
```

**Note:** The fixture uses current `LayoutPackRowSemantics.resolve` behavior (all `COMPATIBILITY_FALLBACK` → `ALPHA`). This is intentional — the test validates `bottomModRowCount`, not row semantics. When Issue #1 is fixed (schema v2), the fixture roles will update to declared `PRIMARY_ACTION` for `row_space`.

---

## 3. Device/Emulator Validation (requires Android SDK)

### Prerequisites
```bash
# One-time setup
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

### Validation Steps

| Step | Action | Expected | Failure Mode |
|------|--------|----------|--------------|
| 1 | Install APK | `adb install -r app/build/outputs/apk/debug/app-debug.apk` | Build fails |
| 2 | Enable IME | `adb shell ime enable dev.patrickgold.florisboard/...FlorisImeService` | Permission denied |
| 3 | Set IME | `adb shell ime set dev.patrickgold.florisboard/...FlorisImeService` | Not default |
| 4 | Select LCARS theme | Settings → Theme → **LCARS Tactical** → Apply | Theme not listed |
| 5 | Select LCARS layout | Settings → Layout → **LCARS Hacker EN-US** | Layout not listed |
| 6 | Open text field | Chrome address bar, Messages, any `EditText` | IME doesn't appear |
| 7 | **Visual check** | Space row height matches design; Space key ~50% width | Space row compressed, keys too tall |
| 8 | **Opacity check** | Settings → Theme → Window background opacity → slide 0-100% | Plate stays opaque, or keys fade |

### Pass Criteria
- ✅ Space row visually identical to LCARS design (not squished)
- ✅ Space key occupies ~6/12 of keyboard width
- ✅ Opacity slider changes keyboard plate transparency, keys remain opaque
- ✅ No crash, no ANR, no visual glitches

---

## 4. Headless Geometry Check (Robolectric / Compose UI test, when SDK available)

```kotlin
@RunWith(AndroidJUnit4::class)
class LayoutPackGeometryTest {
    @get:Rule val composeRule = createComposeRule()
    
    @Test
    fun lcarsLayoutPack_bottomModRowCountZero() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layoutManager = LayoutManager(context)
        val repo = LayoutPackRepository(context)
        val pack = repo.getLayoutPack("lcars_hacker_en_us").getOrThrow()
        val subtype = SubtypeCompat.getCurrentSubtype(context)
        val editorInfo = FlorisEditorInfo.Default
        val state = KeyboardState.new()
        
        val keyboard = layoutManager.computeKeyboardFromLayoutPack(
            pack, KeyboardMode.CHARACTERS, subtype, editorInfo, state
        ).await()
        
        assertEquals(0, keyboard.bottomModRowCount)
        assertEquals(4, keyboard.rowCount)
        assertEquals(SemanticRowRole.PRIMARY_ACTION, keyboard.semanticRows.last().role)
    }
}
```

---

## 5. Regression Guard (add to `ConstructionSiteContractTest`)

```kotlin
@COMPATIBILITY("Layout packs must never report phantom bottom modifier rows.")
@Test
fun `no layout pack fixture reports non-zero bottomModRowCount`() {
    val packs = listOf(
        GeometryFixtures.layoutPackWithSpacersAndUnits(),
        GeometryFixtures.layoutPackWithDeclaredRoles(),
        GeometryFixtures.lcarsHackerLayoutPack(),
    )
    for (kb in packs) {
        assertEquals(0, kb.bottomModRowCount, "fixture ${kb.mode} must have bottomModRowCount=0")
    }
}
```

---

## Execution Checklist

- [ ] Apply `LAYOUT_PACK_BOTTOMMODROWCOUNT_FIX.patch`
- [ ] Add fixture `lcarsHackerLayoutPack()` to `GeometryFixtures.kt`
- [ ] Add unit tests to `ConstructionSiteContractTest.kt`
- [ ] Run JVM tests: `./gradlew :app:test --tests "*ConstructionSiteContractTest*"`
- [ ] When SDK available: run device validation steps 1-8
- [ ] When SDK available: run Robolectric geometry test
- [ ] Commit with message referencing this test plan

---

## Known Limitations

1. **Row Semantics (Issue #1):** Fixture uses `COMPATIBILITY_FALLBACK` roles. When layout pack schema v2 adds declared roles, update fixture to use `DECLARED_ROW_ID` for `row_space` → `PRIMARY_ACTION`.
2. **Theme Variable Resolution (Issue #3):** Device step 8 covers LCARS hex color opacity. No unit test for `#RRGGBB` from JSON.
3. **Build Environment:** Current machine lacks `local.properties` / Android SDK. Tests validate logic; device validates pixels.

---

**Author:** kilo-free-worker (adversarial validation)
**Date:** 2026-11-06
**Depends on:** `LAYOUT_PACK_BOTTOMMODROWCOUNT_FIX.patch` applied