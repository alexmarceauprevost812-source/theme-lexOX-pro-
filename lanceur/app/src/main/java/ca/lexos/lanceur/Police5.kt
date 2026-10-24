package ca.lexos.lanceur

import java.text.Normalizer

/**
 * Police « blocs » 5×5 du terminal LexOS PRO v4 : transforme un mot
 * en 5 lignes de █, comme le gros logo LEXOS du terminal.
 * (La même police existe en JavaScript dans assets/www/index.html.)
 */
object Police5 {
    private val G: Map<Char, Array<String>> = mapOf(
        'A' to arrayOf(" ### ", "#   #", "#####", "#   #", "#   #"),
        'B' to arrayOf("#### ", "#   #", "#### ", "#   #", "#### "),
        'C' to arrayOf(" ####", "#    ", "#    ", "#    ", " ####"),
        'D' to arrayOf("#### ", "#   #", "#   #", "#   #", "#### "),
        'E' to arrayOf("#####", "#    ", "#### ", "#    ", "#####"),
        'F' to arrayOf("#####", "#    ", "#### ", "#    ", "#    "),
        'G' to arrayOf(" ####", "#    ", "#  ##", "#   #", " ####"),
        'H' to arrayOf("#   #", "#   #", "#####", "#   #", "#   #"),
        'I' to arrayOf("#####", "  #  ", "  #  ", "  #  ", "#####"),
        'J' to arrayOf("#####", "   # ", "   # ", "#  # ", " ##  "),
        'K' to arrayOf("#   #", "#  # ", "###  ", "#  # ", "#   #"),
        'L' to arrayOf("#    ", "#    ", "#    ", "#    ", "#####"),
        'M' to arrayOf("#   #", "## ##", "# # #", "#   #", "#   #"),
        'N' to arrayOf("#   #", "##  #", "# # #", "#  ##", "#   #"),
        'O' to arrayOf(" ### ", "#   #", "#   #", "#   #", " ### "),
        'P' to arrayOf("#### ", "#   #", "#### ", "#    ", "#    "),
        'Q' to arrayOf(" ### ", "#   #", "# # #", "#  # ", " ## #"),
        'R' to arrayOf("#### ", "#   #", "#### ", "#  # ", "#   #"),
        'S' to arrayOf(" ####", "#    ", " ### ", "    #", "#### "),
        'T' to arrayOf("#####", "  #  ", "  #  ", "  #  ", "  #  "),
        'U' to arrayOf("#   #", "#   #", "#   #", "#   #", " ### "),
        'V' to arrayOf("#   #", "#   #", "#   #", " # # ", "  #  "),
        'W' to arrayOf("#   #", "#   #", "# # #", "## ##", "#   #"),
        'X' to arrayOf("#   #", " # # ", "  #  ", " # # ", "#   #"),
        'Y' to arrayOf("#   #", " # # ", "  #  ", "  #  ", "  #  "),
        'Z' to arrayOf("#####", "   # ", "  #  ", " #   ", "#####"),
        '0' to arrayOf(" ### ", "#  ##", "# # #", "##  #", " ### "),
        '1' to arrayOf("  #  ", " ##  ", "  #  ", "  #  ", " ### "),
        '2' to arrayOf(" ### ", "#   #", "  ## ", " #   ", "#####"),
        '3' to arrayOf("#### ", "    #", " ### ", "    #", "#### "),
        '4' to arrayOf("#   #", "#   #", "#####", "    #", "    #"),
        '5' to arrayOf("#####", "#    ", "#### ", "    #", "#### "),
        '6' to arrayOf(" ### ", "#    ", "#### ", "#   #", " ### "),
        '7' to arrayOf("#####", "   # ", "  #  ", " #   ", "#    "),
        '8' to arrayOf(" ### ", "#   #", " ### ", "#   #", " ### "),
        '9' to arrayOf(" ### ", "#   #", " ####", "    #", " ### "),
        '-' to arrayOf("     ", "     ", "#####", "     ", "     "),
        '+' to arrayOf("     ", "  #  ", " ### ", "  #  ", "     "),
        ' ' to arrayOf("   ", "   ", "   ", "   ", "   ")
    )

    /** Garde le premier mot (prénom), sans accents, 9 caractères max. */
    fun court(nom: String): String {
        val sans = Normalizer.normalize(nom, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val mot = sans.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() } ?: sans.trim()
        return mot.uppercase().filter { G.containsKey(it) }.take(9).ifEmpty { "?" }
    }

    fun lignes(mot: String): List<String> {
        val out = MutableList(5) { StringBuilder() }
        mot.forEachIndexed { i, c ->
            val g = G[c] ?: G[' ']!!
            for (r in 0 until 5) {
                if (i > 0) out[r].append(' ')
                out[r].append(g[r])
            }
        }
        return out.map { it.toString().replace('#', '█') }
    }
}
