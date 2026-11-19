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

/**
 * Unified intake for static game recompilers. N64 MIPS ELF is executable; Xbox 360 inputs
 * are routed to the isolated XDroid360 Android library. The launcher UI is embedded; toolchain
 * packaging and native runtime integration remain separately gated.
 */
class NgrRecompilerPickerActivity : Activity() {
    private enum class Mode { AUTO, MANUAL }
    private var mode = Mode.AUTO
    private var selectedSystem = NgrRecompilerCatalog.SystemId.N64_MIPS
    private lateinit var systemButton: Button
    private lateinit var status: TextView
    private var selectedTreeUri: Uri? = null

    companion object {
        private const val REQUEST_FILES = 7301
        private const val REQUEST_FOLDER = 7302
        private const val MAX_SCAN_FILES = 5000
        private const val MAX_SCAN_DEPTH = 16
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NgrLogger.info("recompiler.picker", "Opened unified recompiler picker")
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
            text = "Выберите исходные файлы или папку. NGR определит формат либо позволит выбрать целевую систему вручную."
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(180, 190, 205))
            setPadding(0, 16, 0, 18)
        })
        val modeGroup = RadioGroup(this).apply {
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
                status.text = if (mode == Mode.AUTO) "Формат определяется по заголовку файла." else
                    "Выберите целевую систему. Активен только проверенный backend N64 / MIPS ELF."
                NgrLogger.info("recompiler.picker", "Selection mode=$mode")
            }
        }
        root.addView(modeGroup)
        root.addView(Button(this).apply {
            text = "Открыть Xbox 360 (XDroid360)"
            setOnClickListener {
                val ready = runCatching {
                    com.udarmolota.xdroid360.XDroid360EntryPoint.isBuildEnvironmentReady(this@NgrRecompilerPickerActivity)
                }.getOrDefault(false)
                NgrLogger.info("recompiler.xbox360", "Opening XDroid360 launcher; buildEnvironmentReady=$ready")
                if (!ready) {
                    AlertDialog.Builder(this@NgrRecompilerPickerActivity)
                        .setTitle("Среда Xbox 360 подключена не полностью")
                        .setMessage("Интерфейс XDroid360 встроен. Компилятор XenonRecomp, kit и игровые профили ещё не включены в APK, поэтому сборка игры пока недоступна.")
                        .setPositiveButton("Открыть интерфейс") { _, _ ->
                            com.udarmolota.xdroid360.XDroid360EntryPoint.launch(this@NgrRecompilerPickerActivity)
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                } else {
                    com.udarmolota.xdroid360.XDroid360EntryPoint.launch(this@NgrRecompilerPickerActivity)
                }
            }
        })
        systemButton = Button(this).apply {
            text = "Система: Nintendo 64 / MIPS ELF"
            isEnabled = mode == Mode.MANUAL
            setOnClickListener { chooseSystem() }
        }
        root.addView(systemButton)
        root.addView(Button(this).apply {
            text = "Выбрать файл(ы)"
            setOnClickListener {
                NgrLogger.info("recompiler.picker", "Opening SAF multi-file picker")
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
                NgrLogger.info("recompiler.picker", "Opening SAF folder picker")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_FOLDER)
            }
        })
        status = TextView(this).apply {
            text = "Активен: Nintendo 64 / MIPS ELF. ROM .z64 распознаётся отдельно; для N64Recomp нужен ELF из проекта декомпиляции."
            textSize = 13f
            setTextColor(android.graphics.Color.rgb(105, 166, 255))
            setPadding(0, 18, 0, 0)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Показать журнал рекомпилятора"
            setOnClickListener { showRecompilerLog() }
        })
        root.addView(Button(this).apply {
            text = "Закрыть"
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    private fun showRecompilerLog() {
        val view = TextView(this).apply {
            text = "Log: ${NgrLogger.filePath()}\n\n${NgrLogger.recentText(120_000)}"
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(20, 16, 20, 16)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(view) }
        AlertDialog.Builder(this)
            .setTitle("Журнал рекомпилятора")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .setNeutralButton("Поделиться") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, NgrLogger.recentText(120_000))
                }
                startActivity(Intent.createChooser(send, "Отправить журнал"))
            }
            .show()
    }
    private fun chooseSystem() {
        val systems = NgrRecompilerCatalog.systems
        val labels = systems.map { if (it.available) it.label else "${it.label} · скоро" }
        AlertDialog.Builder(this)
            .setTitle("Целевая система")
            .setSingleChoiceItems(labels.toTypedArray(), systems.indexOfFirst { it.id == selectedSystem }) { dialog, which ->
                val item = systems[which]
                selectedSystem = item.id
                systemButton.text = "Система: ${item.label}"
                NgrLogger.info("recompiler.picker", "Manual target=${item.id}; available=${item.available}")
                dialog.dismiss()
                if (!item.available) Toast.makeText(this,
                    "${item.label}: система внесена в каталог, backend ещё не подключён", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        NgrLogger.info("recompiler.picker", "Picker result request=$requestCode result=$resultCode dataPresent=${data != null}")
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_FILES -> {
                selectedTreeUri = null
                val uris = mutableListOf<Uri>()
                data.data?.let(uris::add)
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
                }
                processCandidates(uris.distinct())
            }
            REQUEST_FOLDER -> {
                val treeUri = data.data ?: return
                selectedTreeUri = treeUri
                val root = DocumentFile.fromTreeUri(this, treeUri)
                if (root == null) {
                    NgrLogger.error("recompiler.scan", "Unable to resolve selected folder tree")
                    status.text = "Не удалось открыть выбранную папку."
                    return
                }
                runCatching {
                    contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.onFailure { NgrLogger.warn("recompiler.scan", "Could not persist folder permission: ${it.message}") }
                status.text = "Сканирование папки…"
                Thread {
                    val candidates = scanFolder(root)
                    NgrLogger.info("recompiler.scan", "Folder scan finished; candidates=${candidates.size}")
                    runOnUiThread { processCandidates(candidates) }
                }.start()
            }
        }
    }

    private fun scanFolder(root: DocumentFile): List<Uri> {
        val found = mutableListOf<Uri>()
        val extensions = NgrRecompilerCatalog.systems.flatMap { it.extensions }.map { it.lowercase() }.toSet()
        var visited = 0
        fun walk(directory: DocumentFile, depth: Int) {
            if (depth > MAX_SCAN_DEPTH || found.size >= 200 || visited >= MAX_SCAN_FILES) return
            val children = runCatching { directory.listFiles() }.getOrElse {
                NgrLogger.warn("recompiler.scan", "Cannot list folder: ${it.message}")
                emptyArray()
            }
            for (child in children) {
                if (++visited > MAX_SCAN_FILES || found.size >= 200) break
                if (child.isDirectory) walk(child, depth + 1)
                else if (child.isFile && child.name?.substringAfterLast('.', "")?.lowercase() in extensions) {
                    found += child.uri
                }
            }
        }
        walk(root, 0)
        return found
    }

    private fun processCandidates(uris: List<Uri>) {
        if (uris.isEmpty()) {
            status.text = "Файлы не выбраны или поддерживаемые расширения не найдены."
            NgrLogger.warn("recompiler.detect", "No candidate files received")
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
            return
        }
        val selectedMode = mode
        val targetSystem = selectedSystem
        NgrLogger.info("recompiler.detect", "Processing count=${uris.size}; mode=$selectedMode; target=$targetSystem")
        status.text = "Проверка форматов: ${uris.size} файл(ов)…"
        NgrLogger.info("recompiler.detect", "Detection worker started; count=${uris.size}")
        Thread({
            val detected = uris.map { uri ->
                val name = queryName(uri) ?: uri.lastPathSegment ?: "unknown"
                val system = detectSystem(uri)
                NgrLogger.info("recompiler.detect", "File=$name; detected=${system ?: "unknown"}; uriScheme=${uri.scheme}")
                Triple(uri, name, system)
            }
            val elfs = detected.filter { it.third == NgrRecompilerCatalog.SystemId.N64_MIPS }
            val roms = detected.filter { it.third == NgrRecompilerCatalog.SystemId.N64_ROM }
            val xbox360 = detected.filter { it.third == NgrRecompilerCatalog.SystemId.XBOX_360 }
            val persistedCount = persistDetectedProjects(detected)
            runOnUiThread {
                if (selectedMode == Mode.MANUAL && targetSystem != NgrRecompilerCatalog.SystemId.N64_MIPS &&
                    targetSystem != NgrRecompilerCatalog.SystemId.XBOX_360) {
                    val label = NgrRecompilerCatalog.systems.first { it.id == targetSystem }.label
                    val message = "$label: backend ещё не реализован. Файл выбран, но рекомпиляция пока недоступна."
                    status.text = message
                    NgrLogger.warn("recompiler.detect", "Manual backend unavailable: $targetSystem")
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                } else if (elfs.isNotEmpty()) {
                    if (elfs.size == 1) openN64(elfs.first().first)
                    else {
                        AlertDialog.Builder(this)
                            .setTitle("Выберите N64 MIPS ELF")
                            .setItems(elfs.map { it.second }.toTypedArray()) { _, index -> openN64(elfs[index].first) }
                            .setNegativeButton("Отмена", null)
                            .show()
                        status.text = "Найдено N64 MIPS ELF: ${elfs.size}"
                    }
                } else if (roms.isNotEmpty()) {
                    val message = "Nintendo 64 ROM обнаружен: ${roms.joinToString { it.second }}. Записей в библиотеку проектов: $persistedCount. Заголовок и порядок байтов распознаны. N64Recomp не принимает retail .z64 напрямую: ему нужен MIPS ELF и конфигурация проекта декомпиляции (symbols/functions). Проект сохранён со статусом «backend unavailable», запускаемый ярлык не создаётся."
                    status.text = message
                    NgrLogger.warn("recompiler.detect", "N64 ROM detected; current N64Recomp backend requires decompilation ELF")
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                } else if (xbox360.isNotEmpty()) {
                    val tree = selectedTreeUri
                    if (tree != null) {
                        status.text = "Передача папки Xbox 360 в XDroid360: проверка STFS, title ID и профиля…"
                        NgrLogger.info("recompiler.xbox360", "Dispatching selected SAF tree to XDroid360 GameSource/BuildActivity")
                        dispatchXbox360Build(tree, "tree", "выбранная папка")
                    } else {
                        val supported = xbox360.filter { it.second.substringAfterLast('.', "").lowercase() in setOf("iso", "zip") }
                        if (supported.isEmpty()) {
                            val message = "Файлы Xbox 360 обнаружены, но отдельный .xex не является поддерживаемым входом XDroid360. Выберите ISO-образ или папку с STFS-пакетами."
                            status.text = message
                            NgrLogger.warn("recompiler.xbox360", "Standalone XEX cannot be passed to XDroid360 GameSource")
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        } else {
                            val launch: (Int) -> Unit = { index ->
                                val (uri, name, _) = supported[index]
                                status.text = "Передача $name в XDroid360: проверка XISO/STFS и профиля…"
                                NgrLogger.info("recompiler.xbox360", "Dispatching $name to XDroid360 BuildActivity")
                                dispatchXbox360Build(uri, "file", name)
                            }
                            if (supported.size == 1) launch(0) else AlertDialog.Builder(this)
                                .setTitle("Выберите игру Xbox 360")
                                .setItems(supported.map { it.second }.toTypedArray()) { _, index -> launch(index) }
                                .setNegativeButton("Отмена", null)
                                .show()
                        }
                    }
                } else {
                    val message = "Не найден поддерживаемый входной файл. Проверено: ${detected.size}."
                    status.text = message
                    NgrLogger.warn("recompiler.detect", message)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }).apply {
            name = "NGR-Recompiler-Detect"
            isDaemon = true
        }.start()
    }

    private fun dispatchXbox360Build(uri: Uri, pick: String, label: String) {
        val ready = runCatching {
            com.udarmolota.xdroid360.XDroid360EntryPoint.isBuildEnvironmentReady(this)
        }.getOrDefault(false)
        if (!ready) {
            val message = "Интерфейс XDroid360 перенесён, но в APK пока нет комплекта Clang/LLD/XenonRecomp, STFS/Xenos/DXC и игровых профилей. Сборка Xbox 360 станет доступна после успешного завершения workflow NGR XDroid360 Toolchain."
            NgrLogger.warn("recompiler.xbox360", "Build blocked: compiler kit or title profiles are not packaged")
            status.text = message
            AlertDialog.Builder(this)
                .setTitle("Не установлен комплект рекомпилятора Xbox 360")
                .setMessage(message)
                .setPositiveButton("Открыть библиотеку XDroid360") { _, _ ->
                    com.udarmolota.xdroid360.XDroid360EntryPoint.launch(this)
                }
                .setNegativeButton("Закрыть", null)
                .show()
            return
        }
        NgrLogger.info("recompiler.xbox360", "Launching donor BuildActivity for $label; pick=$pick")
        runCatching {
            com.udarmolota.xdroid360.XDroid360EntryPoint.launchBuild(this, uri, pick)
        }.onFailure {
            NgrLogger.error("recompiler.xbox360", "Unable to launch XDroid360 build flow: ${it.message}")
            status.text = "Не удалось открыть конвейер Xbox 360: ${it.message}"
        }
    }

    private fun persistDetectedProjects(
        detected: List<Triple<Uri, String, NgrRecompilerCatalog.SystemId?>>
    ): Int {
        val store = NgrRecompilerProjectStore(filesDir)
        val xboxReady = runCatching {
            com.udarmolota.xdroid360.XDroid360EntryPoint.isBuildEnvironmentReady(this)
        }.getOrDefault(false)
        var saved = 0
        detected.forEach { (uri, name, system) ->
            val record = when (system) {
                NgrRecompilerCatalog.SystemId.XBOX_360 -> NgrRecompilerProject(
                    projectId = "xbox360-" + java.util.UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString(),
                    displayName = name.take(240),
                    systemId = "XBOX_360",
                    sourceUri = uri.toString(),
                    sourceExtension = name.substringAfterLast('.', "").lowercase().take(16),
                    status = if (xboxReady) NgrRecompilerProject.Status.METADATA_REQUIRED
                             else NgrRecompilerProject.Status.BACKEND_UNAVAILABLE,
                    detail = if (xboxReady) "XDroid360 toolchain and profiles are packaged; title/profile validation is pending."
                             else "XDroid360 source is integrated, but its compiler kit and game profiles are not packaged in this APK."
                )
                NgrRecompilerCatalog.SystemId.N64_ROM -> NgrRecompilerProject(
                    projectId = "n64rom-" + java.util.UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString(),
                    displayName = name.take(240),
                    systemId = "N64_ROM",
                    sourceUri = uri.toString(),
                    sourceExtension = name.substringAfterLast('.', "").lowercase().take(16),
                    status = NgrRecompilerProject.Status.BACKEND_UNAVAILABLE,
                    detail = "Retail N64 ROM requires a title-specific decompilation project; N64Recomp consumes a MIPS ELF."
                )
                else -> null
            } ?: return@forEach
            val result = store.upsert(record)
            if (result.isSuccess) {
                saved++
                NgrLogger.info("recompiler.project", "Project persisted; id=${record.projectId}; system=${record.systemId}; status=${record.status}")
            } else {
                NgrLogger.error("recompiler.project", "Project persistence failed; name=$name; error=${result.exceptionOrNull()?.message}")
            }
        }
        NgrLogger.info("recompiler.project", "Persisted ${saved} recompiler project(s)")
        return saved
    }

    private fun detectSystem(uri: Uri): NgrRecompilerCatalog.SystemId? = runCatching {
        val name = queryName(uri).orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase()
        contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(20)
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) break
                offset += count
            }
            if (offset >= 4) {
                val romOrder = N64RomInputProbe.detect(header)
                if (romOrder != null) {
                    NgrLogger.info("recompiler.detect", "N64 ROM magic detected; byteOrder=$romOrder; name=$name")
                    return@use NgrRecompilerCatalog.SystemId.N64_ROM
                }
            }
            if (Xbox360InputProbe.detect(header) != null) {
                NgrLogger.info("recompiler.detect", "Xbox 360 header detected: ${Xbox360InputProbe.detect(header)}")
                return@use NgrRecompilerCatalog.SystemId.XBOX_360
            }
            if (Xbox360InputProbe.isCandidateFileName(name)) {
                NgrLogger.info("recompiler.detect", "Xbox 360 container candidate by extension; XDroid360 title validation required: $name")
                return@use NgrRecompilerCatalog.SystemId.XBOX_360
            }
            if (extension in setOf("xex", "con", "live", "pirs")) {
                NgrLogger.warn("recompiler.detect", "Xbox 360 extension with unrecognized header: $name")
            }
            if (offset < 20 || header[0] != 0x7f.toByte() ||
                header[1] != 'E'.code.toByte() || header[2] != 'L'.code.toByte() ||
                header[3] != 'F'.code.toByte()) {
                if (extension in setOf("z64", "n64", "v64")) {
                    NgrLogger.warn("recompiler.detect", "N64 extension but invalid ROM magic: name=$name")
                }
                return@use null
            }
            val littleEndian = header[5].toInt() == 1
            val machine = if (littleEndian) {
                (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
            } else {
                ((header[18].toInt() and 0xff) shl 8) or (header[19].toInt() and 0xff)
            }
            if (machine == 8) NgrRecompilerCatalog.SystemId.N64_MIPS else null
        }
    }.onFailure {
        NgrLogger.error("recompiler.detect", "Format detection failed for uri=$uri", it)
    }.getOrNull()

    private fun queryName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun openN64(uri: Uri) {
        NgrLogger.info("recompiler.detect", "Opening N64 ELF backend for uri=$uri")
        startActivity(Intent(this, N64RecompilerActivity::class.java).apply {
            data = uri
            putExtra(N64RecompilerActivity.EXTRA_INPUT_URI, uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
