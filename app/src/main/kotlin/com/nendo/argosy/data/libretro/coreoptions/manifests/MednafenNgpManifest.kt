package com.nendo.argosy.data.libretro.coreoptions.manifests

import com.nendo.argosy.data.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.data.libretro.coreoptions.CoreOptionManifest

object MednafenNgpManifest : CoreOptionManifest {
    override val coreId = "mednafen_ngp"
    override val options = listOf(
        CoreOptionDef(
            key = "ngp_language",
            displayName = "Language (Restart)",
            values = listOf("english", "japanese"),
            defaultValue = "english",
            description = "Sets the system language used by compatible games"
        ),
    )
}
