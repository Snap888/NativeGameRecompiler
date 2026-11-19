package com.ngr.app

/**
 * Header-only format probe for Xbox 360 inputs. This deliberately does not infer a runnable
 * title: a recognized container still needs a matching recipe, runtime and verified title hash.
 */
object Xbox360InputProbe {
    enum class Kind { XEX2, STFS_CON, STFS_LIVE, STFS_PIRS }

    private val candidateExtensions = setOf("iso", "zip", "xex", "con", "live", "pirs")

    /** Container extensions accepted by XDroid360 intake; title support is checked later by its recipe. */
    fun isCandidateFileName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in candidateExtensions

    fun detect(header: ByteArray): Kind? {
        if (header.size < 4) return null
        return when (String(header, 0, 4, Charsets.US_ASCII)) {
            "XEX2" -> Kind.XEX2
            "CON " -> Kind.STFS_CON
            "LIVE" -> Kind.STFS_LIVE
            "PIRS" -> Kind.STFS_PIRS
            else -> null
        }
    }
}
