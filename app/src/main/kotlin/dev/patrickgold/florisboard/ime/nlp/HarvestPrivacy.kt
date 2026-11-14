package dev.patrickgold.florisboard.ime.nlp

/**
 * Decides whether a text field is too sensitive for the usage harvest.
 *
 * A blocked field writes nothing to usage_harvest.md or usage_harvest.jsonl, so HarvestShipper
 * never sends it to Titan and the keylog dataset never sees it. See
 * docs/autocorrect/keylog-pipeline.md ("Privacy gates").
 *
 * The inputs are plain values so the policy can be unit tested without an EditorInfo.
 */
object HarvestPrivacy {
    /** Why a field was blocked; null means the field may be harvested. */
    enum class Reason { PASSWORD, EMAIL, NON_TEXT, NO_PERSONALIZED_LEARNING, INCOGNITO, SECRET_HINT }

    // Words that mark a credential, one-time code or payment field when the app forgets the
    // password input type. Matched as whole words in the field's hint or label.
    private val secretHint = Regex(
        "\\b(pass(word|code|phrase)?|pwd|pin|otp|one[- ]?time|2fa|mfa|verification code|security code|" +
            "auth(entication)? code|cvv|cvc|card number|credit card|iban|routing|account number|ssn|" +
            "social security|api key|secret|token)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param inputType lowercased [dev.patrickgold.florisboard.ime.editor.InputAttributes.Type] name.
     * @param inputVariation lowercased Variation name.
     * @param noPersonalizedLearning the app set IME_FLAG_NO_PERSONALIZED_LEARNING (incognito tabs,
     *   temporary chats, many banking apps).
     * @param incognito the keyboard's own incognito mode is on for this field.
     */
    fun blockReason(
        inputType: String,
        inputVariation: String,
        noPersonalizedLearning: Boolean,
        incognito: Boolean,
        hint: String?,
        label: String?,
    ): Reason? = when {
        inputVariation in PASSWORD_VARIATIONS -> Reason.PASSWORD
        inputVariation in EMAIL_VARIATIONS -> Reason.EMAIL
        // PINs, one-time codes, card and phone numbers. Typing there teaches the models nothing.
        inputType == "number" || inputType == "phone" || inputType == "datetime" -> Reason.NON_TEXT
        noPersonalizedLearning -> Reason.NO_PERSONALIZED_LEARNING
        incognito -> Reason.INCOGNITO
        listOfNotNull(hint, label).any { secretHint.containsMatchIn(it) } -> Reason.SECRET_HINT
        else -> null
    }

    private val PASSWORD_VARIATIONS = setOf("password", "visible_password", "web_password")
    private val EMAIL_VARIATIONS = setOf("email_address", "web_email_address")
}
