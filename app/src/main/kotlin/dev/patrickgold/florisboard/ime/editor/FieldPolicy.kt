/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.editor

/**
 * What kind of text a field (or the token being typed) holds, and therefore which prose rules
 * are allowed to touch it.
 *
 * Prose rules are autocorrect, auto-space after punctuation, eating the space before
 * punctuation, phantom space and the double-space period. They are right for sentences and
 * wrong for addresses: in an email field `sam.d` became `Sam. D`, and in a URL field `https`
 * became `Http` (both seen in the keylog). Two layers decide it:
 *
 *  1. The field: email, URL and password variations never get prose rules.
 *  2. The token: in any field, a word that is already shaped like an address
 *     (`name@host`, `scheme://`, `www.`) is typed literally until the next whitespace. This is
 *     what keeps `sam@gmail.com` intact in Slack or Messages, which declare plain text fields.
 *
 * Kept free of Android types so it is unit tested directly.
 */
object FieldPolicy {
    enum class Kind {
        /** Ordinary text: all prose rules apply. */
        PROSE,
        EMAIL,
        URI,
        /** Passwords: no rules and no suggestions at all. */
        SECRET,
    }

    fun kindOf(variation: InputAttributes.Variation): Kind = when (variation) {
        InputAttributes.Variation.EMAIL_ADDRESS,
        InputAttributes.Variation.WEB_EMAIL_ADDRESS -> Kind.EMAIL
        InputAttributes.Variation.URI -> Kind.URI
        InputAttributes.Variation.PASSWORD,
        InputAttributes.Variation.VISIBLE_PASSWORD,
        InputAttributes.Variation.WEB_PASSWORD -> Kind.SECRET
        else -> Kind.PROSE
    }

    /** The run of non-whitespace characters that ends at the cursor. */
    fun currentToken(textBeforeCursor: CharSequence): String {
        var start = textBeforeCursor.length
        while (start > 0 && !textBeforeCursor[start - 1].isWhitespace()) start--
        return textBeforeCursor.substring(start)
    }

    /**
     * True when [token] is already unambiguously an email address or URL. A leading `@` is a
     * mention (`@sam` in Slack), not an address, so it needs something before it.
     */
    fun isAddressToken(token: CharSequence): Boolean {
        if (token.isEmpty()) return false
        val at = token.indexOf('@')
        if (at > 0) return true
        if (token.contains("://")) return true
        return token.startsWith("www.", ignoreCase = true)
    }

    /** True when no prose rule may rewrite, space or capitalize what is being typed. */
    fun isLiteral(kind: Kind, textBeforeCursor: CharSequence): Boolean =
        kind != Kind.PROSE || isAddressToken(currentToken(textBeforeCursor))

    /** A chip for the suggestion strip: [label] is shown, [insert] is what a tap types. */
    data class Chip(val label: String, val insert: String)

    /** Common mail hosts, most likely first. Generic on purpose: nothing personal ships here. */
    val MAIL_HOSTS = listOf("gmail.com", "icloud.com", "outlook.com", "yahoo.com", "hotmail.com")

    /** Top-level domains offered for any host, most likely first. */
    val TLDS = listOf("com", "io", "net", "org", "dev", "ai")

    /**
     * Strip chips for an email or URL being typed. Empty when the field is prose and the token is
     * not an address, or when the address already looks complete.
     */
    fun addressChips(kind: Kind, textBeforeCursor: CharSequence): List<Chip> {
        if (kind == Kind.SECRET) return emptyList()
        val token = currentToken(textBeforeCursor)
        val isAddress = isAddressToken(token)
        if (kind == Kind.PROSE && !isAddress) return emptyList()

        val at = token.lastIndexOf('@')
        if (kind == Kind.EMAIL || at > 0) {
            if (at < 0) {
                // Local part only, in an email field: offer the whole host.
                if (token.isEmpty()) return emptyList()
                return MAIL_HOSTS.take(4).map { Chip("@$it", "@$it") }
            }
            return hostChips(token.substring(at + 1), offerMailHosts = true)
        }

        // URLs: chips only while typing the host, never inside the path.
        val afterScheme = token.substringAfter("://", token)
        if (afterScheme.contains('/')) return emptyList()
        if (afterScheme.isEmpty()) return emptyList()
        return hostChips(afterScheme, offerMailHosts = false)
    }

    private fun hostChips(host: String, offerMailHosts: Boolean): List<Chip> {
        val dot = host.lastIndexOf('.')
        if (dot < 0) {
            val chips = mutableListOf<Chip>()
            if (offerMailHosts) {
                MAIL_HOSTS
                    .filter { it.startsWith(host, ignoreCase = true) && !it.equals(host, ignoreCase = true) }
                    .take(4)
                    .forEach { chips += Chip(it, it.substring(host.length)) }
            }
            if (host.isNotEmpty()) {
                TLDS.forEach { chips += Chip(".$it", ".$it") }
            }
            return chips.take(6)
        }
        // After the last dot: complete the top-level domain being typed.
        val tail = host.substring(dot + 1)
        if (dot == 0) return emptyList()
        return TLDS
            .filter { it.startsWith(tail, ignoreCase = true) && !it.equals(tail, ignoreCase = true) }
            .map { Chip(".$it", it.substring(tail.length)) }
    }
}
