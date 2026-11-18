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

/** Bilingual first-stage N64Recomp flow: import and inspect an N64 ELF. */
class N64RecompilerActivity : Activity() {
    private var language = "en"
    private lateinit var status: TextView
    companion object {
        private const val REQUEST_ELF = 6201
        private const val MAX_ELF_BYTES = 512L * 1024L * 1024L
        init { System.loadLibrary("ngr_runtime") }
    }
    private external fun nativeInspectN64Elf(path: String): String
    private fun ru() = language == "ru"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        language = getPreferences(MODE_PRIVATE).getString("language", "en") ?: "en"
        render()
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
            textSize = 24f; setTextColor(Color.WHITE)
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
                "N64 / MIPS → C → ARM64\n\nЭтап 1: анализ ELF и таблицы символов встроенным ядром N64Recomp. Для генерации ARM64-кода и запуска игры также потребуются ROM/символьные данные, конфигурация проекта и совместимое N64 runtime."
            else
                "N64 / MIPS → C → ARM64\n\nStage 1: inspect the ELF and symbol table using the integrated N64Recomp core. ARM64 code generation and game execution also require ROM/symbol data, project configuration and a compatible N64 runtime."
            textSize = 14f; setTextColor(Color.rgb(180, 190, 205)); setPadding(0, 20, 0, 20)
        })
        root.addView(Button(this).apply {
            text = if (ru()) "Выбрать ELF-файл" else "Choose ELF file"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                }
                startActivityForResult(intent, REQUEST_ELF)
            }
        })
        status = TextView(this).apply {
            text = if (ru()) "Ожидание ELF-файла…" else "Waiting for an ELF file…"
            textSize = 13f; setTextColor(Color.rgb(105, 166, 255)); setPadding(0, 20, 0, 0)
        }
        root.addView(status)
        setContentView(scroll)
    }

    @Deprecated("Legacy callback retained for compatibility with the host Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ELF || resultCode != RESULT_OK || data?.data == null) return
        status.text = if (ru()) "Копирование ELF в приватное хранилище…" else "Copying ELF into private storage…"
        val uri = data.data!!
        Thread {
            try {
                val dir = File(filesDir, "n64recomp/incoming").apply { mkdirs() }
                val target = File(dir, "input.elf")
                var total = 0L
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected document" }
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_ELF_BYTES) { "ELF exceeds 512 MiB limit" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(total >= 64) { "Selected file is too small to be an ELF" }
                val report = nativeInspectN64Elf(target.absolutePath)
                runOnUiThread { status.text = report }
            } catch (failure: Exception) {
                runOnUiThread {
                    status.text = (if (ru()) "Ошибка импорта ELF: " else "ELF import failed: ") + (failure.message ?: "unknown")
                    Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
