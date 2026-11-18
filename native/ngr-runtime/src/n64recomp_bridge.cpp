#include <jni.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <exception>
#include <fstream>
#include <set>
#include <sstream>
#include <string>
#include <unordered_set>
#include <vector>

#include "recompiler/context.h"

namespace {

constexpr uint64_t kMaxElfBytes = 512ull * 1024ull * 1024ull;
constexpr size_t kMaxFunctions = 500000;
constexpr uint64_t kMaxInstructions = 64ull * 1024ull * 1024ull;

std::string json_escape(const std::string& value) {
    std::string out;
    out.reserve(value.size() + 8);
    for (unsigned char ch : value) {
        switch (ch) {
        case '\\': out += "\\\\"; break;
        case '"': out += "\\\""; break;
        case '\n': out += "\\n"; break;
        case '\r': out += "\\r"; break;
        case '\t': out += "\\t"; break;
        default:
            if (ch < 0x20) out += '?';
            else out += static_cast<char>(ch);
        }
    }
    return out;
}

std::string unique_identifier(const std::string& input, std::unordered_set<std::string>& used) {
    std::string base;
    base.reserve(input.size() + 8);
    for (unsigned char ch : input) {
        base.push_back((std::isalnum(ch) || ch == '_') ? static_cast<char>(ch) : '_');
    }
    if (base.empty() || !(std::isalpha(static_cast<unsigned char>(base[0])) || base[0] == '_')) {
        base.insert(base.begin(), '_');
    }
    std::string candidate = base;
    size_t suffix = 1;
    while (!used.insert(candidate).second) {
        candidate = base + "_" + std::to_string(suffix++);
    }
    return candidate;
}

bool write_recompiled_function(
    const N64Recomp::Context& context,
    size_t functionIndex,
    const std::filesystem::path& output,
    std::vector<std::vector<uint32_t>>& staticFunctions,
    std::string& error) {
    std::ofstream file(output, std::ios::binary | std::ios::trunc);
    if (!file) {
        error = "Cannot create generated source: " + output.string();
        return false;
    }
    file << "#include \"funcs.h\"\n\n";
    if (!N64Recomp::recompile_function(context, functionIndex, file, staticFunctions, false)) {
        error = "N64Recomp failed while translating function: " + context.functions[functionIndex].name;
        file.close();
        return false;
    }
    file.flush();
    if (!file.good()) {
        error = "Failed writing generated source: " + output.string();
        return false;
    }
    return true;
}

jstring result_string(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_ngr_app_N64RecompilerActivity_nativeInspectN64Elf(JNIEnv* env, jobject, jstring inputPath) {
    if (inputPath == nullptr) return result_string(env, "ERROR: missing ELF path");
    const char* raw = env->GetStringUTFChars(inputPath, nullptr);
    if (raw == nullptr) return result_string(env, "ERROR: cannot read path");
    const std::filesystem::path path(raw);
    env->ReleaseStringUTFChars(inputPath, raw);

    std::error_code ec;
    const auto size = std::filesystem::file_size(path, ec);
    if (ec || size < 64 || size > kMaxElfBytes)
        return result_string(env, "ERROR: ELF size is invalid (expected 64 B..512 MiB)");

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
            return result_string(env, "ERROR: N64Recomp could not parse this ELF. A supported MIPS/N64 ELF with symbols is required.");
    } catch (const std::exception& e) {
        return result_string(env, std::string("ERROR: ELF parser exception: ") + e.what());
    }

    size_t executableSections = 0;
    size_t relocations = 0;
    for (const auto& section : context.sections) {
        if (section.executable) ++executableSections;
        relocations += section.relocs.size();
    }
    std::ostringstream report;
    report << "OK\nN64Recomp ELF analysis complete\n"
           << "Sections: " << context.sections.size() << "\n"
           << "Executable sections: " << executableSections << "\n"
           << "Functions: " << context.functions.size() << "\n"
           << "Relocations: " << relocations << "\n"
           << "Entrypoint symbol: " << (foundEntrypoint ? "found" : "not configured") << "\n"
           << "Next: generate C and provide a compatible N64 title runtime.";
    return result_string(env, report.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ngr_app_N64RecompilerActivity_nativeGenerateN64C(JNIEnv* env, jobject, jstring inputPath, jstring outputPath) {
    if (inputPath == nullptr || outputPath == nullptr) return result_string(env, "ERROR: input or output path is missing");
    const char* inputRaw = env->GetStringUTFChars(inputPath, nullptr);
    const char* outputRaw = env->GetStringUTFChars(outputPath, nullptr);
    if (inputRaw == nullptr || outputRaw == nullptr) {
        if (inputRaw) env->ReleaseStringUTFChars(inputPath, inputRaw);
        if (outputRaw) env->ReleaseStringUTFChars(outputPath, outputRaw);
        return result_string(env, "ERROR: cannot read input/output path");
    }
    const std::filesystem::path input(inputRaw);
    const std::filesystem::path output(outputRaw);
    env->ReleaseStringUTFChars(inputPath, inputRaw);
    env->ReleaseStringUTFChars(outputPath, outputRaw);

    std::error_code ec;
    const uint64_t inputSize = std::filesystem::file_size(input, ec);
    if (ec || inputSize < 64 || inputSize > kMaxElfBytes)
        return result_string(env, "ERROR: ELF size is invalid (expected 64 B..512 MiB)");
    if (std::filesystem::exists(output, ec))
        return result_string(env, "ERROR: output directory already exists");

    const auto staging = output.parent_path() / (output.filename().string() + ".staging");
    if (std::filesystem::exists(staging, ec))
        return result_string(env, "ERROR: stale staging directory exists; remove it and retry");
    if (!std::filesystem::create_directories(staging, ec) || ec)
        return result_string(env, "ERROR: cannot create private output staging directory");

    auto fail = [&](const std::string& message) -> jstring {
        std::error_code ignored;
        std::filesystem::remove_all(staging, ignored);
        return result_string(env, "ERROR: " + message);
    };

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
        if (!N64Recomp::Context::from_elf_file(input, context, flags, false, dataSymbols, foundEntrypoint))
            return fail("N64Recomp could not parse this ELF. Supply a supported MIPS/N64 ELF with symbol and relocation data.");
    } catch (const std::exception& e) {
        return fail(std::string("ELF parser exception: ") + e.what());
    }

    if (context.functions.empty() || context.functions.size() > kMaxFunctions)
        return fail("ELF function count is empty or exceeds the safety limit");
    uint64_t instructionCount = 0;
    for (const auto& function : context.functions) {
        instructionCount += function.words.size();
        if (instructionCount > kMaxInstructions) return fail("ELF instruction count exceeds the safety limit");
    }

    // Normalize ELF symbol names to unique C identifiers before code generation.
    std::unordered_set<std::string> usedNames;
    for (auto& function : context.functions) {
        function.name = unique_identifier(function.name, usedNames);
    }
    context.functions_by_name.clear();
    for (size_t i = 0; i < context.functions.size(); ++i) {
        context.functions_by_name.emplace(context.functions[i].name, i);
    }
    context.trace_mode = false;

    std::vector<std::vector<uint32_t>> staticFunctions(context.sections.size());
    std::vector<std::string> generatedNames;
    generatedNames.reserve(context.functions.size());
    size_t compiledFunctions = 0;

    // First pass: all functions discovered from the ELF symbol table.
    const size_t originalFunctionCount = context.functions.size();
    for (size_t i = 0; i < originalFunctionCount; ++i) {
        const auto& function = context.functions[i];
        if (function.ignored || function.words.empty()) continue;
        const auto fileName = function.name + ".c";
        std::string error;
        if (!write_recompiled_function(context, i, staging / fileName, staticFunctions, error))
            return fail(error);
        generatedNames.push_back(fileName);
        ++compiledFunctions;
    }

    // Second pass: resolve static functions. Iterate to a fixed point because a function
    // in a later section can discover a static target in a section already visited.
    std::vector<std::unordered_set<uint32_t>> processedStatics(context.sections.size());
    bool foundMoreStatics = true;
    while (foundMoreStatics) {
        foundMoreStatics = false;
        for (size_t sectionIndex = 0; sectionIndex < context.sections.size(); ++sectionIndex) {
            auto& section = context.sections[sectionIndex];
            std::set<uint32_t> pending(staticFunctions[sectionIndex].begin(), staticFunctions[sectionIndex].end());
            for (uint32_t address : pending) {
                if (processedStatics[sectionIndex].contains(address)) continue;
                if (context.find_function_by_vram_section(address, sectionIndex) != static_cast<size_t>(-1)) {
                    processedStatics[sectionIndex].insert(address);
                    continue;
                }
                processedStatics[sectionIndex].insert(address);
                foundMoreStatics = true;

                if (section.rom_addr == UINT32_MAX || address < section.ram_addr ||
                    static_cast<uint64_t>(address) >= static_cast<uint64_t>(section.ram_addr) + section.size)
                    return fail("N64Recomp discovered a static function outside a file-backed section");

                uint32_t endAddress = section.ram_addr + section.size;
                for (const auto functionIndex : context.section_functions[sectionIndex]) {
                    const uint32_t knownAddress = context.functions[functionIndex].vram;
                    if (knownAddress > address && knownAddress < endAddress) endAddress = knownAddress;
                }
                for (uint32_t other : staticFunctions[sectionIndex]) {
                    if (other > address && other < endAddress) endAddress = other;
                }
                if (endAddress <= address || ((endAddress - address) & 3u) != 0)
                    return fail("Invalid bounds for a discovered static function");

                const uint64_t romOffset = static_cast<uint64_t>(section.rom_addr) + (address - section.ram_addr);
                const size_t wordCount = (endAddress - address) / 4;
                if (romOffset + static_cast<uint64_t>(wordCount) * 4 > context.rom.size())
                    return fail("Static function bytes exceed the ELF image bounds");

                std::vector<uint32_t> words(wordCount);
                std::memcpy(words.data(), context.rom.data() + romOffset, wordCount * sizeof(uint32_t));
                std::ostringstream addressText;
                addressText << std::hex << std::uppercase << address;
                const std::string functionName = unique_identifier(
                    "static_" + std::to_string(sectionIndex) + "_" + addressText.str(), usedNames);

                const size_t functionIndex = context.functions.size();
                context.functions.emplace_back(address, static_cast<uint32_t>(romOffset), std::move(words),
                    functionName, static_cast<uint16_t>(sectionIndex), false);
                context.section_functions[sectionIndex].push_back(functionIndex);
                section.function_addrs.push_back(address);
                context.functions_by_vram[address].push_back(functionIndex);

                std::string error;
                const std::string fileName = functionName + ".c";
                if (!write_recompiled_function(context, functionIndex, staging / fileName, staticFunctions, error))
                    return fail(error);
                generatedNames.push_back(fileName);
                ++compiledFunctions;
            }
        }
    }

    // Emit declarations after every discovered static function is known.
    {
        std::ofstream header(staging / "funcs.h", std::ios::binary | std::ios::trunc);
        if (!header) return fail("Cannot create funcs.h");
        header << "#pragma once\n#include <stdint.h>\n#include \"recomp.h\"\n\n";
        header << "#ifdef __cplusplus\nextern \"C\" {\n#endif\n";
        for (const auto& function : context.functions) {
            if (!function.ignored && !function.words.empty())
                header << "void " << function.name << "(uint8_t* rdram, recomp_context* ctx);\n";
        }
        header << "#ifdef __cplusplus\n}\n#endif\n";
        if (!header.good()) return fail("Failed writing funcs.h");
    }

    // A generated C project is not executable until the game-specific runtime is supplied.
    {
        std::ofstream manifest(staging / "ngr-recompile-manifest.json", std::ios::binary | std::ios::trunc);
        if (!manifest) return fail("Cannot create recompile manifest");
        manifest << "{\n"
                 << "  \"format\": \"NGR-N64-C-v1\",\n"
                 << "  \"recompiler\": \"N64Recomp\",\n"
                 << "  \"upstream_commit\": \"598ce2080004a5672d604dceadbda436045307b7\",\n"
                 << "  \"input_elf\": \"" << json_escape(input.filename().string()) << "\",\n"
                 << "  \"generated_functions\": " << compiledFunctions << ",\n"
                 << "  \"generated_sources\": " << generatedNames.size() << ",\n"
                 << "  \"runtime_required\": true,\n"
                 << "  \"runtime_status\": \"not-integrated\",\n"
                 << "  \"arm64_binary_built\": false,\n"
                 << "  \"launchable\": false\n"
                 << "}\n";
        if (!manifest.good()) return fail("Failed writing recompile manifest");
    }

    std::filesystem::rename(staging, output, ec);
    if (ec) return fail("Cannot atomically publish generated output: " + ec.message());

    std::ostringstream report;
    report << "OK\nN64Recomp C generation completed\n"
           << "Functions translated: " << compiledFunctions << "\n"
           << "C source files: " << generatedNames.size() << "\n"
           << "Output: " << output.string() << "\n"
           << "Next required stage: compile with Android ARM64 Clang and link a compatible N64 title runtime.\n"
           << "Launchable: no (runtime not integrated).";
    return result_string(env, report.str());
}
