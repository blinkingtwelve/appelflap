package org.nontrivialpursuit.appelflap.p2pmonitor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.nontrivialpursuit.appelflap.DEBUG_SITEURL_PREFS_NAME
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.SiteUrls
import org.nontrivialpursuit.appelflap.getLocalBroadcastManager
import org.nontrivialpursuit.appelflap.peddlenet.STATUS_INTERVAL_ACTION
import org.nontrivialpursuit.appelflap.peddlenet.services.StatusBroadcaster

class PrefsFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_p2pmonitor_prefs, container, false)
        val statsIntervalView = root.findViewById<EditText>(R.id.stats_interval_enter)
        val wrappedsiteurl = root.findViewById<EditText>(R.id.wrappedsiteurl).also {
            it.setText(SiteUrls(requireContext()).wrapped_site_url)
        }
        val our_domains = root.findViewById<EditText>(R.id.ourdomains).also {
            it.setText(SiteUrls(requireContext()).our_domains.sorted().joinToString(", "))
        }
        val lobroman = getLocalBroadcastManager(requireContext())
        val siteurl_prefs = requireContext().getSharedPreferences(DEBUG_SITEURL_PREFS_NAME, Context.MODE_PRIVATE)

        statsIntervalView.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
                if (setOf(
                        EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_UNSPECIFIED
                    ).contains(actionId)) {
                    // Android 8 sends a EditorInfo.IME_ACTION_UNSPECIFIED
                    lobroman.sendBroadcast(Intent(context, StatusBroadcaster::class.java).also {
                        val thetext = v.text.toString()
                        if (!thetext.isBlank()) {
                            it.putExtra("status_interval", thetext.toLongOrNull())
                            it.action = STATUS_INTERVAL_ACTION
                        }
                    })
                    v.text = ""
                    Toast.makeText(
                        context!!, "Interval set. Check status pane.", Toast.LENGTH_LONG
                    )
                    return true
                }
                return false
            }
        })

        wrappedsiteurl.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
                if (setOf(
                        EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_UNSPECIFIED
                    ).contains(actionId)) {
                    val siteurl = v.text.toString()
                    if (siteurl.isBlank()) {
                        siteurl_prefs.edit().remove("wrapped_site_url").commit()
                    } else {
                        siteurl_prefs.edit().putString("wrapped_site_url", siteurl.trim()).commit()
                    }
                    v.text = SiteUrls(requireContext()).wrapped_site_url
                    return true
                }
                return false
            }
        })

        our_domains.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
                if (setOf(
                        EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_UNSPECIFIED
                    ).contains(actionId)) {
                    val ourdomains = v.text.toString().split(",").map { it.trim() }.filter { it.length > 0 }
                    if (ourdomains.isEmpty()) {
                        siteurl_prefs.edit().remove("our_domains").commit()
                    } else {
                        siteurl_prefs.edit().putStringSet("our_domains", ourdomains.toSet()).commit()
                    }
                    v.text = SiteUrls(requireContext()).our_domains.sorted().joinToString(", ")
                    return true
                }
                return false
            }
        })


        return root
    }
}