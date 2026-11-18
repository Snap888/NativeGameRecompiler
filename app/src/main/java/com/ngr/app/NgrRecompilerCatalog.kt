package com.ngr.app

/** Central registry for static game recompilers. A backend becomes selectable only when integrated and tested. */
object NgrRecompilerCatalog {
    enum class SystemId { N64_MIPS, PS1_MIPS, PS2, PSP, GAMECUBE_WII, DREAMCAST }

    data class SystemEntry(
        val id: SystemId,
        val label: String,
        val extensions: Set<String>,
        val available: Boolean
    )

    val systems = listOf(
        SystemEntry(SystemId.N64_MIPS, "Nintendo 64 / MIPS", setOf("elf"), true),
        SystemEntry(SystemId.PS1_MIPS, "PlayStation 1 / MIPS", setOf("cue", "bin", "iso"), false),
        SystemEntry(SystemId.PS2, "PlayStation 2", setOf("iso", "elf"), false),
        SystemEntry(SystemId.PSP, "PlayStation Portable", setOf("iso", "cso", "elf"), false),
        SystemEntry(SystemId.GAMECUBE_WII, "GameCube / Wii", setOf("iso", "gcm", "rvz", "elf"), false),
        SystemEntry(SystemId.DREAMCAST, "Sega Dreamcast", setOf("gdi", "cdi", "chd", "elf"), false)
    )
}
