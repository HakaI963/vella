package com.vella.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private val ARCH_LABELS = arrayOf("x86-64", "x86 (32-bit)", "ARMv7", "AArch64")
        private val ARCH_IDS = arrayOf(
            "x86:LE:64:default",
            "x86:LE:32:default",
            "ARM:LE:32:v7",
            "AARCH64:LE:64:v8A"
        )
        private const val DEFAULT_SIZE = 0x4000L
    }

    private lateinit var pickButton: Button
    private lateinit var decompileButton: Button
    private lateinit var copyButton: Button
    private lateinit var shareButton: Button
    private lateinit var saveButton: Button
    private lateinit var archSpinner: Spinner
    private lateinit var baseInput: EditText
    private lateinit var entryInput: EditText
    private lateinit var sizeInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var fileText: TextView
    private lateinit var statusText: TextView
    private lateinit var outputText: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var progress: ProgressBar

    private var inputFile: File? = null
    private var lastOutput: String = ""

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadFile(uri)
    }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-c")) { uri ->
            if (uri != null && lastOutput.isNotEmpty()) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(lastOutput.toByteArray()) }
                }.onFailure { toast("Could not save: ${it.message}") }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pickButton = findViewById(R.id.pickButton)
        decompileButton = findViewById(R.id.decompileButton)
        copyButton = findViewById(R.id.copyButton)
        shareButton = findViewById(R.id.shareButton)
        saveButton = findViewById(R.id.saveButton)
        archSpinner = findViewById(R.id.archSpinner)
        baseInput = findViewById(R.id.baseInput)
        entryInput = findViewById(R.id.entryInput)
        sizeInput = findViewById(R.id.sizeInput)
        nameInput = findViewById(R.id.nameInput)
        fileText = findViewById(R.id.fileText)
        statusText = findViewById(R.id.statusText)
        outputText = findViewById(R.id.outputText)
        outputScroll = findViewById(R.id.outputScroll)
        progress = findViewById(R.id.progress)

        archSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ARCH_LABELS
        )

        pickButton.setOnClickListener { picker.launch(arrayOf("*/*")) }
        decompileButton.setOnClickListener { startDecompile() }
        copyButton.setOnClickListener { copyOutput() }
        shareButton.setOnClickListener { shareOutput() }
        saveButton.setOnClickListener {
            if (lastOutput.isNotEmpty()) createDocument.launch("vella.c")
        }
    }

    private fun loadFile(uri: Uri) {
        val displayName = displayName(uri)
        lifecycleScope.launch {
            statusText.text = "Reading $displayName…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(cacheDir, "input.bin")
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Cannot open input stream")
                    target
                }
            }
            val file = result.getOrElse {
                statusText.text = "Could not read file"
                toast(it.message ?: "Could not read file")
                return@launch
            }

            inputFile = file
            fileText.text = "$displayName • ${formatSize(file.length())}"

            val info = withContext(Dispatchers.IO) { Elf.parse(file) }
            if (info != null) {
                info.language?.let { language ->
                    val index = ARCH_IDS.indexOf(language)
                    if (index >= 0) archSpinner.setSelection(index)
                }
                baseInput.setText(hex(info.base))
                val entryOffset = (info.entry - info.base).coerceAtLeast(0)
                entryInput.setText(hex(entryOffset))
                val remaining = (file.length() - entryOffset).coerceAtLeast(16)
                sizeInput.setText(hex(minOf(remaining, DEFAULT_SIZE)))
                statusText.text =
                    "ELF ${info.bits}-bit • base ${hex(info.base)} • entry ${hex(info.entry)}"
            } else {
                baseInput.setText("0x100000")
                entryInput.setText("0x0")
                sizeInput.setText(hex(minOf(file.length(), DEFAULT_SIZE)))
                statusText.text = "Raw binary • ${formatSize(file.length())}"
            }

            decompileButton.isEnabled = true
            outputText.text = getString(R.string.no_output)
            lastOutput = ""
            setOutputActions(false)
        }
    }

    private fun startDecompile() {
        val file = inputFile ?: return
        val base = parseHex(baseInput.text.toString()) ?: return bad("base address")
        val entry = parseHex(entryInput.text.toString()) ?: return bad("entry offset")
        val requested = parseHex(sizeInput.text.toString()) ?: DEFAULT_SIZE
        val name = nameInput.text.toString().trim().ifEmpty { "entry" }

        if (entry < 0 || entry >= file.length()) {
            return bad("entry offset (outside file)")
        }

        val size = requested.coerceIn(16L, (file.length() - entry).coerceAtLeast(16L))
        val language = ARCH_IDS[archSpinner.selectedItemPosition]

        setBusy(true)
        lastOutput = ""
        outputText.text = ""
        statusText.text = getString(R.string.decompiling)

        lifecycleScope.launch {
            val started = SystemClock.elapsedRealtime()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val specDir = Assets.ensure(this@MainActivity)
                    Native.decompile(
                        file.absolutePath,
                        language,
                        base,
                        entry,
                        size,
                        name,
                        specDir.absolutePath
                    )
                }.getOrElse { "ERROR: ${it.message ?: it.javaClass.simpleName}" }
            }
            val elapsed = SystemClock.elapsedRealtime() - started

            setBusy(false)
            lastOutput = result
            outputText.text = result
            outputScroll.scrollTo(0, 0)

            if (result.startsWith("ERROR:")) {
                statusText.text = result.lineSequence().first()
                setOutputActions(false)
            } else {
                statusText.text =
                    "Decompiled $size bytes in $elapsed ms • ${formatSize(result.toByteArray().size.toLong())}"
                setOutputActions(true)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        decompileButton.isEnabled = !busy && inputFile != null
        pickButton.isEnabled = !busy
    }

    private fun setOutputActions(enabled: Boolean) {
        copyButton.isEnabled = enabled
        shareButton.isEnabled = enabled
        saveButton.isEnabled = enabled
    }

    private fun copyOutput() {
        if (lastOutput.isEmpty()) return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Vella", lastOutput))
        toast("Copied")
    }

    private fun shareOutput() {
        if (lastOutput.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Vella decompilation")
            putExtra(Intent.EXTRA_TEXT, lastOutput)
        }
        startActivity(Intent.createChooser(intent, "Share decompilation"))
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment ?: "input.bin"
    }

    private fun parseHex(raw: String): Long? {
        val text = raw.trim().removePrefix("0x").removePrefix("0X").replace("_", "")
        if (text.isEmpty()) return null
        return text.toLongOrNull(16)
    }

    private fun hex(value: Long): String = String.format(Locale.US, "0x%x", value)

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun bad(field: String) {
        statusText.text = "Invalid $field"
        toast("Invalid $field")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
