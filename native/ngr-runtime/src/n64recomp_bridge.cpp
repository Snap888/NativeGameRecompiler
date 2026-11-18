#include <jni.h>
#include <filesystem>
#include <sstream>
#include <unordered_map>
#include "recompiler/context.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_ngr_app_N64RecompilerActivity_nativeInspectN64Elf(JNIEnv* env, jobject, jstring inputPath) {
    if (inputPath == nullptr) return env->NewStringUTF("ERROR: missing ELF path");
    const char* raw = env->GetStringUTFChars(inputPath, nullptr);
    if (raw == nullptr) return env->NewStringUTF("ERROR: cannot read path");
    const std::filesystem::path path(raw);
    env->ReleaseStringUTFChars(inputPath, raw);

    std::error_code ec;
    const auto size = std::filesystem::file_size(path, ec);
    if (ec || size < 64 || size > (512ull * 1024ull * 1024ull))
        return env->NewStringUTF("ERROR: ELF size is invalid (expected 64 B..512 MiB)");

    N64Recomp::Context context{};
    N64Recomp::ElfParsingConfig flags{};
    flags.bss_section_suffix = ".bss";
    flags.has_entrypoint = false;
    flags.entrypoint_address = 0;
    flags.use_absolute_symbols = false;
    flags.unpaired_lo16_warnings = false;
    flags.all_sections_relocatable = false;
    flags.use_mdebug = false;
    N64Recomp::DataSymbolMap dataSymbols{};
    bool foundEntrypoint = false;
    try {
        if (!N64Recomp::Context::from_elf_file(path, context, flags, false, dataSymbols, foundEntrypoint))
            return env->NewStringUTF("ERROR: N64Recomp could not parse this ELF. Check that it is a supported MIPS/N64 ELF with symbols.");
    } catch (const std::exception& e) {
        std::string message = std::string("ERROR: ELF parser exception: ") + e.what();
        return env->NewStringUTF(message.c_str());
    }

    size_t executableSections = 0;
    size_t relocations = 0;
    for (const auto& section : context.sections) {
        if (section.executable) ++executableSections;
        relocations += section.relocs.size();
    }
    std::ostringstream report;
    report << "OK\\nN64Recomp ELF analysis complete\\n"
           << "Sections: " << context.sections.size() << "\\n"
           << "Executable sections: " << executableSections << "\\n"
           << "Functions: " << context.functions.size() << "\\n"
           << "Relocations: " << relocations << "\\n"
           << "Entrypoint symbol: " << (foundEntrypoint ? "found" : "not configured") << "\\n"
           << "Next: generate C and link the N64 title runtime.";
    return env->NewStringUTF(report.str().c_str());
}
