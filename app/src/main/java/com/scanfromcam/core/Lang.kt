package com.scanfromcam.core

import java.util.Locale

/** The two languages the app ships in. */
enum class Lang(val tag: String) {
    ES("es"),
    EN("en");

    val locale: Locale
        get() = when (this) {
            ES -> Locale.forLanguageTag("es-ES")
            EN -> Locale.US
        }

    companion object {
        /**
         * Spanish only on Spanish (Spain) devices, English everywhere else.
         * The user can override it later from Settings.
         */
        fun defaultFor(device: Locale = Locale.getDefault()): Lang =
            if (device.language == "es" && (device.country.isEmpty() || device.country == "ES")) ES else EN

        fun fromTag(tag: String?): Lang = Lang.entries.firstOrNull { it.tag == tag } ?: EN
    }
}
