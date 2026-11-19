package com.nendo.argosy.ui.common

import com.nendo.argosy.data.platform.PlatformDefinitions

private val regionTag = Regex("""[\(\[]([^()\[\]]+)[\)\]]""")
private val superFamicomSlugs = setOf("sfam", "sfc", "superfamicom", "super_famicom", "super-famicom")
private val downloadOnlyPlatforms = setOf("psp-minis", "psp_minis", "pspminis", "playstation-portable-minis", "playstation_portable_minis")

internal fun physicalMediaModelFor(
    platformSlug: String,
    regions: String? = null,
    fileName: String? = null,
    title: String? = null,
    fileSizeBytes: Long? = null,
    localPath: String? = null,
    sha1Hash: String? = null
): String? {
    val raw = platformSlug.lowercase()
    if (raw in downloadOnlyPlatforms) return null
    val platform = PlatformDefinitions.getCanonicalSlug(raw)
    if (sha1Hash != null) verifiedArchiveModel(platform, sha1Hash, fileName, localPath, title)?.let { return it }
    return when (platform) {
        "atari2600", "gba", "gb", "gbc", "n64", "3ds", "switch", "sms", "gg", "tg16", "psp" -> platform
        "arcade", "cps1", "cps2", "cps3" -> "arcade"
        "atari7800" -> "atari2600"
        "nes" -> nesShell(raw, regions, fileName ?: localPath, title)
        "gc" -> "gc_mini_dvd"
        "dreamcast" -> "dreamcast_gd"
        "pico8" -> "pico8".takeIf { listOfNotNull(fileName, localPath).any { it.endsWith(".png", ignoreCase = true) } }
        "nds", "dsi" -> "ds"
        "genesis" -> "md"
        "supergrafx" -> "tg16"
        "snes" -> snesShell(raw, regions, fileName ?: localPath, title)
        "psx", "ps2", "ps3", "ps4", "ps5", "wii", "wiiu", "xbox", "xbox360", "xboxone",
        "xboxseriesx", "scd", "saturn", "tgcd", "pcfx", "neogeocd", "jaguarcd",
        "amigacd32", "cdtv", "cdi", "3do" -> "disc"
        "zx" -> spectrumModel(fileName, localPath, title)
        "c64" -> commodore64Model(fileName, localPath, title)
        "scummvm" -> listOfNotNull(fileName, localPath, title).firstNotNullOfOrNull {
            mediaEdition(it.substringAfterLast('/').substringAfterLast('\\'))
        }
        "x68000" -> {
            val compact = compactEdition.containsMatchIn(raw)
            listOfNotNull(fileName, localPath).firstNotNullOfOrNull { path ->
                x68000Model(path, compact)
            }
        }
        "amiga", "atari8bit", "c128", "vic20", "plus4", "cpet", "msx", "msx2", "dos", "amstradcpc" ->
            floppyModelFor(platform, fileName ?: localPath, fileSizeBytes)
                ?: localPath?.takeIf { fileName != null && it != fileName }?.let { floppyModelFor(platform, it, null) }
                ?: taggedCassetteFor(platform, fileName, localPath, title)
                ?: msxArchiveCartridge(platform, fileName, localPath)
                ?: dosArchiveDefault(platform, fileName ?: localPath, localPath)
        else -> null
    }
}

private val msx720KiBDiskSha1 = setOf(
    "066fd0e055112954b394424aa1a00716900e6ba8",
    "105317da4cad0b47b55389eba099384f39606720",
    "475fd7261c660a0fb22bc2ac6d8f13623e9decb5",
    "64a79efb84d42248666a53ae80b8d267fdc7bb4f",
    "95006f046f328f76a606d341aa46e0acaec499b2",
    "d8150e889b32275e7dd2b0e42d2a2769c6e57053",
    "fa3b5323829b596ef4ea94cd95f4501cc2fe72ca"
)

private val spectrumTrDosDiskSha1 = setOf(
    "04a391e166bf0a5dbea219321486e86233032ac9",
    "1310e1a838b54b53aee65b693e52065f501db9c7",
    "156b55fa245f97e2882efbe255533599fa062df6",
    "268cc1830f551a68c30cb8ffc62a052bacc04ed1",
    "27f6d042a59931c8fb0499637af6a00079731ce8",
    "4b3a4c327ccf6dd55d32ee4b97c9af0bceacbeb0",
    "63a7eab80c01824375a8891ea82e96c8b7b2f89d",
    "8b51197edf36b0cee13a1edff56ba582cc68d356",
    "adc9f070c11d697c7fdc346f7db74e4a2f393e3f",
    "ae1be5d9a3a7238d1a4016643fe410b9aaaa43d5",
    "c7fc8e4d605e70b5a1f76fb7cb580de06a116920"
)

private fun verifiedArchiveModel(
    platform: String,
    sha1Hash: String,
    fileName: String?,
    localPath: String?,
    title: String?
): String? {
    val default = when (platform) {
        "zx" -> if (sha1Hash.lowercase() in spectrumTrDosDiskSha1) "floppy525" else return null
        "msx", "msx2" -> if (sha1Hash.lowercase() in msx720KiBDiskSha1) "floppy35" else return null
        else -> return null
    }
    val names = listOfNotNull(fileName, localPath).map(::mediaName)
    if (names.isEmpty() || names.any { it.substringAfterLast('.') !in diskArchiveSuffixes }) return null
    return (names + listOfNotNull(title)).firstNotNullOfOrNull(::explicitFloppySize) ?: default
}

private val diskArchiveSuffixes = setOf("zip", "7z", "gz")
private val floppySizeTag = Regex(
    """(?<![\p{L}\d.])(3[.]5|5[.]25)\s*[- ]?(?:inch(?:es)?|["″]|floppy|disk(?:ette)?)(?![\p{L}\d])""",
    RegexOption.IGNORE_CASE
)

private val opticalEdition = Regex("""(?i)(?<![a-z0-9])(?:cd(?:[- ]?rom)?|dvd(?:[- ]?rom)?)(?![a-z0-9])""")
private val floppyEdition = Regex("""(?i)(?<![a-z0-9])floppy(?![a-z0-9])""")
private val compactEdition = Regex("""(?i)(?<![a-z0-9])compact(?![a-z0-9])""")

private fun x68000Model(path: String, compact: Boolean): String? {
    val name = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
    val disk = if (name.substringAfterLast('.') in diskArchiveSuffixes) name.substringBeforeLast('.') else name
    if (disk.substringAfterLast('.') !in setOf("dim", "xdf", "m3u")) return null
    if (disk.endsWith(".m3u") && opticalEdition.containsMatchIn(disk)) return null
    return explicitFloppySize(disk) ?: if (compact || compactEdition.containsMatchIn(disk)) "floppy35" else "floppy525"
}

private fun mediaEdition(name: String): String? = when {
    opticalEdition.containsMatchIn(name) -> "disc"
    floppySizeTag.containsMatchIn(name) -> explicitFloppySize(name)
    floppyEdition.containsMatchIn(name) -> "floppy35"
    else -> null
}

private fun explicitFloppySize(name: String): String? =
    floppySizeTag.find(name)?.let { if (it.groupValues[1] == "3.5") "floppy35" else "floppy525" }

private fun msxArchiveCartridge(platform: String, fileName: String?, localPath: String?): String? {
    if (platform != "msx" && platform != "msx2") return null
    val names = listOfNotNull(fileName, localPath).map(::mediaName)
    val archived = names.isNotEmpty() && names.all { it.substringAfterLast('.') in diskArchiveSuffixes }
    if (!archived || names.any { unwrappedName(it).substringAfterLast('.') in nonCassetteExtensions }) return null
    return "msx"
}

private fun dosArchiveDefault(platform: String, fileName: String?, localPath: String?): String? {
    if (platform != "dos") return null
    val names = listOfNotNull(fileName, localPath)
    if (names.any { it.endsWith(".ima", true) || it.endsWith(".img", true) }) return null
    return "floppy35".takeIf { names.any { it.endsWith(".zip", true) || it.endsWith(".7z", true) || it.endsWith(".dosz", true) } }
}

private val famicomSlugs = setOf("famicom", "fc", "fam", "family_computer", "family-computer")
private val japaneseRegions = setOf("japan", "jp", "jpn", "j")
private val otherMarkets = setOf(
    "usa", "us", "u", "united states", "north america", "canada", "brazil", "world", "w", "europe", "eu", "eur", "e",
    "asia", "korea", "china", "taiwan", "australia", "france", "germany", "spain", "italy", "united kingdom"
)

private fun nesShell(slug: String, regions: String?, fileName: String?, title: String?): String {
    if (slug in famicomSlugs) return "famicom_cart"
    val markets = regions.orEmpty().lowercase().split(',').map(String::trim).filterNot { it.isEmpty() || it == "unlicensed" }
    if (markets.isNotEmpty()) return if (markets.all { it in japaneseRegions }) "famicom_cart" else "nes"
    val names = listOfNotNull(fileName?.substringAfterLast('/'), title)
    val tags = names.flatMap { regionTag.findAll(it).map { match -> match.groupValues[1].lowercase() } }
    val tagValues = tags.map { it.split(',').map(String::trim) }
    if (tagValues.any { values -> values.any { it in otherMarkets } }) return "nes"
    return if (tagValues.any { values -> values.all { it in japaneseRegions } } || names.any { it.startsWith("Famicom ", true) }) {
        "famicom_cart"
    } else "nes"
}

private val cassetteTag = Regex(
    """(?i)(?:[\[(]\s*(?:audio[ -])?(?:cassette|tape)(?:[ -](?:version|edition|release|side[ -]?[ab12]|[ab12]))?\s*[\])]|\b(?:cassette|tape)[ -](?:version|edition|release)\b)"""
)
private val spectrumDiskTag = Regex(
    """(?i)(?:[\[(]\s*(?:\+3[ -])?(?:disk|disc|floppy)(?:[ -](?:version|edition|release|[1-9](?: of [1-9])?))?\s*[\])]|\b(?:disk|disc|floppy)[ -](?:version|edition|release)\b|\btr[- ]?dos\b|\+3dos\b)"""
)
private val spectrumDiskExtensions = setOf("dsk", "edsk", "trd", "scl", "fdi", "udi", "mgt", "img", "ipf")
private val spectrumCassetteDefaults = diskArchiveSuffixes + setOf("sna", "z80", "szx", "slt")
private val spectrumTapeExtensions = setOf("tap", "tzx", "pzx", "csw")
private val cassettePlatforms = setOf("c64", "c128", "vic20", "plus4", "cpet", "msx", "msx2", "atari8bit", "amstradcpc")
private val nonCassetteExtensions = setOf(
    "pcm", "wav", "mp3", "flac", "ogg", "dck", "rom", "crt", "car", "mx1", "mx2", "prg", "p00", "cfg",
    "mdr", "hdf", "dsk", "atr"
)

private fun mediaName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
private fun unwrappedName(name: String): String =
    if (name.substringAfterLast('.') in diskArchiveSuffixes) name.substringBeforeLast('.') else name

private fun spectrumModel(fileName: String?, localPath: String?, title: String?): String? {
    val names = listOfNotNull(fileName, localPath).map(::mediaName)
    for (name in names) {
        val inner = unwrappedName(name)
        val ext = inner.substringAfterLast('.', "")
        when {
            ext in spectrumTapeExtensions -> return "cassette"
            ext in spectrumDiskExtensions -> return explicitFloppySize(inner) ?: title?.let(::explicitFloppySize)
                ?: "floppy525".takeIf { ext == "trd" || ext == "scl" }
            ext in nonCassetteExtensions -> return null
        }
    }
    val labels = names + listOfNotNull(title)
    labels.firstNotNullOfOrNull(::explicitFloppySize)?.let { return it }
    if (labels.any(spectrumDiskTag::containsMatchIn)) return null
    if (labels.any(cassetteTag::containsMatchIn)) return "cassette"
    return "cassette".takeIf { names.any { it.substringAfterLast('.') in spectrumCassetteDefaults } }
}

private fun taggedCassetteFor(platform: String, fileName: String?, localPath: String?, title: String?): String? {
    if (platform !in cassettePlatforms) return null
    val names = listOfNotNull(fileName, localPath).map(::mediaName)
    if (names.any { unwrappedName(it).substringAfterLast('.') in nonCassetteExtensions }) return null
    return "cassette".takeIf { (names + listOfNotNull(title)).any(cassetteTag::containsMatchIn) }
}

private val c64NonDiskExtensions = setOf("crt", "prg", "p00", "vsf", "pcm", "wav")

private fun commodore64Model(fileName: String?, localPath: String?, title: String?): String? {
    val names = listOfNotNull(fileName, localPath).map(::mediaName)
    for (name in names) {
        floppyModelFor("c64", name, null)?.let { return it }
        val inner = unwrappedName(name)
        if (inner.substringAfterLast('.', "") in c64NonDiskExtensions) return null
    }
    taggedCassetteFor("c64", fileName, localPath, title)?.let { return it }
    val archive = names.firstOrNull { it.endsWith(".7z") } ?: return null
    return if (floppySizeTag.find(archive)?.groupValues?.get(1) == "3.5") "floppy35" else "floppy525"
}

private fun floppyModelFor(platform: String, path: String?, sizeBytes: Long?): String? {
    val name = path?.substringAfterLast('/')?.substringAfterLast('\\') ?: return null
    val outerExtension = name.substringAfterLast('.', "").lowercase()
    val archived = outerExtension in diskArchiveSuffixes
    val diskName = if (archived) name.substringBeforeLast('.') else name
    val extension = diskName.substringAfterLast('.', "").lowercase().takeIf { it.all(Char::isLetterOrDigit) }.orEmpty()
    val explicitSize = explicitFloppySize(diskName)
    val rawSize = sizeBytes.takeUnless { archived }
    return when (platform) {
        "amiga" -> when (extension) {
            "lha", "adf", "adz", "dms", "ipf" -> explicitSize ?: "floppy35"
            "m3u" -> mediaEdition(diskName) ?: "floppy35"
            else -> if (archived) mediaEdition(diskName) else null
        }
        "c64", "c128", "vic20", "plus4", "cpet" -> when (extension) {
            "tap", "t64" -> "cassette"
            "d64", "g64", "d67", "d71", "g71", "d80", "d82" -> "floppy525"
            "d81" -> "floppy35"
            else -> null
        }
        "atari8bit" -> when (extension) {
            "cas" -> "cassette"
            "atr", "xex" -> explicitSize ?: "floppy525"
            else -> null
        }
        "dos" -> when {
            extension in setOf("iso", "cue", "chd") -> "disc"
            archived -> mediaEdition(diskName)
            extension !in setOf("ima", "img") -> null
            explicitSize != null -> explicitSize
            rawSize in dosFloppy525Sizes -> "floppy525"
            rawSize in dosFloppy35Sizes -> "floppy35"
            else -> null
        }
        "msx", "msx2" -> when {
            extension in setOf("rom", "mx1", "mx2") -> "msx"
            extension == "cas" -> "cassette"
            extension == "m3u" -> explicitSize ?: "floppy35"
            extension != "dsk" -> if (archived && extension !in setOf("rom", "mx1", "mx2", "cas")) mediaEdition(diskName) else null
            explicitSize != null -> explicitSize
            rawSize == 720L * 1024 -> "floppy35"
            else -> null
        }
        "amstradcpc" -> when (extension) {
            "cdt", "tzx" -> "cassette"
            "dsk" -> explicitSize
            else -> null
        }
        else -> null
    }
}

private val dosFloppy525Sizes = setOf(160, 180, 200, 320, 360, 400, 1200).map { it * 1024L }.toSet()
private val dosFloppy35Sizes = setOf(720, 1440, 2880).map { it * 1024L }.toSet()

private fun snesShell(slug: String, regions: String?, fileName: String?, title: String?): String {
    fun shell(region: String): String? = when (region.trim().lowercase()) {
        "usa", "us", "u", "north america", "united states", "canada", "brazil", "br", "ntsc-u" -> "snes"
        "europe", "eu", "eur", "e", "pal", "australia", "au", "new zealand",
        "japan", "jp", "jpn", "j", "ntsc-j", "korea", "kr", "kor", "south korea",
        "france", "germany", "italy", "spain", "uk", "united kingdom", "sweden", "netherlands" -> "snes-pal"
        else -> null
    }

    val metadataShell = regions?.split(',')?.firstNotNullOfOrNull(::shell)
    if (metadataShell != null) return metadataShell
    val taggedShell = sequenceOf(fileName?.substringAfterLast('/'), title).filterNotNull()
        .flatMap { name -> regionTag.findAll(name).map { it.groupValues[1] } }
        .flatMap { tag -> tag.split(',').asSequence() }
        .firstNotNullOfOrNull(::shell)
    if (taggedShell != null) return taggedShell
    if (slug in superFamicomSlugs) return "snes-pal"
    return shell(slug.substringAfterLast('-').substringAfterLast('_')) ?: "snes"
}
