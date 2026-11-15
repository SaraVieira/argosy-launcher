package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.platform.PlatformDefinitions

internal object Pico8CartFiles {
    private const val CART_IMAGE_SUFFIX = ".p8.png"

    fun isCartImage(fileName: String, parentDirName: String): Boolean =
        fileName.lowercase().endsWith(CART_IMAGE_SUFFIX) ||
            PlatformDefinitions.getCanonicalSlug(parentDirName) == "pico8"

    fun titleStem(fileName: String): String =
        if (fileName.lowercase().endsWith(CART_IMAGE_SUFFIX)) {
            fileName.dropLast(CART_IMAGE_SUFFIX.length)
        } else {
            fileName.substringBeforeLast('.')
        }
}
