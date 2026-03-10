import java.io.File
import kotlin.math.sqrt

object KeyboardLayout {
    val QWERTY_POSITIONS = mapOf(
        'q' to (0.0 to 0.0), 'w' to (1.0 to 0.0), 'e' to (2.0 to 0.0), 'r' to (3.0 to 0.0),
        't' to (4.0 to 0.0), 'y' to (5.0 to 0.0), 'u' to (6.0 to 0.0), 'i' to (7.0 to 0.0),
        'o' to (8.0 to 0.0), 'p' to (9.0 to 0.0),
        'a' to (0.5 to 1.0), 's' to (1.5 to 1.0), 'd' to (2.5 to 1.0), 'f' to (3.5 to 1.0),
        'g' to (4.5 to 1.0), 'h' to (5.5 to 1.0), 'j' to (6.5 to 1.0), 'k' to (7.5 to 1.0),
        'l' to (8.5 to 1.0),
        'z' to (1.5 to 2.0), 'x' to (2.5 to 2.0), 'c' to (3.5 to 2.0), 'v' to (4.5 to 2.0),
        'b' to (5.5 to 2.0), 'n' to (6.5 to 2.0), 'm' to (7.5 to 2.0),
    )

    fun keyDistance(a: Char, b: Char): Double {
        val aLower = a.lowercaseChar()
        val bLower = b.lowercaseChar()
        if (aLower == bLower) return 0.0
        val posA = QWERTY_POSITIONS[aLower] ?: return 2.0
        val posB = QWERTY_POSITIONS[bLower] ?: return 2.0
        val dx = posA.first - posB.first
        val dy = posA.second - posB.second
        val dist = sqrt(dx * dx + dy * dy)
        return if (dist > 2.0) 2.0 else dist
    }
}

object CandidateScorer {
    fun spatialCost(typed: String, candidate: String): Double {
        var cost = 0.0
        val len = minOf(typed.length, candidate.length)

        var i = 0
        while (i < len) {
            val t = typed[i]
            val c = candidate[i]
            if (t == c) {
                i++
                continue
            }

            if (i + 1 < len && i + 1 < typed.length && i + 1 < candidate.length) {
                val t1 = typed[i + 1]
                val c1 = candidate[i + 1]
                if (t == c1 && t1 == c) {
                    cost += 0.3
                    i += 2
                    continue
                }
            }

            cost += KeyboardLayout.keyDistance(t, c)
            i++
        }

        val diff = Math.abs(typed.length - candidate.length)
        cost += diff * 0.5
        return cost
    }
    
    fun score(typed: String, candidate: String, editDistance: Double): Double {
        var score = editDistance
        score += spatialCost(typed, candidate)
        return score
    }
}

fun main() {
    println("Score tbis->this: " + CandidateScorer.score("tbis", "this", 1.0))
    println("Score agaib->again: " + CandidateScorer.score("agaib", "again", 1.0))
    println("Score tbe->the: " + CandidateScorer.score("tbe", "the", 1.0))
}
