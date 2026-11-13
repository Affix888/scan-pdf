package com.scanpdf.app

import android.graphics.Bitmap
import android.text.format.Formatter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.scanpdf.app.databinding.ItemScanBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class ScanAdapter(
    private val scope: CoroutineScope,
    private val onOpen: (File) -> Unit,
    private val onMenu: (File, View) -> Unit,
) : RecyclerView.Adapter<ScanAdapter.VH>() {

    data class Thumb(val bitmap: Bitmap, val pages: Int)

    class VH(val b: ItemScanBinding) : RecyclerView.ViewHolder(b.root) {
        var job: Job? = null
    }

    private var items: List<File> = emptyList()
    private val thumbCache = LruCache<String, Thumb>(60)

    fun submit(list: List<File>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemScanBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val file = items[position]
        val ctx = h.itemView.context
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(file.lastModified()))
        val size = Formatter.formatShortFileSize(ctx, file.length())

        h.b.title.text = file.nameWithoutExtension
        h.b.root.setOnClickListener { onOpen(file) }
        h.b.more.setOnClickListener { onMenu(file, it) }

        h.job?.cancel()
        val key = file.absolutePath + ":" + file.lastModified()
        val cached = thumbCache.get(key)
        if (cached != null) {
            show(h, cached, date, size)
            return
        }
        h.b.thumb.setImageDrawable(null)
        h.b.subtitle.text = "$date • $size"
        h.job = scope.launch {
            val result = withContext(Dispatchers.IO) { FileActions.renderFirstPage(file, 240) }
            if (result != null) {
                val thumb = Thumb(result.first, result.second)
                thumbCache.put(key, thumb)
                show(h, thumb, date, size)
            }
        }
    }

    private fun show(h: VH, t: Thumb, date: String, size: String) {
        h.b.thumb.setImageBitmap(t.bitmap)
        val pages = h.itemView.resources.getQuantityString(R.plurals.pages, t.pages, t.pages)
        h.b.subtitle.text = "$pages • $date • $size"
    }
}
