package dev.patrickgold.florisboard.ime.editor

import dev.patrickgold.florisboard.ime.editor.FieldPolicy.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldPolicyTest {
    private fun labels(kind: Kind, before: String) = FieldPolicy.addressChips(kind, before).map { it.label }
    private fun inserts(kind: Kind, before: String) = FieldPolicy.addressChips(kind, before).map { it.insert }

    @Test
    fun `email url and password fields are literal whatever is typed`() {
        assertEquals(Kind.EMAIL, FieldPolicy.kindOf(InputAttributes.Variation.WEB_EMAIL_ADDRESS))
        assertEquals(Kind.URI, FieldPolicy.kindOf(InputAttributes.Variation.URI))
        assertEquals(Kind.SECRET, FieldPolicy.kindOf(InputAttributes.Variation.WEB_PASSWORD))
        assertEquals(Kind.PROSE, FieldPolicy.kindOf(InputAttributes.Variation.WEB_EDIT_TEXT))
        assertTrue(FieldPolicy.isLiteral(Kind.EMAIL, "sam"))
        assertTrue(FieldPolicy.isLiteral(Kind.URI, "https"))
        assertTrue(FieldPolicy.isLiteral(Kind.SECRET, ""))
    }

    @Test
    fun `address shaped tokens are literal in prose fields`() {
        assertTrue(FieldPolicy.isLiteral(Kind.PROSE, "mail me at sam@gmail"))
        assertTrue(FieldPolicy.isLiteral(Kind.PROSE, "see https://github"))
        assertTrue(FieldPolicy.isLiteral(Kind.PROSE, "go to www.exa"))
        assertFalse(FieldPolicy.isLiteral(Kind.PROSE, "hey sam"))
        assertFalse(FieldPolicy.isLiteral(Kind.PROSE, "sam@gmail.com "), "a space ends the address")
    }

    @Test
    fun `a leading at sign is a mention not an address`() {
        assertFalse(FieldPolicy.isLiteral(Kind.PROSE, "thanks @sam"))
        assertEquals(emptyList(), labels(Kind.PROSE, "thanks @"))
    }

    @Test
    fun `email local part offers whole mail hosts`() {
        assertEquals(listOf("@gmail.com", "@icloud.com", "@outlook.com", "@yahoo.com"), labels(Kind.EMAIL, "sam.dipaola"))
        assertEquals(emptyList(), labels(Kind.EMAIL, ""))
        assertEquals(emptyList(), labels(Kind.PROSE, "sam"), "prose without @ gets nothing")
    }

    @Test
    fun `after the at sign hosts complete by prefix`() {
        assertEquals(FieldPolicy.MAIL_HOSTS.take(4), labels(Kind.EMAIL, "sam@"))
        assertEquals("gmail.com", labels(Kind.PROSE, "sam@gm").first())
        assertEquals("ail.com", inserts(Kind.PROSE, "sam@gm").first())
        assertTrue(".io" in labels(Kind.PROSE, "sam@work"))
    }

    @Test
    fun `top level domains complete after the dot and stop once complete`() {
        assertEquals(FieldPolicy.TLDS, inserts(Kind.EMAIL, "sam@work."))
        assertEquals(listOf(".com"), labels(Kind.EMAIL, "sam@gmail.c"))
        assertEquals(listOf("om"), inserts(Kind.EMAIL, "sam@gmail.c"))
        assertEquals(emptyList(), labels(Kind.EMAIL, "sam@gmail.com"))
    }

    @Test
    fun `urls get domains while typing the host only`() {
        assertEquals(FieldPolicy.TLDS.map { ".$it" }, labels(Kind.URI, "github"))
        assertEquals(FieldPolicy.TLDS.map { ".$it" }, labels(Kind.PROSE, "https://github"))
        assertEquals(emptyList(), labels(Kind.URI, "github.com/yourowndog"))
        assertEquals(emptyList(), labels(Kind.URI, ""))
        assertEquals(emptyList(), labels(Kind.SECRET, "hunter2"))
    }
}
