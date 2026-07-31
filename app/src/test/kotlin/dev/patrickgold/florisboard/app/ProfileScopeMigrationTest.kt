/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.keyboard.KeyboardProfile
import dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import dev.patrickgold.jetpref.datastore.model.PreferenceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Stage 04 acceptance: the Text/Coding profile split must not cost an upgrading user anything.
 *
 * These run against the real generated [FlorisPreferenceModelImpl] rather than a stand-in, so
 * `migrate` and the declared key registry are the actual ones that ship. That matters because the
 * failure this suite is built to catch is silent: renaming a user's settings onto a key that no
 * declaration reads would compile, run, and quietly reset their keyboard.
 */
class ProfileScopeMigrationTest {
    private val model = FlorisPreferenceModelImpl()

    /** Every key the model declares, ignoring type. */
    private val declaredKeys: Set<String> =
        model.declaredPreferenceEntries.keys.map { it.key }.toSet()

    private fun typeOf(key: String): PreferenceType? =
        model.declaredPreferenceEntries.keys.find { it.key == key }?.type

    // `PreferenceMigrationEntry`'s constructor and its `Action` enum are `internal` to JetPref —
    // only the datastore is meant to build one. Reflection is used rather than reimplementing the
    // entry, because the value of this suite comes from running the real `migrate` override
    // against the real declared-key registry. A stand-in entry type would test a copy of the
    // migration instead of the migration.
    private val actionClass: Class<*> =
        Class.forName("dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry\$Action")

    private val keepAsIs: Any =
        actionClass.enumConstants.first { (it as Enum<*>).name == "KEEP_AS_IS" }

    private val entryConstructor =
        PreferenceMigrationEntry::class.java.getDeclaredConstructor(
            actionClass,
            PreferenceType::class.java,
            String::class.java,
            String::class.java,
        ).apply { isAccessible = true }

    /**
     * Runs one persisted entry through the real migration, mirroring how `DataStore.loadAndUpdate`
     * calls it: one entry at a time, as read off disk.
     */
    private fun migrate(key: String, rawValue: String): PreferenceMigrationEntry {
        val type = typeOf(key) ?: PreferenceType.string()
        val entry = entryConstructor.newInstance(keepAsIs, type, key, rawValue)
            as PreferenceMigrationEntry
        return model.migrate(entry)
    }

    // --- The mapping itself -------------------------------------------------------------------

    @Test
    fun `every migrated key lands on a key the model actually declares`() {
        // The whole point of the suite. A target that nothing declares is dropped by the datastore
        // on load, which reads to the user as "the upgrade reset my keyboard".
        for ((oldKey, newKey) in STAGE_04_PROFILE_SCOPED_KEYS) {
            assertTrue(
                newKey in declaredKeys,
                "migration sends '$oldKey' to '$newKey', which no preference declares",
            )
        }
    }

    @Test
    fun `every migrated key targets coding scope and leaves text scope alone`() {
        for ((oldKey, newKey) in STAGE_04_PROFILE_SCOPED_KEYS) {
            assertTrue(
                newKey.startsWith("keyboard__${KeyboardProfile.CODING.id}__"),
                "'$oldKey' must migrate into Coding scope, got '$newKey'",
            )
        }
        // Text is required to start from clean defaults rather than inherit Coding's tuning.
        assertTrue(
            STAGE_04_PROFILE_SCOPED_KEYS.values.none { it.contains(KeyboardProfile.TEXT.id) },
            "no old preference may migrate into Text scope",
        )
    }

    @Test
    fun `both profiles declare the same scoped preferences`() {
        val suffixes = STAGE_04_PROFILE_SCOPED_KEYS.keys.map { it.removePrefix("keyboard__") }
        for (profile in KeyboardProfile.entries) {
            for (suffix in suffixes) {
                val key = "keyboard__${profile.id}__$suffix"
                assertTrue(key in declaredKeys, "profile ${profile.id} is missing '$key'")
            }
        }
    }

    @Test
    fun `migration preserves the preference type`() {
        // TypedKey is (type, key), so a rename that changes type is dropped by the datastore's
        // `contains` check just as surely as a wrong key would be. The pre-migration types are
        // written out here rather than read from the model, because the old declarations are gone
        // — this table is the only remaining record of what a user's datastore holds on disk.
        val typesBeforeStage04 = mapOf(
            "keyboard__number_row" to PreferenceType.boolean(),
            "keyboard__dev_row" to PreferenceType.boolean(),
            "keyboard__mod_rows_visible" to PreferenceType.boolean(),
            "keyboard__height_factor_portrait" to PreferenceType.integer(),
            "keyboard__height_factor_landscape" to PreferenceType.integer(),
            "keyboard__alpha_key_width" to PreferenceType.integer(),
            "keyboard__mod_key_width" to PreferenceType.integer(),
            "keyboard__key_spacing_vertical" to PreferenceType.float(),
            "keyboard__key_spacing_horizontal" to PreferenceType.float(),
            "keyboard__bottom_row_height_factor" to PreferenceType.integer(),
            "keyboard__alpha_row_height_factor" to PreferenceType.integer(),
            "keyboard__mod_row_upper_gap" to PreferenceType.integer(),
            "keyboard__mod_row_inner_gap" to PreferenceType.integer(),
            "keyboard__mod_row_lower_gap" to PreferenceType.integer(),
            "keyboard__key_customizations" to PreferenceType.string(),
        )
        assertEquals(
            STAGE_04_PROFILE_SCOPED_KEYS.keys,
            typesBeforeStage04.keys,
            "the recorded pre-migration types must cover exactly the migrated keys",
        )
        for ((oldKey, oldType) in typesBeforeStage04) {
            val newKey = STAGE_04_PROFILE_SCOPED_KEYS.getValue(oldKey)
            assertEquals(oldType, typeOf(newKey), "'$oldKey' and '$newKey' must share a type")
        }
    }

    // --- Required test: fresh install ----------------------------------------------------------

    @Test
    fun `fresh install defaults to coding with untouched scoped defaults`() {
        // A fresh install has no persisted entries at all, so migrate never runs and the declared
        // defaults stand. Coding must be the default profile and it must be selectable.
        assertEquals(KeyboardProfile.CODING, KeyboardProfile.Default)
        assertTrue(KeyboardProfile.Default.isSelectable)
        assertEquals(KeyboardProfile.Default.id, model.keyboard.activeProfileId.default)

        // Text starts clean rather than inheriting anything.
        assertEquals(true, model.keyboard.textProfile.modRowsVisible.default)
        assertEquals(false, model.keyboard.textProfile.numberRow.default)
        assertEquals("{}", model.keyboard.textProfile.keyCustomizations.default)
    }

    // --- Required test: upgrade from populated old preferences ---------------------------------

    @Test
    fun `upgrade renames old global keys into coding and preserves their values`() {
        val populated = mapOf(
            "keyboard__key_spacing_vertical" to "3.5",
            "keyboard__alpha_key_width" to "118",
            "keyboard__mod_row_upper_gap" to "7",
            "keyboard__height_factor_portrait" to "92",
            "keyboard__key_customizations" to """{"32":{"label":"space"}}""",
        )
        for ((oldKey, rawValue) in populated) {
            val migrated = migrate(oldKey, rawValue)
            assertEquals(
                STAGE_04_PROFILE_SCOPED_KEYS.getValue(oldKey),
                migrated.key,
                "'$oldKey' migrated to the wrong key",
            )
            assertEquals(rawValue, migrated.rawValue, "'$oldKey' must keep its value, not reset")
        }
    }

    @Test
    fun `upgrade leaves genuinely global preferences where they are`() {
        // Window offsets, one-handed mode, hint behaviour and popup timing describe the device or
        // the person, not the keyboard being drawn, so they must not be swept into a profile.
        val globals = listOf(
            "keyboard__bottom_offset_portrait",
            "keyboard__bottom_offset_landscape",
            "keyboard__one_handed_mode_enabled",
            "keyboard__one_handed_mode_scale_factor",
            "keyboard__hinted_number_row_enabled",
            "keyboard__font_size_multiplier_portrait",
            "keyboard__long_press_delay",
        )
        for (key in globals) {
            assertEquals(key, migrate(key, "1").key, "'$key' must stay global")
            assertTrue(key in declaredKeys, "'$key' should still be declared")
        }
    }

    @Test
    fun `upgrade does not touch subtype or component family preferences`() {
        // Subtypes and the eight component-family mappings are independent of profiles and must
        // survive the split untouched — a renamed subtype key orphans the user's languages.
        val subtypeKeys = declaredKeys.filter { it.startsWith("localization__") }
        assertTrue(subtypeKeys.isNotEmpty(), "expected localization preferences to exist")
        for (key in subtypeKeys) {
            assertEquals(key, migrate(key, "[]").key, "'$key' must not be migrated")
        }
        assertTrue(
            STAGE_04_PROFILE_SCOPED_KEYS.keys.none { it.startsWith("localization__") },
            "no subtype preference may be profile-scoped",
        )

        // The bundled Coding composition keeps working while names migrate: the layout id is
        // persisted inside subtypes, so it stays as-is even though the display name becomes
        // truthful.
        assertEquals(
            "org.florisboard.layouts:qwerty_wide",
            Subtype.DEFAULT.layoutMap.characters.toString(),
            "the persisted characters layout id must not be renamed",
        )
    }

    // --- Required test: compact Coding ----------------------------------------------------------

    @Test
    fun `compact coding survives the upgrade`() {
        // `modRowsVisible=false` is compact Coding. Its scoped default is true, so if the migration
        // dropped the entry instead of renaming it the user's compact keyboard would silently
        // expand — exactly the regression this asserts against.
        assertEquals(true, model.keyboard.codingProfile.modRowsVisible.default)
        val migrated = migrate("keyboard__mod_rows_visible", "false")
        assertEquals("keyboard__${KeyboardProfile.CODING.id}__mod_rows_visible", migrated.key)
        assertEquals("false", migrated.rawValue)
    }

    // --- Required test: idempotency --------------------------------------------------------------

    @Test
    fun `migration is idempotent`() {
        // Idempotency here is structural rather than counted: `transform` renames the entry, so
        // after one pass the old key is gone from disk and the rule can never match again. Feeding
        // an already-migrated key back in must therefore be a no-op.
        for ((oldKey, newKey) in STAGE_04_PROFILE_SCOPED_KEYS) {
            val once = migrate(oldKey, "42")
            assertEquals(newKey, once.key)

            val twice = migrate(once.key, once.rawValue)
            assertEquals(newKey, twice.key, "re-running the migration must not move '$newKey' again")
            assertEquals("42", twice.rawValue, "re-running the migration must not alter the value")
        }
    }

    @Test
    fun `already scoped text keys are never rewritten`() {
        for (suffix in STAGE_04_PROFILE_SCOPED_KEYS.keys.map { it.removePrefix("keyboard__") }) {
            val key = "keyboard__${KeyboardProfile.TEXT.id}__$suffix"
            assertEquals(key, migrate(key, "1").key, "'$key' must be left alone")
        }
    }

    // --- Required test: corrupt profile id --------------------------------------------------------

    @Test
    fun `unknown or corrupt profile ids fall back to the default`() {
        val garbage = listOf(null, "", " ", "codin", "CODING", "Coding", "text ", "1", "{}", "null")
        for (id in garbage) {
            assertEquals(
                KeyboardProfile.Default,
                KeyboardProfile.fromId(id),
                "'$id' should fall back to the default profile",
            )
        }
    }

    @Test
    fun `a profile with no runtime behind it is never selected`() {
        // Text is declared but has no layout until Stage 08. Persisting its id must not be able to
        // route the keyboard to assets that do not exist yet.
        assertTrue(!KeyboardProfile.TEXT.isSelectable, "Text must stay unselectable until Stage 08")
        assertEquals(KeyboardProfile.Default, KeyboardProfile.fromId(KeyboardProfile.TEXT.id))
        assertTrue(
            KeyboardProfile.Default.isSelectable,
            "the fallback profile must itself be selectable",
        )
    }

    // --- Required test: process restart -----------------------------------------------------------

    @Test
    fun `a selected available profile survives a process restart`() {
        // A restart is a persist-then-reload. What is written is the profile's id; what comes back
        // must be the same profile, resolved through the same tolerant path used at startup.
        for (profile in KeyboardProfile.entries.filter { it.isSelectable }) {
            val persisted = profile.id
            assertSame(
                profile,
                KeyboardProfile.fromId(persisted),
                "profile ${profile.id} did not survive a round trip",
            )
        }
    }

    @Test
    fun `profile ids are stable and distinct from enum names`() {
        // The persisted vocabulary must not move when the enum is renamed.
        assertEquals("text", KeyboardProfile.TEXT.id)
        assertEquals("coding", KeyboardProfile.CODING.id)
        assertEquals(
            KeyboardProfile.entries.size,
            KeyboardProfile.entries.map { it.id }.toSet().size,
            "profile ids must be unique",
        )
    }

    // --- Resolver totality --------------------------------------------------------------------

    @Test
    fun `the resolver returns a distinct block for every profile`() {
        val blocks = KeyboardProfile.entries.associateWith { model.keyboard.profile(it) }
        assertEquals(
            KeyboardProfile.entries.size,
            blocks.values.distinct().size,
            "each profile must have its own preference block",
        )
        assertSame(model.keyboard.codingProfile, model.keyboard.profile(KeyboardProfile.CODING))
        assertSame(model.keyboard.textProfile, model.keyboard.profile(KeyboardProfile.TEXT))
    }

    @Test
    fun `the formerly global scoped keys are no longer declared`() {
        // If an old key were still declared it would keep its persisted value and shadow the
        // migration, leaving two sources of truth for the same setting.
        for (oldKey in STAGE_04_PROFILE_SCOPED_KEYS.keys) {
            assertTrue(
                oldKey !in declaredKeys,
                "'$oldKey' moved into profile scope but is still declared globally",
            )
        }
    }

    @Test
    fun `no two declared preferences share a typed key`() {
        val typedKeys = model.declaredPreferenceEntries.keys.toList()
        assertEquals(
            typedKeys.size,
            typedKeys.toSet().size,
            "duplicate typed keys would make one preference shadow another",
        )
    }
}
