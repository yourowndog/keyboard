package dev.patrickgold.florisboard.ime.keyboard.geometry

/**
 * Stage 00 classification markers for keyboard-geometry characterization tests.
 *
 * Every characterization assertion in this package is classified with exactly one of these, so a
 * later stage can tell at a glance whether it is breaking a promise or fixing a bug.
 *
 * See `omniboard-artifacts/implementation/keyboard-geometry/00-baseline-contracts.md`.
 */

/**
 * Behaviour intentionally preserved through the migration. A later stage that changes this is
 * changing observable product behaviour and must justify it.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class COMPATIBILITY(val reason: String)

/**
 * Evidence of behaviour that is currently wrong and that a later stage is expected to fix.
 *
 * These are deliberately **not** golden requirements: the assertion records what today's code does
 * so the change is visible when it happens, not because the value is correct. A later stage that
 * makes one of these fail should update or delete it, citing the stage that fixed it.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class KNOWN_DEFECT(val reason: String)

/**
 * Persisted input that a future upgrade path must still be able to decode. These pin the wire
 * format of data already on users' devices, not the behaviour derived from it.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class MIGRATION_FIXTURE(val reason: String)

/**
 * A difference between a legacy authority and its replacement that the migration intends.
 *
 * Added in Stage 02 for comparison-mode assertions, where the point of the test is that the two
 * sides disagree. [reason] must name the defect being fixed, so that an unclassified difference —
 * one nobody decided about — cannot pass as an intentional one.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class EXPECTED_FIX(val reason: String)
