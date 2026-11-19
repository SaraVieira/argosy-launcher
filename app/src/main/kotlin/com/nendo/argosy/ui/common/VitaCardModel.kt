package com.nendo.argosy.ui.common

private val cardTitles = setOf(
    "child of light", "code realize future blessings", "code realize guardian of rebirth",
    "final fantasy x hd remaster", "gravity rush", "gundam breaker 3", "gundam breaker 3 break edition",
    "jak and daxter collection", "killzone mercenary", "persona 4 golden",
    "ratchet clank collection", "the ratchet clank trilogy", "rayman legends", "rayman origins",
    "risk of rain", "spelunky", "steins gate", "steins gate 0", "tales from space mutant blobs attack",
    "tearaway", "velocity 2x", "zero escape virtues last reward", "zero escape zero time dilemma"
)
private val editionTags = Regex("""[\[(][^()\[\]]+[\])]""")
private val digitalTag = Regex("""(?i)[(\[](?:PSN|digital|download(?: only)?|demo)[)\]]""")
private val separators = Regex("""[^a-z0-9]+""")

internal fun vitaCardModel(title: String?, fileName: String?, localPath: String?): String? {
    val files = listOfNotNull(fileName, localPath).map { it.substringAfterLast('/').substringAfterLast('\\') }
    if (files.any { it.endsWith(".pkg", true) || digitalTag.containsMatchIn(it) } ||
        title?.let(digitalTag::containsMatchIn) == true) return null
    if (files.any { it.endsWith(".psv", true) }) return "vita"
    val names = listOfNotNull(title) + files.map { it.substringBeforeLast('.') }
    return "vita".takeIf {
        names.any { name ->
            val key = name.lowercase().replace(editionTags, "").replace("'", "").replace("’", "")
                .replace(separators, " ").trim()
            key in cardTitles
        }
    }
}
