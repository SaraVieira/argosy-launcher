package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.model.isOpticalDiscModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class PhysicalMediaImage(val url: String, val region: String? = null)

internal fun RomMApiClient.physicalMediaImages(
    rom: RomMRom?,
    model: String,
    indexed: PhysicalMediaImage?
): List<PhysicalMediaImage> {
    val regions = rom?.regions.orEmpty().map(::mediaRegion)
    val isDisc = isOpticalDiscModel(model)
    val isFloppy = model == "floppy35" || model == "floppy525"
    val preferred = if (isDisc || isFloppy) "Disc" else "Cart - Front"
    val launchBox = rom?.launchboxMetadata?.images.orEmpty()
        .filter { it.type.equals(preferred, true) || (model == "psp" && it.type.equals("Cart - Front", true)) }
        .sortedWith(compareBy({ !it.type.equals(preferred, true) }, {
            when (mediaRegion(it.region.orEmpty())) {
                in regions -> 0
                "world", "" -> 1
                else -> 2
            }
        }))
        .map { PhysicalMediaImage(it.url, it.region) }
    val ss = rom?.ssMetadata
    val disc = rom?.discNumber?.takeIf { isDisc || isFloppy }
    val alternate = disc?.let { which -> ss?.physicalExtraDiscs?.find { it.disc == which } }
    val useMain = disc == null || disc == (ss?.physicalDisc ?: 1)
    val path = alternate?.path ?: ss?.physicalPath?.takeIf { useMain }
    val url = alternate?.url ?: ss?.physicalUrl?.takeIf { useMain }
    val local = listOfNotNull(buildResourceUrl(path), buildResourceUrl(rom?.gamelistMetadata?.physicalPath))
        .map { PhysicalMediaImage(it, rom?.regions?.firstOrNull()) }
    return (local + launchBox + listOfNotNull(indexed, url?.let { PhysicalMediaImage(it, rom?.regions?.firstOrNull()) }))
        .filter { it.url.toHttpUrlOrNull() != null }
        .distinctBy { it.url }
}

private fun mediaRegion(region: String): String = when (val value = region.trim().lowercase()) {
    "us", "usa", "united states" -> "north america"
    "eu", "eur" -> "europe"
    "jp", "jpn" -> "japan"
    else -> value
}
