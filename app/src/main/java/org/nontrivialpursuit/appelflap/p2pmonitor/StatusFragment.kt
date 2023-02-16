package org.nontrivialpursuit.appelflap.p2pmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.nontrivialpursuit.appelflap.Logger
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.getLocalBroadcastManager
import org.nontrivialpursuit.appelflap.peddlenet.STATUS_PUSH_ACTION

class StatusReceiver(val statfrag: StatusFragment) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        statfrag.statusview?.text = intent.getStringExtra("status")
    }
}


class StatusFragment : Fragment() {

    var statusview: TextView? = null
    val log = Logger(this::class.java)

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_p2pmonitor_status, container, false)
        this.statusview = root.findViewById(R.id.text_status)
        this.statusview?.text = "Hold on for stats to appear. If you have not enabled stats broadcasting, you will be waiting for quite a while."
        getLocalBroadcastManager(requireContext()).registerReceiver(StatusReceiver(this), IntentFilter(STATUS_PUSH_ACTION))
        return root
    }

}