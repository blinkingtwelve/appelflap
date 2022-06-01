package org.nontrivialpursuit.appelflap.p2pmonitor

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.nontrivialpursuit.appelflap.BuildConfig
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.appelflap.peddlenet.ConductorState
import org.nontrivialpursuit.appelflap.peddlenet.FORCE_MODESWITCH_ACTION
import org.nontrivialpursuit.appelflap.peddlenet.SetStatusReceiver
import org.nontrivialpursuit.appelflap.peddlenet.TENPLUS

class PreflightFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_p2pmonitor_preflight, container, false)
        val textView = root.findViewById<TextView>(R.id.text_home)
        val wifiman = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val thumbsup = "\uD83D\uDC4D"
        val thumbsdown = "\uD83D\uDC4E"
        val has_p2p_hardware = wifiman.isP2pSupported
        val wifi_is_on = wifiman.isWifiEnabled
        val p2p_featureflag_is_on = BuildConfig.WIFI_P2P_ENABLED
        val lobroman = LocalBroadcastManager.getInstance(requireContext())

        val build_text = when (TENPLUS) {
            true -> "${thumbsup} You're on Android 10+."
            false -> "${thumbsdown} Your Android does not have a modern enough version of the WiFi-Direct APIs. You need Android 10+."
        }

        val featureflag_text = when (p2p_featureflag_is_on) {
            true -> "${thumbsup} Your Appelflap product flavour has the Wifi-P2P feature flag enabled."
            false -> "${thumbsdown} Your Appelflap product flavour does not have the Wifi-P2P feature flag enabled."
        }

        val wifi_text = when (wifi_is_on) {
            true -> when (has_p2p_hardware) {
                true -> "${thumbsup} You have WiFi-Direct capable hardware."
                false -> "${thumbsdown} You do not have WiFi-Direct capable hardware."
            }
            false -> """${thumbsdown} Wi-Fi is off. Turn it on already!"""
        }

        val conclusion_text = when (TENPLUS && has_p2p_hardware && p2p_featureflag_is_on) {
            true -> """You're good to go. You might want to turn off ("forget") some WiFi networks to avoid latching on to peers found through there if you want to exclusively test the P2P networking."""
            false -> when (wifi_is_on) {
                true -> """There were some ${thumbsdown}. The app will still work — in the sense that it will communicate with peers, but it will only be able to do so on existing networks (eg: your WiFi), and will not be able to find, connect to, or create P2P networks by itself. 😿"""
                false -> """You really need to turn on your WiFi 😾"""
            }
        }

        textView.also({
                          it.text = """
            Flight check — Android version:
            ${build_text}
            
            Flight check — Appelflap build feature flags:
            ${featureflag_text}
            
            Flight check — WiFi/P2P hardware capability:
            ${wifi_text}
             
            Conclusion:
            ${conclusion_text}
            """.trimIndent()
                      })

        setOf(
            R.id.button_force_hosting, R.id.button_force_joining, R.id.button_force_lurking
        ).forEach {
            if (TENPLUS) {
                root.findViewById<Button>(it).setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View) {
                        val desired_state = when (v.id) {
                            R.id.button_force_hosting -> ConductorState.HOSTING
                            R.id.button_force_joining -> ConductorState.JOINING
                            R.id.button_force_lurking -> ConductorState.LURKING
                            else -> null
                        }
                        desired_state?.also {
                            lobroman.sendBroadcast(Intent(
                                context, SetStatusReceiver::class.java
                            ).also {
                                it.putExtra("desired_state", desired_state.name)
                                it.action = FORCE_MODESWITCH_ACTION
                            })
                        }
                    }
                })
            } else {
                root.findViewById<Button>(it).also {
                    it.isEnabled = false
                    it.visibility = View.GONE
                }
            }
        }
        return root
    }
}