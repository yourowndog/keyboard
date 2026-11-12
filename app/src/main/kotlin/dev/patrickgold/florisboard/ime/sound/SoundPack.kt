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

package dev.patrickgold.florisboard.ime.sound

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A sound pack's `soundpack.json`.
 *
 * A pack is a folder on the device, never part of the APK or the repository: Sam's LCARS packs are
 * licensed for his personal use only. The folder holds this manifest plus the clips it names, as
 * paths relative to the folder. Any [SoundEvent] the manifest leaves out falls back to the system
 * key click for key events and to silence for the rest.
 *
 * ```json
 * {
 *   "id": "lcars-1",
 *   "events": {
 *     "KEY_ALPHA": ["alpha-1.ogg", "alpha-2.ogg", "alpha-3.ogg"],
 *     "KEY_ENTER": ["enter.ogg"]
 *   },
 *   "gainDb": { "KEY_DELETE_REPEAT": -4 }
 * }
 * ```
 *
 * Several clips for one event play in turn, so fast typing does not machine-gun one sample.
 */
@Serializable
data class SoundPackManifest(
    val id: String,
    val events: Map<String, List<String>> = emptyMap(),
    val gainDb: Map<String, Float> = emptyMap(),
) {
    /** Clips for [event]; event names this build does not know are ignored rather than fatal. */
    fun clipsFor(event: SoundEvent): List<String> = events[event.name].orEmpty()

    fun gainFor(event: SoundEvent): Float = gainDb[event.name] ?: 0f

    /** Names in the manifest that no [SoundEvent] matches, typically typos worth reporting. */
    fun unknownEvents(): Set<String> = events.keys - SoundEvent.entries.map { it.name }.toSet()

    companion object {
        const val FILE_NAME = "soundpack.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Result<SoundPackManifest> = runCatching {
            json.decodeFromString(serializer(), text)
        }
    }
}

/** Picks clips for an event in rotation. Pure and single-threaded; the engine owns the instance. */
class SoundClipRotation(private val manifest: SoundPackManifest) {
    private val next = IntArray(SoundEvent.entries.size)

    fun nextClip(event: SoundEvent): String? {
        val clips = manifest.clipsFor(event)
        if (clips.isEmpty()) return null
        val i = next[event.ordinal]
        next[event.ordinal] = (i + 1) % clips.size
        return clips[i % clips.size]
    }
}
