package com.ngr.app

/** Central registry for target platforms. Availability means a working, tested backend exists. */
object NgrRecompilerCatalog {
    enum class SystemId {
        N64_MIPS, N64_ROM, PS1_MIPS, PS2, PSP, GAMECUBE_WII, DREAMCAST,
        PS3, PS4, NINTENDO_SWITCH, NINTENDO_SWITCH_2, XBOX, XBOX_360, XBOX_ONE, PC_EXE
    }

    data class SystemEntry(
        val id: SystemId,
        val label: String,
        val extensions: Set<String>,
        val available: Boolean
    )

    val systems = listOf(
        SystemEntry(SystemId.N64_MIPS, "Nintendo 64 / MIPS ELF (N64Recomp)", setOf("elf"), true),
        SystemEntry(SystemId.N64_ROM, "Nintendo 64 ROM (.z64 / .n64 / .v64)", setOf("z64", "n64", "v64"), false),
        SystemEntry(SystemId.PS1_MIPS, "PlayStation 1 / MIPS", setOf("cue", "bin", "iso"), false),
        SystemEntry(SystemId.PS2, "PlayStation 2", setOf("iso", "elf"), false),
        SystemEntry(SystemId.PSP, "PlayStation Portable", setOf("iso", "cso", "elf"), false),
        SystemEntry(SystemId.GAMECUBE_WII, "Nintendo GameCube / Wii", setOf("iso", "gcm", "rvz", "elf"), false),
        SystemEntry(SystemId.DREAMCAST, "Sega Dreamcast", setOf("gdi", "cdi", "chd", "elf"), false),
        SystemEntry(SystemId.PS3, "PlayStation 3", setOf("iso", "pkg", "self", "sprx"), false),
        SystemEntry(SystemId.PS4, "PlayStation 4", setOf("pkg", "elf", "self"), false),
        SystemEntry(SystemId.NINTENDO_SWITCH, "Nintendo Switch", setOf("nsp", "xci", "nro", "nso"), false),
        SystemEntry(SystemId.NINTENDO_SWITCH_2, "Nintendo Switch 2", setOf("nsp", "xci", "nro", "nso"), false),
        SystemEntry(SystemId.XBOX, "Xbox (Original)", setOf("iso", "xbe"), false),
        SystemEntry(SystemId.XBOX_360, "Xbox 360", setOf("iso", "xex", "LIVE"), false),
        SystemEntry(SystemId.XBOX_ONE, "Xbox One", setOf("xvc", "pkg", "iso"), false),
        SystemEntry(SystemId.PC_EXE, "PC Windows (EXE)", setOf("exe", "dll"), false)
    )
}
