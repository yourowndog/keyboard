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

import android.media.AudioManager
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import java.io.File

/**
 * Plays a [SoundEvent]. The one seam between "something happened" and "make a noise".
 *
 * [InputFeedbackController][dev.patrickgold.florisboard.ime.input.InputFeedbackController] decides
 * whether audio is allowed at all (the user's toggle, system sound settings, volume) and then calls
 * [play]. Engines only choose and play a clip.
 */
interface SoundEngine {
    /**
     * Plays [event] at [volume] (0..1). Returns false when this engine has nothing for the event, so
     * the caller can fall back to another engine.
     */
    fun play(event: SoundEvent, volume: Float): Boolean

    /** Frees decoded audio. Called when the input method is destroyed. */
    fun release() {}
}

/**
 * The stock behaviour: Android's own key clicks through [AudioManager.playSoundEffect]. Android
 * offers only four keyboard clicks (standard, space, delete, return), which is why everything else
 * belongs in a [SoundPackEngine]. Events not raised by a key stay silent here, so adding a feature
 * sound never makes the stock keyboard click on something new.
 */
class SystemSoundEngine(private val audioManager: AudioManager) : SoundEngine {
    override fun play(event: SoundEvent, volume: Float): Boolean {
        if (!event.fromKey) return false
        val effect = when (event) {
            SoundEvent.KEY_DELETE, SoundEvent.KEY_DELETE_REPEAT -> AudioManager.FX_KEYPRESS_DELETE
            SoundEvent.KEY_ENTER -> AudioManager.FX_KEYPRESS_RETURN
            SoundEvent.KEY_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audioManager.playSoundEffect(effect, volume)
        return true
    }
}

/**
 * Plays a sound pack folder (see [SoundPackManifest]) and falls back to [fallback] for events the
 * pack does not cover.
 *
 * STUB: the manifest is read and clips are chosen, but nothing is decoded or played yet, so every
 * event currently falls through to [fallback]. The intended implementation:
 * - one `android.media.SoundPool` with `USAGE_ASSISTANCE_SONIFICATION` attributes and about 6
 *   streams, every clip loaded once when the pack is selected, never on the key-press path;
 * - per-event gain from [SoundPackManifest.gainFor] applied as a volume multiplier;
 * - a release on [release] and whenever the pack changes.
 * Pack selection (a preference holding the folder, under the app's files dir) and a settings
 * screen come with that work.
 */
class SoundPackEngine(
    private val folder: File,
    private val fallback: SoundEngine?,
) : SoundEngine {
    private val manifest: SoundPackManifest? =
        File(folder, SoundPackManifest.FILE_NAME).takeIf { it.isFile }
            ?.let { SoundPackManifest.parse(it.readText()).getOrNull() }
    private val rotation = manifest?.let { SoundClipRotation(it) }

    override fun play(event: SoundEvent, volume: Float): Boolean {
        val clip = rotation?.nextClip(event)
        if (clip != null) {
            flogDebug { "Sound pack ${manifest?.id}: $event -> $clip (not played yet; stub)" }
        }
        return fallback?.play(event, volume) ?: false
    }
}
