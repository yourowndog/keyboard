# Stage 04 Results — Profiles, Persistence, and Naming

Commits: `296719be` (persistence), `26cf5e73` (naming and selector seam), on
`keygeo-phase3-normalization`.

## Outcome

Text/Coding profile identity exists, the preferences that describe a specific keyboard are scoped to
a profile, and the layout a user sees is named for what it is. An upgrading user's keyboard is
unchanged: every old key is renamed rather than reset, and Coding is the default profile.

## Decisions taken

**Text is declared but not selectable.** The stage contract defers the Text runtime to Stage 08, and
the non-goals forbid a new Text asset. `KeyboardProfile.TEXT` therefore exists, owns its own
preference block, and is resolved away by `KeyboardProfile.fromId`, which returns the default for any
profile that is not `isSelectable`. Nothing can route the keyboard to assets that do not exist yet,
including a stale or hand-edited persisted id.

**Idempotency is structural, not counted.** JetPref already exposes a per-entry `migrate` hook that
runs at load. `entry.transform(key = …)` renames the entry, so after one pass the old key is gone
from disk and no rule can match again. A version int was considered and rejected: it would add a
second source of truth for whether the migration has run, and the two could disagree.

**Height factor is profile-scoped.** This is the one genuinely debatable entry in the scoped list —
it is close to device-level. It went into the profile because the solver consumes it and a six-row
Coding keyboard wants a different height from prose. Easy to reverse if that reads wrong in use.

**The scope errs narrow.** Only the four categories the contract enumerates are scoped. The
ambiguous cases — font size multipliers, hint behaviour, utility key, space bar mode — stay global.
Scoping something later is additive; un-scoping requires a second migration.

## Preference mapping

| Old key | New key | Type |
| --- | --- | --- |
| `keyboard__number_row` | `keyboard__coding__number_row` | boolean |
| `keyboard__dev_row` | `keyboard__coding__dev_row` | boolean |
| `keyboard__mod_rows_visible` | `keyboard__coding__mod_rows_visible` | boolean |
| `keyboard__height_factor_portrait` | `keyboard__coding__height_factor_portrait` | integer |
| `keyboard__height_factor_landscape` | `keyboard__coding__height_factor_landscape` | integer |
| `keyboard__alpha_key_width` | `keyboard__coding__alpha_key_width` | integer |
| `keyboard__mod_key_width` | `keyboard__coding__mod_key_width` | integer |
| `keyboard__key_spacing_vertical` | `keyboard__coding__key_spacing_vertical` | float |
| `keyboard__key_spacing_horizontal` | `keyboard__coding__key_spacing_horizontal` | float |
| `keyboard__bottom_row_height_factor` | `keyboard__coding__bottom_row_height_factor` | integer |
| `keyboard__alpha_row_height_factor` | `keyboard__coding__alpha_row_height_factor` | integer |
| `keyboard__mod_row_upper_gap` | `keyboard__coding__mod_row_upper_gap` | integer |
| `keyboard__mod_row_inner_gap` | `keyboard__coding__mod_row_inner_gap` | integer |
| `keyboard__mod_row_lower_gap` | `keyboard__coding__mod_row_lower_gap` | integer |
| `keyboard__key_customizations` | `keyboard__coding__key_customizations` | string |

`keyboard__active_profile_id` is new and global. Text declares the same fifteen under
`keyboard__text__*`, all at their clean defaults.

Unchanged and global on purpose: `keyboard__bottom_offset_{portrait,landscape}`,
`keyboard__one_handed_mode*`, `keyboard__landscape_input_ui_mode`, `keyboard__hinted_*`,
`keyboard__font_size_multiplier_*`, `keyboard__utility_key_*`, `keyboard__space_bar_display_mode`,
`keyboard__capitalization_behavior`, `keyboard__popup_enabled`, `keyboard__long_press_delay`,
`keyboard__incognito_indicator`, `keyboard__key_hints_visible`.

Untouched entirely: every `localization__*` key, all subtype ids, and the eight component-family
mappings.

## Naming

`qwerty_wide`, `qwerty_wide_swipe`, `qwerty_wide_full`, `qwerty_wide_default`, `qwerty_wide_mod` and
`qwerty_wide_swipe_mod` keep their ids. The ids are persisted inside subtypes and referenced from
`org.florisboard.localization/extension.json`, so renaming them would orphan a user's languages.
Their labels became "QWERTY Coding", "QWERTY Coding (Swipe)", "QWERTY Coding (Full)" and the
corresponding modifier names. This is the compatibility-alias case the naming constraint permits:
truthful language, persistent id.

## Correction carried in from Stage 03

Commit `f572b5e2` removed four dead per-region spacing preferences and claimed their persisted
entries would survive for Stage 07. That claim was wrong. `DataStore.loadAndUpdate` seeds its map
from `model.declaredPreferenceEntries` and gates every parsed line behind
`if (rawEncodedValues.contains(typedKey))`, so a key with no declaration is never retained, and
`persist` writes only what the map holds. Those four entries are discarded on the first write after
their declarations were removed.

Stage 07 therefore has no historical spacing values to migrate. This matches rather than contradicts
the Stage 03 product decision that historical geometry is forensic evidence, not a migration seed.
Both copies of the wrong comment have been corrected.

The same mechanism is why the scoped declarations had to land in the same commit as the migration:
renaming a key onto a target nothing declares would silently discard the user's value.

## Tests

`ProfileScopeMigrationTest` — 18 tests, all passing. They run against the real generated
`FlorisPreferenceModelImpl`, not a stand-in, so `migrate` and the declared-key registry are the ones
that ship. `PreferenceMigrationEntry`'s constructor is `internal` to JetPref and is reached by
reflection; building a substitute entry type would have tested a copy of the migration instead of
the migration.

Coverage against the contract's eight required tests:

| Required | Test |
| --- | --- |
| Fresh install defaults | `fresh install defaults to coding with untouched scoped defaults` |
| Upgrade from populated old preferences | `upgrade renames old global keys into coding and preserves their values` |
| Existing Coding settings preserved exactly | same, plus `every migrated key lands on a key the model actually declares` and `migration preserves the preference type` |
| Active subtype and component mappings preserved | `upgrade does not touch subtype or component family preferences` |
| Compact Coding preserved | `compact coding survives the upgrade` |
| Repeated migration is idempotent | `migration is idempotent`, `already scoped text keys are never rewritten` |
| Unknown/corrupt profile ID falls back safely | `unknown or corrupt profile ids fall back to the default`, `a profile with no runtime behind it is never selected` |
| Process restart restores the selected available profile | `a selected available profile survives a process restart` |

The remaining tests guard couplings the required list does not name: that the formerly global keys
are no longer declared (otherwise two sources of truth for one setting), that no two declared
preferences share a typed key, that both profiles declare the same fifteen preferences, that the
resolver is total over `KeyboardProfile` and returns a distinct block per profile, that profile ids
are unique and distinct from the enum names, and that genuinely global preferences were not swept
into a profile.

`./gradlew assembleDebug testDebugUnitTest` — BUILD SUCCESSFUL. No pre-existing test regressed. The
pre-existing warnings in `BackupScreen.kt`, `RestoreScreen.kt` and `CrashUtility.kt` are unchanged.

## Invalidation

Profile identity reaches the flow layer, not only the getters. `KeyboardManager` merges
`activeProfileId` with both profiles' row-visibility flows into the cache-clear subscription, so a
profile switch invalidates the keyboard the same way toggling a row does. Both profiles are observed
rather than re-subscribing on switch: the action is an idempotent cache clear, so a write to the
inactive profile costs one wasted invalidation from the settings screen.

Compose reads needed no such treatment. `observeAsState` is `asFlow()` fed into `collectAsState`,
which keys on the flow, so pointing a read at a different profile's preference object re-subscribes
it automatically. Every Compose consumer observes `activeProfileId` and resolves the block from it.

`KeyCustomizationExporter` uses `flatMapLatest` rather than `merge`, because its output file holds
one profile's JSON and must follow the active profile rather than react to either.

## Not done

Device verification. These commits are not installed. The change is behavioural for anyone with
existing settings — the migration runs once at first load — so it wants a real upgrade check against
a populated datastore rather than a fresh install.
