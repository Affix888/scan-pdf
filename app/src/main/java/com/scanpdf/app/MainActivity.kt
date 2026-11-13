package com.scanpdf.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.scanpdf.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ScanAdapter
    private val scansDir by lazy { FileActions.scansDir(this) }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scan?.pdf?.uri
            if (pdfUri != null) savePdf(pdfUri) else toast(getString(R.string.scan_failed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = ScanAdapter(
            scope = lifecycleScope,
            onOpen = { file -> openInViewer(file) },
            onMenu = { file, anchor -> showItemMenu(file, anchor) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fabScan.setOnClickListener { startScan() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.scanner_unavailable)
                    .setMessage(getString(R.string.scanner_unavailable_msg, e.localizedMessage ?: ""))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
    }

    private fun savePdf(source: Uri) {
        lifecycleScope.launch {
            val name = "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".pdf"
            val file = File(scansDir, name)
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(source)!!.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }.isSuccess
            }
            if (!copied) {
                toast(getString(R.string.save_failed))
                return@launch
            }
            // Otomatis juga disimpan ke folder Download/ScanPDF (Android 10+)
            val exported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                withContext(Dispatchers.IO) { FileActions.exportToDownloads(this@MainActivity, file) }
            } else false

            refresh()
            val msg = if (exported) getString(R.string.saved_with_download, name)
            else getString(R.string.saved_as, name)
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.fabScan)
                .setAction(R.string.open) { openInViewer(file) }
                .show()
        }
    }

    private fun refresh() {
        val files = scansDir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        adapter.submit(files)
        binding.emptyView.isVisible = files.isEmpty()
    }

    private fun openInViewer(file: File) = startActivity(ViewerActivity.intent(this, file))

    private fun showItemMenu(file: File, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.item_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open -> openInViewer(file)
                R.id.action_open_with -> FileActions.openExternal(this, file)
                R.id.action_share -> FileActions.share(this, file)
                R.id.action_rename -> rename(file)
                R.id.action_download -> saveToDownloads(file)
                R.id.action_delete -> confirmDelete(file)
            }
            true
        }
        popup.show()
    }

    private fun saveToDownloads(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast(getString(R.string.download_unsupported))
            FileActions.share(this, file)
            return
        }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { FileActions.exportToDownloads(this@MainActivity, file) }
            toast(getString(if (ok) R.string.download_ok else R.string.save_failed))
        }
    }

    private fun rename(file: File) {
        val input = EditText(this).apply {
            setText(file.nameWithoutExtension)
            setSingleLine()
            selectAll()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                if (newName.isEmpty()) return@setPositiveButton
                val target = File(scansDir, "$newName.pdf")
                when {
                    target == file -> Unit
                    target.exists() -> toast(getString(R.string.name_exists))
                    file.renameTo(target) -> refresh()
                    else -> toast(getString(R.string.rename_failed))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_confirm, file.nameWithoutExtension))
            .setPositiveButton(R.string.delete) { _, _ ->
                file.delete()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
