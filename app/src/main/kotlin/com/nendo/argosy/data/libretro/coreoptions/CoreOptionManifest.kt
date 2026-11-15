package com.nendo.argosy.data.libretro.coreoptions

interface CoreOptionManifest {
    val coreId: String
    val options: List<CoreOptionDef>
}
