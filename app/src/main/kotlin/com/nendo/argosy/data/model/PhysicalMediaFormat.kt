package com.nendo.argosy.data.model

internal fun isOpticalDiscModel(model: String): Boolean = when (model) {
    "disc", "psp", "gc_mini_dvd", "dreamcast_gd" -> true
    else -> false
}
