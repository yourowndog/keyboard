package dev.patrickgold.florisboard.ime.nlp.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractionRulesTest {
    @Test
    fun staticShortcutResolutionIssuesAnExactOpaqueLicense() {
        val resolution = assertNotNull(ContractionRules.resolveStatic("DONT"))

        assertEquals("don't", resolution.candidate)
        assertEquals(ContractionLicenseKind.STATIC_RULE, resolution.license.kind)
        assertEquals("dont", resolution.license.normalizedTyped)
        assertEquals("don't", resolution.license.rawCandidate)
        assertEquals(CandidateProvenance.CONTRACTION_RULE, resolution.license.provenance)
        assertTrue(
            ContractionRules.isValidLicense(
                typed = "DONT",
                rawCandidate = "don't",
                provenance = CandidateProvenance.CONTRACTION_RULE,
                license = resolution.license,
            ),
        )

        assertNull(ContractionRules.resolveStatic("were"))
        assertNull(ContractionRules.resolveStatic("its"))
        assertNull(ContractionRules.resolveStatic("I"))
    }

    @Test
    fun staticLicenseFailsClosedForEveryMismatchedPairOrProvenance() {
        val dont = assertNotNull(ContractionRules.resolveStatic("dont"))
        val cant = assertNotNull(ContractionRules.resolveStatic("cant"))

        assertFalse(ContractionRules.isValidLicense("dont", "can't", CandidateProvenance.CONTRACTION_RULE, dont.license))
        assertFalse(ContractionRules.isValidLicense("cant", "don't", CandidateProvenance.CONTRACTION_RULE, dont.license))
        assertFalse(ContractionRules.isValidLicense("dont", "don't", CandidateProvenance.PREFIX_COMPLETION, dont.license))
        assertFalse(ContractionRules.isValidLicense("dont", "don't", CandidateProvenance.EDIT_DISTANCE, dont.license))
        assertFalse(ContractionRules.isValidLicense("dont", "don't", CandidateProvenance.CONTRACTION_RULE, cant.license))
        assertFalse(ContractionRules.isValidLicense("dont", "don't", CandidateProvenance.CONTRACTION_RULE, null))
    }
}
