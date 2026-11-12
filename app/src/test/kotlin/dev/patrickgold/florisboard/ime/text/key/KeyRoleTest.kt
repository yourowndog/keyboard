/*
 * Copyright (C) 2026 The OmniBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.key

import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyRoleTest {
    @Test
    fun rolesClassifyKeysByPurpose() {
        assertEquals(KeyRole.ALPHA, KeyRole.forKey(TextKeyData(code = 'k'.code)))
        assertEquals(KeyRole.NUMBER, KeyRole.forKey(TextKeyData(code = '0'.code)))
        assertEquals(KeyRole.SYMBOL, KeyRole.forKey(TextKeyData(code = '|'.code)))
        assertEquals(KeyRole.ENTER, KeyRole.forKey(TextKeyData(code = KeyCode.ENTER)))
        assertEquals(KeyRole.MODIFIER, KeyRole.forKey(TextKeyData(code = KeyCode.ARROW_DOWN)))
        assertEquals(KeyRole.LAYOUT, KeyRole.forKey(TextKeyData(code = KeyCode.VIEW_NUMERIC_ADVANCED)))
        assertEquals(KeyRole.PROFILE, KeyRole.forKey(TextKeyData(code = KeyCode.LANGUAGE_SWITCH)))
    }

    @Test
    fun roleIdsAreUniqueSelectorSafeStrings() {
        val ids = KeyRole.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // Ids are written into stylesheets as `key[role=`id`]`; keep them plain lowercase words.
        assertTrue(ids.all { it.matches(Regex("[a-z]+")) }, ids.toString())
    }
}
