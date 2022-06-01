package org.nontrivialpursuit.appelflap.p2pmonitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.peddlenet.LOG_DIR
import org.nontrivialpursuit.appelflap.peddlenet.LOG_FILE_EXTENSION
import java.io.File

class LogFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_p2pmonitor_log_list, container, false)

        // Set the adapter
        if (view is RecyclerView) {
            with(view) {
                layoutManager = LinearLayoutManager(context)
                adapter = LogRecyclerViewAdapter(
                    context,
                    ArrayList(File(context.cacheDir, LOG_DIR).also { it.mkdir() }.takeIf { it.isDirectory and it.exists() }
                                  ?.listFiles({ path: File -> path.name.endsWith(LOG_FILE_EXTENSION) })!!.sorted()
                    )
                )
            }
        }
        return view
    }
}