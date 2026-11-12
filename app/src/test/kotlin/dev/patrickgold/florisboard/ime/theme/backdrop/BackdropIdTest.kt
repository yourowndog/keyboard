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

package dev.patrickgold.florisboard.ime.theme.backdrop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackdropIdTest {
    @Test
    fun everyIdRoundTripsThroughItsStoredValue() {
        val ids = listOf(
            BackdropId.None,
            BackdropId.Builtin(BuiltinBackdrop.GRID),
            BackdropId.Builtin(BuiltinBackdrop.STARFIELD),
            BackdropId.PrivateAsset("bridge-panel.png"),
            BackdropId.Imported("my_photo.jpg"),
        )
        ids.forEach { assertEquals(it, BackdropId.parse(it.storedValue)) }
    }

    @Test
    fun anythingUnrecognisedFallsBackToNone() {
        listOf(
            null,
            "",
            "   ",
            "builtin:warp-core-from-a-newer-build",
            "file:../../shared_prefs/prefs.xml",
            "file:.hidden.png",
            "asset:sub/dir.png",
            "file:notes.txt",
            "http://example.com/x.png",
        ).forEach { assertEquals(BackdropId.None, BackdropId.parse(it), "for $it") }
    }

    @Test
    fun builtinIdsAreStable() {
        // Written to preferences and backups: renaming one silently drops users back to None.
        assertEquals(listOf("grid", "starfield", "scanlines"), BuiltinBackdrop.entries.map { it.id })
    }

    @Test
    fun importNamesAreSanitisedAndNeverOverwrite() {
        assertEquals("Bridge-Panel-v2.png", BackdropFiles.sanitize("Bridge Panel v2.png", "image/png", emptySet()))
        assertEquals("scan.gif", BackdropFiles.sanitize("content/scan.gif", null, emptySet()))
        assertEquals("IMG_0042.jpg", BackdropFiles.sanitize("IMG_0042", "image/jpeg", emptySet()))
        assertEquals("backdrop.png", BackdropFiles.sanitize(null, null, emptySet()))
        assertEquals("grid-3.png", BackdropFiles.sanitize("grid.png", "image/png", setOf("grid.png", "grid-2.png")))
        assertTrue(BackdropFiles.isSafeName(BackdropFiles.sanitize("../../evil name!.webp", null, emptySet())))
    }

    @Test
    fun onlyImageFilesAreSafeNames() {
        assertTrue(BackdropFiles.isSafeName("lcars_grid-01.webp"))
        assertFalse(BackdropFiles.isSafeName("README.md"))
        assertFalse(BackdropFiles.isSafeName(".partial.png"))
        assertFalse(BackdropFiles.isSafeName("a/b.png"))
    }
}
