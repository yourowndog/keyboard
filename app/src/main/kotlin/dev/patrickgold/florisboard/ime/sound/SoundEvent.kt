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

import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyRole

/**
 * Everything OmniBoard can make a sound for, named by meaning rather than by key code.
 *
 * A sound pack maps these names to clips (see [SoundPackManifest]); the code that reacts to input
 * only ever says which event happened. Key presses are classified by [KeyRole], the same
 * classification themes use, through [SoundEvent.forKey]. The
 * remaining events are raised directly through
 * [InputFeedbackController.soundEvent][dev.patrickgold.florisboard.ime.input.InputFeedbackController.soundEvent]
 * by the feature that owns them.
 *
 * The names are a stable contract: they are the keys of `soundpack.json`. Add new events at the
 * end of a group and never rename one.
 */
enum class SoundEvent(
    /** True when the event comes from pressing a key, so the system key-click may stand in for it. */
    val fromKey: Boolean,
) {
    // Key presses
    KEY_ALPHA(fromKey = true),
    KEY_NUMBER(fromKey = true),
    KEY_SYMBOL(fromKey = true),
    KEY_SPACE(fromKey = true),
    KEY_DELETE(fromKey = true),
    KEY_DELETE_REPEAT(fromKey = true),
    KEY_ENTER(fromKey = true),
    KEY_SHIFT(fromKey = true),
    KEY_CAPS_LOCK(fromKey = true),
    KEY_MODIFIER(fromKey = true),
    LAYOUT_SWITCH(fromKey = true),
    PROFILE_SWITCH(fromKey = true),
    COMMAND(fromKey = true),
    VOICE_START(fromKey = true),
    CLIPBOARD_PASTE(fromKey = true),

    // Raised by features, not by a key
    CANDIDATE_PICK(fromKey = false),
    AUTOCORRECT_APPLIED(fromKey = false),
    POPUP_OPEN(fromKey = false),
    VOICE_STOP(fromKey = false),
    DENY(fromKey = false);

    companion object {
        /**
         * The event a key press makes. Pure, so the mapping is unit-tested without Android.
         *
         * @param repeated true for the auto-repeat of a held key; only delete has its own sound.
         */
        fun forKey(data: KeyData, repeated: Boolean = false): SoundEvent {
            val event = when (KeyRole.forKey(data)) {
                KeyRole.ALPHA -> KEY_ALPHA
                KeyRole.NUMBER -> KEY_NUMBER
                KeyRole.SYMBOL -> KEY_SYMBOL
                KeyRole.SPACE -> KEY_SPACE
                KeyRole.DELETE -> KEY_DELETE
                KeyRole.ENTER -> KEY_ENTER
                KeyRole.SHIFT -> KEY_SHIFT
                KeyRole.CAPS_LOCK -> KEY_CAPS_LOCK
                KeyRole.MODIFIER -> KEY_MODIFIER
                KeyRole.LAYOUT -> LAYOUT_SWITCH
                KeyRole.PROFILE -> PROFILE_SWITCH
                KeyRole.VOICE -> VOICE_START
                KeyRole.CLIPBOARD -> CLIPBOARD_PASTE
                KeyRole.COMMAND -> COMMAND
            }
            return if (repeated && event == KEY_DELETE) KEY_DELETE_REPEAT else event
        }
    }
}
