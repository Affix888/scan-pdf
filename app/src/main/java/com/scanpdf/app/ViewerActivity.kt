package com.scanpdf.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scanpdf.app.databinding.ActivityViewerBinding
import com.scanpdf.app.databinding.ItemPageBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Penampil PDF bawaan (pakai PdfRenderer Android, tanpa aplikasi lain). */
class ViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PATH = "path"
        fun intent(context: Context, file: File) =
            Intent(context, ViewerActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)
    }

    private lateinit var binding: ActivityViewerBinding
    private lateinit var file: File
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    // PdfRenderer tidak thread-safe: semua render dijalankan di satu thread.
    private val executor = Executors.newSingleThreadExecutor()
    private val renderDispatcher = executor.asCoroutineDispatcher()

    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        file = File(intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return })
        title = file.nameWithoutExtension

        val sizes = try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = fd
            val r = PdfRenderer(fd)
            renderer = r
            (0 until r.pageCount).map { i ->
                val page = r.openPage(i)
                try { page.width to page.height } finally { page.close() }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val density = resources.displayMetrics.density
        val targetWidth = (resources.displayMetrics.widthPixels - (24 * density).toInt()).coerceIn(200, 1600)

        binding.pages.layoutManager = LinearLayoutManager(this)
        binding.pages.adapter = PageAdapter(sizes, targetWidth)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share -> { FileActions.share(this, file); true }
        R.id.action_open_with -> { FileActions.openExternal(this, file); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        val r = renderer
        val fd = pfd
        executor.execute {
            runCatching { r?.close() }
            runCatching { fd?.close() }
        }
        executor.shutdown()
    }

    private fun renderPage(index: Int, width: Int): Bitmap? {
        val r = renderer ?: return null
        return try {
            val page = r.openPage(index)
            try {
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    class PageVH(val b: ItemPageBinding) : RecyclerView.ViewHolder(b.root) {
        var job: Job? = null
    }

    inner class PageAdapter(
        private val sizes: List<Pair<Int, Int>>,
        private val targetWidth: Int,
    ) : RecyclerView.Adapter<PageVH>() {

        override fun getItemCount() = sizes.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PageVH(ItemPageBinding.inflate(layoutInflater, parent, false))

        override fun onBindViewHolder(h: PageVH, position: Int) {
            val (w, ht) = sizes[position]
            h.b.image.layoutParams = h.b.image.layoutParams.apply {
                height = (targetWidth.toFloat() * ht / w).toInt()
            }
            h.b.pageNumber.text = getString(R.string.page_of, position + 1, sizes.size)

            h.job?.cancel()
            val cached = cache.get(position)
            if (cached != null) {
                h.b.image.setImageBitmap(cached)
                return
            }
            h.b.image.setImageDrawable(null)
            h.job = lifecycleScope.launch {
                val bmp = withContext(renderDispatcher) { renderPage(position, targetWidth) }
                if (bmp != null) {
                    cache.put(position, bmp)
                    h.b.image.setImageBitmap(bmp)
                }
            }
        }
    }
}
