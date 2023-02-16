package org.nontrivialpursuit.appelflap

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.nontrivialpursuit.ingeblikt.*
import java.io.InputStream
import java.math.BigInteger
import java.text.SimpleDateFormat


class BundlefileOpener : AppCompatActivity() {

    var depth = 0
    var maestrosity: Maestrosity? = null
    var should_reboot = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.window.setFullScreenFlags()
        this.supportActionBar?.hide()
        this.window.setBackgroundDrawableResource(android.R.color.black)

        kotlin.runCatching {
            getInputStream()?.use { istream ->
                DumpfileUnpacker.verifyDump(istream, AndroidPKIOps(this, BuildConfig.APPLICATION_ID))
            }
        }.fold({ results ->
                           results?.also {
                               maestrosity = Maestrosity(this, 12_000L).apply { start() }
                               should_reboot = (it.first.first.serviceworkerDescriptor != null)
                               AlertDialog.Builder(this).setTitle("Appelflap Bundle details").setMessage(templateBundleDetails(it))
                                   .setPositiveButton("Load & publish", onClick(this)).setNeutralButton("Publish", onClick(this))
                                   .setNegativeButton("Cancel", onClick(this)).setOnDismissListener { if (depth == 0) finish() }.create()
                                   .show()
                           }
                       }, { the_err ->
                           AlertDialog.Builder(this).setTitle("Appelflap Bundle error")
                               .setMessage("There is something wrong with this Appelflap bundle:\n\n${the_err}")
                               .setOnDismissListener { this.finish() }.create().show()
                       })

    }

    fun getInputStream(): InputStream? {
        return intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.let {
            contentResolver.openInputStream(it)
        }
    }


    class onClick(val bfo: BundlefileOpener) : DialogInterface.OnClickListener {

        fun reportOutcome(title: String, message: String = "", reboot_on_dismiss: Boolean = false) {
            AlertDialog.Builder(bfo as Context).setTitle(title).setMessage(message).setOnDismissListener({
                                                                                                             if (reboot_on_dismiss) Appelflap.get(
                                                                                                                 bfo
                                                                                                             ).relaunchViaTrampoline()
                                                                                                             bfo.finish()
                                                                                                         }).create().show()
        }

        override fun onClick(dialog: DialogInterface?, which: Int) {
            bfo.depth++
            when (which) {
                DialogInterface.BUTTON_NEUTRAL -> {
                    // Publish
                    bfo.getInputStream()?.use {
                        val grm = GeckoRuntimeManager.getInstance(Appelflap.get(bfo), null)
                        kotlin.runCatching { grm.koekoeksNest.addBundle(it) }
                            .fold({ reportOutcome("Appelflap Bundle publication successful") }, {
                                reportOutcome(
                                    "Appelflap Bundle publication failure", "${it.message}"
                                )
                            })
                    }
                }
                DialogInterface.BUTTON_POSITIVE -> {
                    // Load and publish
                    val grm = GeckoRuntimeManager.getInstance(Appelflap.get(bfo), null)
                    kotlin.runCatching {
                        bfo.getInputStream()?.use {
                            grm.inject(it, honor_injectionlock = false)
                        }
                        bfo.getInputStream()?.use {
                            grm.koekoeksNest.saveSubscriptionRecord(grm.koekoeksNest.addBundle(it), RECORD_INJECTED or RECORD_PUBLISHED)
                        }
                    }.fold({ reportOutcome("Appelflap Bundle loading & publication successful", reboot_on_dismiss = bfo.should_reboot) }, {
                        reportOutcome(
                            "Appelflap Bundle loading & publication failure", "${it.message}"
                        )
                    })
                }
                else -> {
                    bfo.maestrosity?.stop()
                    bfo.finish()
                }
            }
        }
    }

    override fun onStop() {
        maestrosity?.stop()
        super.onStop()
    }


    fun templateBundleDetails(outcome: Pair<Triple<DumpDescriptor, CacheSecurityInfoMap, Pair<String, BigInteger>>, Int>): String {
        val (deets, entry_count) = outcome
        val (dumpdesc, _, certinfo) = deets
        val bundletype = dumpdesc.serviceworkerDescriptor?.let { "Service worker" } ?: "Cache"
        val (timestamp, timestamp_numeric) = dumpdesc.cacheDescriptor.last_server_timestamp?.let {
            SimpleDateFormat("y-MM-dd HH:mm:ss Z").format(
                it * 1000
            ) to it.toString()
        } ?: "Unknown" to "Unknown"
        return """
            Type:
                ${bundletype}
            Name:
                ${dumpdesc.name}
            Origin:
                ${dumpdesc.cacheDescriptor.origin}
            Version:
                ${dumpdesc.cacheDescriptor.version}
            
            Number of entries:
                ${entry_count}
            Last server timestamp:
                ${timestamp}
            Last server timestamp (numeric):
                ${timestamp_numeric}
            
            Generated by peer:
                ${certinfo.first}
            With certificate ID:
                ${certinfo.second}
        """.trimIndent()
    }
}