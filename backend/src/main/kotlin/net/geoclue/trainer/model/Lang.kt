package net.geoclue.trainer.model

/**
 * The language a response is rendered in.
 *
 * English is the language the guides are written in, so it is both the default
 * and the fallback: anything Hungarian has no translation for is served in
 * English rather than left blank.
 */
enum class Lang {
    EN,
    HU,
    ;

    /** How the client names it on the wire - an ISO 639-1 code. */
    val wire: String get() = name.lowercase()

    companion object {
        val DEFAULT = EN

        /** Accepts "hu", "HU", "hu-HU"; anything unknown falls back to English. */
        fun parse(value: String?): Lang {
            val code = value?.trim()?.substringBefore('-')?.lowercase() ?: return DEFAULT
            return entries.firstOrNull { it.wire == code } ?: DEFAULT
        }
    }
}
