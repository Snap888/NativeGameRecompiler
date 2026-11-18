package com.ngr.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * Unified intake for static game recompilers. Detection is based on file structure,
 * not extensions alone. Only the N64/MIPS ELF backend is currently executable.
 */
class NgrRecompilerPickerActivity : Activity() {
    private enum class Mode { AUTO, MANUAL }
    private var mode = Mode.AUTO
    private var selectedSystem = NgrRecompilerCatalog.SystemId.N64_MIPS
    private lateinit var modeGroup: RadioGroup
    private lateinit var systemButton: Button
    private lateinit var status: TextView

    companion object {
        private const val REQUEST_FILES = 7301
        private const val REQUEST_FOLDER = 7302
        private const val MAX_SCAN_FILES = 5000
        private const val MAX_SCAN_DEPTH = 16
        private const val MAX_INPUT_BYTES = 512L * 1024L * 1024L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(android.graphics.Color.rgb(15, 18, 24))
        }
        root.addView(TextView(this).apply {
            text = "GAME RECOMPILER"
            textSize = 23f
            setTextColor(android.graphics.Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Выберите исходные файлы или папку. NGR определит формат и предложит совместимый backend либо позволит выбрать систему вручную."
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(180, 190, 205))
            setPadding(0, 16, 0, 18)
        })
        modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val auto = RadioButton(this@NgrRecompilerPickerActivity).apply {
                id = 1
                text = "Автоопределение формата"
                setTextColor(android.graphics.Color.WHITE)
                isChecked = mode == Mode.AUTO
            }
            val manual = RadioButton(this@NgrRecompilerPickerActivity).apply {
                id = 2
                text = "Выбрать систему вручную"
                setTextColor(android.graphics.Color.WHITE)
                isChecked = mode == Mode.MANUAL
            }
            addView(auto)
            addView(manual)
            setOnCheckedChangeListener { _, checked ->
                mode = if (checked == 2) Mode.MANUAL else Mode.AUTO
                systemButton.isEnabled = mode == Mode.MANUAL
                status.text = if (mode == Mode.AUTO) {
                    "Формат будет определён по заголовку и архитектуре файла."
                } else {
                    "Выберите целевую систему. Сейчас подключён N64 / MIPS."
                }
            }
        }
        root.addView(modeGroup)
        systemButton = Button(this).apply {
            text = "Система: Nintendo 64 / MIPS (N64Recomp)"
            isEnabled = mode == Mode.MANUAL
            setOnClickListener { chooseSystem() }
        }
        root.addView(systemButton)
        root.addView(Button(this).apply {
            text = "Выбрать файл(ы)"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }, REQUEST_FILES)
            }
        })
        root.addView(Button(this).apply {
            text = "Выбрать папку"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_FOLDER)
            }
        })
        status = TextView(this).apply {
            text = "Поддерживаемый backend: Nintendo 64 / MIPS ELF. Остальные системы будут добавляться отдельными модулями."
            textSize = 13f
            setTextColor(android.graphics.Color.rgb(105, 166, 255))
            setPadding(0, 18, 0, 0)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Закрыть"
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    private fun chooseSystem() {
        val systems = NgrRecompilerCatalog.systems
        val labels = systems.map { item ->
            if (item.available) item.label else "${item.label} · скоро"
        }
        AlertDialog.Builder(this)
            .setTitle("Целевая система")
            .setSingleChoiceItems(labels.toTypedArray(), systems.indexOfFirst { it.id == selectedSystem }) { dialog, which ->
                val item = systems[which]
                if (!item.available) {
                    Toast.makeText(this, "${item.label}: backend ещё не подключён", Toast.LENGTH_LONG).show()
                } else {
                    selectedSystem = item.id
                    systemButton.text = "Система: ${item.label}"
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_FILES -> {
                val uris = mutableListOf<Uri>()
                data.data?.let(uris::add)
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
                }
                processCandidates(uris.distinct())
            }
            REQUEST_FOLDER -> {
                val treeUri = data.data ?: return
                val root = DocumentFile.fromTreeUri(this, treeUri) ?: return
                status.text = "Сканирование папки…"
                Thread {
                    val candidates = scanFolder(root)
                    runOnUiThread { processCandidates(candidates) }
                }.start()
            }
        }
    }

    private fun scanFolder(root: DocumentFile): List<Uri> {
        val found = mutableListOf<Uri>()
        var visited = 0
        fun walk(directory: DocumentFile, depth: Int) {
            if (depth > MAX_SCAN_DEPTH || found.size >= 200 || visited >= MAX_SCAN_FILES) return
            val children = runCatching { directory.listFiles() }.getOrDefault(emptyArray())
            for (child in children) {
                if (++visited > MAX_SCAN_FILES || found.size >= 200) break
                if (child.isDirectory) walk(child, depth + 1)
                else if (child.isFile && child.name?.substringAfterLast('.', "")?.equals("elf", true) == true) {
                    found += child.uri
                }
            }
        }
        walk(root, 0)
        return found
    }

    private fun processCandidates(uris: List<Uri>) {
        if (uris.isEmpty()) {
            status.text = "Не найдено подходящих ELF-файлов."
            return
        }
        status.text = "Проверка форматов: ${uris.size} файл(ов)…"
        Thread {
            val compatible = uris.mapNotNull { uri ->
                val detected = detectSystem(uri)
                if (mode == Mode.AUTO) {
                    if (detected == NgrRecompilerCatalog.SystemId.N64_MIPS) uri to detected else null
                } else {
                    if (selectedSystem == NgrRecompilerCatalog.SystemId.N64_MIPS &&
                        detected == NgrRecompilerCatalog.SystemId.N64_MIPS) uri to selectedSystem else null
                }
            }
            runOnUiThread {
                when {
                    compatible.isEmpty() -> status.text =
                        "Совместимые файлы не найдены. N64Recomp принимает MIPS ELF, а не обычный ROM (.z64/.n64/.v64)."
                    compatible.size == 1 -> openN64(compatible.first().first)
                    else -> {
                        val labels = compatible.map { queryName(it.first) ?: it.first.lastPathSegment ?: "MIPS ELF" }
                        AlertDialog.Builder(this)
                            .setTitle("Выберите ELF для рекомпиляции")
                            .setItems(labels.toTypedArray()) { _, index -> openN64(compatible[index].first) }
                            .setNegativeButton("Отмена", null)
                            .show()
                        status.text = "Найдено MIPS ELF: ${compatible.size}"
                    }
                }
            }
        }.start()
    }

    private fun detectSystem(uri: Uri): NgrRecompilerCatalog.SystemId? = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(20)
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) break
                offset += count
            }
            if (offset < 20 || header[0] != 0x7f.toByte() ||
                header[1] != 'E'.code.toByte() || header[2] != 'L'.code.toByte() ||
                header[3] != 'F'.code.toByte()) return@use null
            val littleEndian = header[5].toInt() == 1
            val machine = if (littleEndian) {
                (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
            } else {
                ((header[18].toInt() and 0xff) shl 8) or (header[19].toInt() and 0xff)
            }
            if (machine == 8) NgrRecompilerCatalog.SystemId.N64_MIPS else null
        }
    }.getOrNull()

    private fun queryName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()

    private fun openN64(uri: Uri) {
        startActivity(Intent(this, N64RecompilerActivity::class.java).apply {
            data = uri
            putExtra(N64RecompilerActivity.EXTRA_INPUT_URI, uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
