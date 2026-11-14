package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.nlp.HarvestPrivacy.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HarvestPrivacyTest {
    private fun reason(
        inputType: String = "text",
        inputVariation: String = "normal",
        noPersonalizedLearning: Boolean = false,
        incognito: Boolean = false,
        hint: String? = null,
        label: String? = null,
    ) = HarvestPrivacy.blockReason(inputType, inputVariation, noPersonalizedLearning, incognito, hint, label)

    @Test
    fun `ordinary prose fields are harvested`() {
        assertNull(reason(hint = "Message"))
        assertNull(reason(inputVariation = "web_edit_text", hint = "Ask anything"))
        assertNull(reason(inputVariation = "uri", hint = "Search or type URL"))
        assertNull(reason(inputType = "null"))
        // Whole words only: these contain secret words as substrings.
        assertNull(reason(hint = "Type a message about shopping"))
        assertNull(reason(hint = "Compass notes"))
    }

    @Test
    fun `password and email fields stay blocked`() {
        for (v in listOf("password", "visible_password", "web_password")) assertEquals(Reason.PASSWORD, reason(inputVariation = v))
        for (v in listOf("email_address", "web_email_address")) assertEquals(Reason.EMAIL, reason(inputVariation = v))
    }

    @Test
    fun `numeric and phone fields are blocked`() {
        // A numeric password resolves to the password variation first.
        assertEquals(Reason.PASSWORD, reason(inputType = "number", inputVariation = "password"))
        assertEquals(Reason.NON_TEXT, reason(inputType = "number"))
        assertEquals(Reason.NON_TEXT, reason(inputType = "phone"))
        assertEquals(Reason.NON_TEXT, reason(inputType = "datetime", inputVariation = "date"))
    }

    @Test
    fun `apps that ask for no learning are blocked`() {
        assertEquals(Reason.NO_PERSONALIZED_LEARNING, reason(noPersonalizedLearning = true))
        assertEquals(Reason.INCOGNITO, reason(incognito = true))
    }

    @Test
    fun `secret fields with a plain text type are caught by their hint or label`() {
        val hints = listOf(
            "Password", "Enter passcode", "PIN", "Enter OTP", "One-time code", "Verification code",
            "CVV", "Card number", "API key", "Recovery passphrase", "2FA code",
        )
        for (h in hints) assertEquals(Reason.SECRET_HINT, reason(hint = h), h)
        assertEquals(Reason.SECRET_HINT, reason(label = "Security code"))
    }
}
