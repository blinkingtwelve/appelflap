package org.nontrivialpursuit.appelflap.p2pmonitor

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.R
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

class LogRecyclerViewAdapter(
        val context: Context, private val values: ArrayList<File>) : RecyclerView.Adapter<LogRecyclerViewAdapter.ViewHolder>() {

    val log = Logger(this::class.java)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_p2pmonitor_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thefile = values[position]
        val timestamp = thefile.name.substringBefore('.').let maketimestamp@{
            it.toLongOrNull()?.let {
                if (Build.VERSION.SDK_INT >= 26) {
                    return@maketimestamp DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it))
                } else {
                    return@maketimestamp Date(it).toString()
                }
            }
        } ?: "???"
        holder.contentView.text = "${timestamp}"
        holder.sizeView.text = "${thefile.length() / 1024} kB"
        holder.shareView.setOnClickListener {
            context.startActivity(
                ShareCompat.IntentBuilder(context).apply {
                    setStream(
                        FileProvider.getUriForFile(
                            context, context.getString(R.string.P2P_LOGFILEPROVIDER_AUTHORITY), thefile
                        )
                    )
                    setChooserTitle("Share log file")
                    setType("application/gzip")
                }.createChooserIntent()
            )
        }

        holder.deleteView.setOnClickListener {
            thefile.delete()
            values.remove(thefile)  // values.removeAt would be O(1) rather than O(N), but because of double-taps, it's unsafe
            notifyItemRemoved(holder.adapterPosition)
            notifyItemRangeChanged(holder.adapterPosition, values.size)
        }
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentView: TextView = view.findViewById(R.id.content)
        val sizeView: TextView = view.findViewById(R.id.size)
        val shareView: FloatingActionButton = view.findViewById(R.id.floatingShareButton)
        val deleteView: FloatingActionButton = view.findViewById(R.id.floatingDeleteButton)

        override fun toString(): String {
            return contentView.text.toString()
        }
    }
}