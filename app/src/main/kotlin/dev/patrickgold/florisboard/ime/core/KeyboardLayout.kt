package dev.patrickgold.florisboard.ime.core

/**
 * Single source of truth for keyboard layout data (QWERTY adjacency).
 * Used by SymSpellManager.spatialCost() and swipe gesture scoring.
 */
object KeyboardLayout {
    
    /**
     * QWERTY key adjacency map.
     * Each key maps to a string of its neighboring keys.
     */
    val QWERTY_NEIGHBORS: Map<Char, String> = mapOf(
        'q' to "wa", 
        'w' to "qase", 
        'e' to "wsdfr", 
        'r' to "edft", 
        't' to "rfgy", 
        'y' to "tghu", 
        'u' to "yhij", 
        'i' to "ujko", 
        'o' to "iklp", 
        'p' to "ol",
        'a' to "qwsz", 
        's' to "qweadzx", 
        'd' to "ersfcx", 
        'f' to "rtdgcv", 
        'g' to "tyfhvb", 
        'h' to "yugjbn", 
        'j' to "uikhnm", 
        'k' to "iojlm", 
        'l' to "opk",
        'z' to "asx", 
        'x' to "zsdc", 
        'c' to "xdfv", 
        'v' to "cfgb", 
        'b' to "vghn", 
        'n' to "bhjm", 
        'm' to "njk"
    )

    /**
     * Check if two keys are adjacent on QWERTY layout.
     */
    fun isAdjacent(a: Char, b: Char): Boolean {
        return QWERTY_NEIGHBORS[a.lowercaseChar()]?.contains(b.lowercaseChar()) == true
    }

    /**
     * Get spatial cost between two characters.
     * @return 0.0 if same, 0.5 if adjacent, 2.0 if far
     */
    fun keyDistance(a: Char, b: Char): Double {
        val aLower = a.lowercaseChar()
        val bLower = b.lowercaseChar()
        return when {
            aLower == bLower -> 0.0
            isAdjacent(aLower, bLower) -> 0.5
            else -> 2.0
        }
    }
}
