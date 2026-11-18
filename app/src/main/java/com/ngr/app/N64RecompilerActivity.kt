package com.ngr.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/** N64Recomp import, ELF analysis and native C-code generation flow. */
class N64RecompilerActivity : Activity() {
    private var language = "en"
    private var selectedElfPath: String? = null
    private lateinit var status: TextView
    private lateinit var generateButton: Button

    companion object {
        private const val REQUEST_ELF = 6201
        const val EXTRA_INPUT_URI = "com.ngr.app.extra.INPUT_URI"
        private const val MAX_ELF_BYTES = 512L * 1024L * 1024L
        init { System.loadLibrary("ngr_runtime") }
    }

    private external fun nativeInspectN64Elf(path: String): String
    private external fun nativeGenerateN64C(inputPath: String, outputPath: String): String
    private fun ru() = language == "ru"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NgrLogger.info("recompiler.n64", "N64RecompilerActivity opened; intentData=${intent.data}")
        language = getPreferences(MODE_PRIVATE).getString("language", "en") ?: "en"
        render()
        (intent.getStringExtra(EXTRA_INPUT_URI)?.let { raw -> runCatching { android.net.Uri.parse(raw) }.getOrNull() }
            ?: intent.data)?.let(::importElfUri)
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(Color.rgb(15, 18, 24))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(TextView(this).apply {
            text = if (ru()) "Рекомпилятор Nintendo 64" else "Nintendo 64 Recompiler"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(Button(this).apply {
            text = if (ru()) "Русский / English" else "English / Русский"
            setOnClickListener {
                language = if (ru()) "en" else "ru"
                getPreferences(MODE_PRIVATE).edit().putString("language", language).apply()
                render()
            }
        })
        root.addView(TextView(this).apply {
            text = if (ru())
                "N64 / MIPS → C → ARM64\n\n1. Импорт ELF с таблицей символов.\n2. Генерация исходников C встроенным N64Recomp.\n3. Следующий этап — сборка ARM64 и подключение игрового N64 runtime. Пока runtime не подключён, созданные исходники не являются запускаемой игрой."
            else
                "N64 / MIPS → C → ARM64\n\n1. Import an ELF with symbol data.\n2. Generate C sources using the integrated N64Recomp core.\n3. Next: ARM64 compilation and integration of the game-specific N64 runtime. Generated sources are not a playable game until that runtime is available."
            textSize = 14f
            setTextColor(Color.rgb(180, 190, 205))
            setPadding(0, 20, 0, 20)
        })
        root.addView(Button(this).apply {
            text = if (ru()) "Выбрать ELF-файл" else "Choose ELF file"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                }
                startActivityForResult(intent, REQUEST_ELF)
            }
        })
        generateButton = Button(this).apply {
            text = if (ru()) "Рекомпилировать в C" else "Recompile to C"
            isEnabled = selectedElfPath != null
            setOnClickListener { generateC() }
        }
        root.addView(generateButton)
        root.addView(Button(this).apply {
            text = if (ru()) "Показать журнал рекомпилятора" else "Show recompiler log"
            setOnClickListener { showRecompilerLog() }
        })
        status = TextView(this).apply {
            text = if (ru()) "Ожидание ELF-файла…" else "Waiting for an ELF file…"
            textSize = 13f
            setTextColor(Color.rgb(105, 166, 255))
            setPadding(0, 20, 0, 0)
        }
        root.addView(status)
        setContentView(scroll)
    }

    private fun showRecompilerLog() {
        val view = TextView(this).apply {
            text = "Log: ${NgrLogger.filePath()}\\n\\n${NgrLogger.recentText(120_000)}"
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(20, 16, 20, 16)
        }
        val scroll = ScrollView(this).apply { addView(view) }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (ru()) "Журнал рекомпилятора" else "Recompiler log")
            .setView(scroll)
            .setPositiveButton(if (ru()) "Закрыть" else "Close", null)
            .setNeutralButton(if (ru()) "Поделиться" else "Share") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, NgrLogger.recentText(120_000))
                }
                startActivity(Intent.createChooser(send, if (ru()) "Отправить журнал" else "Share log"))
            }
            .show()
    }

    private fun generateC() {
        val input = selectedElfPath ?: return
        generateButton.isEnabled = false
        NgrLogger.info("recompiler.n64", "Generation started; input=$input")
        status.text = if (ru()) "Рекомпиляция MIPS → C… Не закрывайте приложение." else "Recompiling MIPS → C… Keep the app open."
        Thread {
            val output = File(filesDir, "n64recomp/generated/run-${System.currentTimeMillis()}")
            try {
                output.parentFile?.mkdirs()
                val report = nativeGenerateN64C(input, output.absolutePath)
                NgrLogger.info("recompiler.n64", "Native generation finished; output=$output; report=${report.take(2000)}")
                val shortcutMessage = if (report.startsWith("OK")) createRecompiledGameShortcut(output) else null
                val shortcutCreated = shortcutMessage?.startsWith("OK:") == true
                runOnUiThread {
                    status.text = if (shortcutMessage != null) "$report\n\n$shortcutMessage" else report
                    generateButton.isEnabled = true
                    Toast.makeText(this, if (shortcutCreated) {
                        if (ru()) "Игра добавлена в ярлыки" else "Game added to shortcuts"
                    } else if (report.startsWith("OK")) {
                        if (ru()) "Исходники C созданы, но ярлык не сохранён" else "C sources generated, shortcut was not saved"
                    } else report, Toast.LENGTH_LONG).show()
                }
            } catch (failure: Exception) {
                NgrLogger.error("recompiler.n64", "Generation failed; input=$input", failure)
                runOnUiThread {
                    status.text = (if (ru()) "Ошибка рекомпиляции: " else "Recompiler error: ") +
                        (failure.message ?: "unknown")
                    generateButton.isEnabled = true
                }
            }
        }.start()
    }

    /**
     * Register generated N64 projects in the shared game-shortcut library.
     * The shortcut is deliberately non-launchable until an ARM64 binary and title runtime exist.
     */
    private fun createRecompiledGameShortcut(output: File): String {
        NgrLogger.info("recompiler.n64", "Registering generated game shortcut for $output")
        return try {
            val runtimeRoot = File(filesDir, "ngr-runtime")
            val profileStore = NgrEnvironmentProfileStore(runtimeRoot)
            var profile = profileStore.loadAll().profiles.firstOrNull()
            if (profile == null) {
                profile = NgrEnvironmentProfile(
                    profileId = "n64-native-default",
                    displayName = "N64 Native (runtime pending)",
                    containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
                    cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64
                )
                val savedProfile = profileStore.save(profile)
                require(savedProfile.success) { savedProfile.error ?: "Unable to create the default N64 profile" }
            }

            val suffix = output.name.removePrefix("run-").take(32).ifBlank { System.currentTimeMillis().toString() }
            val gameTitle = File(selectedElfPath ?: "N64 Game").nameWithoutExtension.take(90).ifBlank { "N64 Game" }
            val shortcut = NgrGameShortcut(
                shortcutId = "n64-$suffix",
                title = gameTitle,
                packageId = "n64-$suffix",
                executablePath = "native/run-$suffix/game.so",
                workingDirectory = "native/run-$suffix",
                environmentProfileId = profile.profileId,
                launchMode = NgrGameShortcut.LaunchMode.NATIVE_ARM64,
                enabled = true
            )
            val saved = NgrGameShortcutStore(runtimeRoot).save(shortcut)
            require(saved.success) { saved.error ?: "Unable to save game shortcut" }
            NgrLogger.info("recompiler.n64", "Game shortcut saved; id=${shortcut.shortcutId}; title=${shortcut.title}")
            if (ru()) {
                "OK: Ярлык создан: ${shortcut.title}. Настройки доступны в GAME SHORTCUTS. Запуск появится после сборки ARM64 и подключения N64 runtime."
            } else {
                "OK: Shortcut created: ${shortcut.title}. Configure it in GAME SHORTCUTS. Launch will be enabled after ARM64 compilation and N64 runtime integration."
            }
        } catch (failure: Exception) {
            NgrLogger.error("recompiler.n64", "Unable to register generated game shortcut", failure)
            "ERROR: " + (if (ru()) "Не удалось создать ярлык: " else "Could not create shortcut: ") +
                (failure.message ?: "unknown error")
        }
    }
    @Deprecated("Legacy callback retained for compatibility with the host Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ELF || resultCode != RESULT_OK || data?.data == null) return
        NgrLogger.info("recompiler.n64", "SAF ELF file selected; uri=${data.data}")
        importElfUri(data.data!!)
    }

    private fun importElfUri(uri: android.net.Uri) {
        NgrLogger.info("recompiler.n64", "Import started; uri=$uri")
        status.text = if (ru()) "Копирование ELF в приватное хранилище…" else "Copying ELF into private storage…"
        Thread {
            try {
                val dir = File(filesDir, "n64recomp/incoming").apply { mkdirs() }
                val target = File(dir, "input-" + System.currentTimeMillis() + ".elf")
                var total = 0L
                contentResolver.openInputStream(uri).use { input ->
                    val source = requireNotNull(input) { "Unable to open selected document" }
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_ELF_BYTES) { "ELF exceeds 512 MiB limit" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(total >= 64) { "Selected file is too small to be an ELF" }
                val report = nativeInspectN64Elf(target.absolutePath)
                NgrLogger.info("recompiler.n64", "ELF inspection finished; bytes=$total; path=$target; report=${report.take(2000)}")
                runOnUiThread {
                    selectedElfPath = target.absolutePath
                    status.text = report
                    generateButton.isEnabled = report.startsWith("OK")
                }
            } catch (failure: Exception) {
                NgrLogger.error("recompiler.n64", "ELF import/inspection failed; uri=$uri", failure)
                runOnUiThread {
                    status.text = (if (ru()) "Ошибка импорта ELF: " else "ELF import failed: ") +
                        (failure.message ?: "unknown")
                    generateButton.isEnabled = false
                    Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
