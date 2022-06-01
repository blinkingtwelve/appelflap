package org.nontrivialpursuit.appelflap.webwrap

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import org.nontrivialpursuit.appelflap.Appelflap
import org.nontrivialpursuit.appelflap.R


fun createStateZapDialog(context: Context): Dialog {
    val selectedItems = HashSet<Int>()
    return AlertDialog.Builder(context).setTitle(
        String.format(
            context.resources.getString(R.string.zap_dialog_message), context.resources.getString(R.string.WEBWRAP_TITLE)
        )
    ).setMultiChoiceItems(R.array.zap_dialog_reset_items, null) { _: DialogInterface?, which, isChecked ->
        when (isChecked) {
            true -> selectedItems.add(which)
            false -> selectedItems.remove(which)
        }
    }.setPositiveButton(R.string.zap_dialog_positive) { _: DialogInterface?, _: Int ->
        if (selectedItems.isNotEmpty()) {
            val app = Appelflap.get(context)
            if (1 in selectedItems) {
                app.koekoeksNest.clearSubscriptions()
            }
            if (2 in selectedItems) {
                app.koekoeksNest.clearBundles()
            }
            if (3 in selectedItems) {
                app.koekoeksNest.pkiOps.deleteChain()
            }
            if (0 in selectedItems) {
                app.geckoRuntimeManager?.wrapper?.also { wrapper ->
                    wrapper.geckoSession?.also { sesh ->
                        sesh.close()
                        wrapper.geckoSession = null
                    }
                    app.geckoRuntimeManager?.apply {
                        mozdir.deleteRecursively()
                    }
                    app.relaunchViaTrampoline()
                }
            }
        }
    }.setNegativeButton(R.string.zap_dialog_negative) { _: DialogInterface?, _: Int ->
    }.create()
}

fun createShutdownDialog(context: Context): AlertDialog {
    val adbuilder = AlertDialog.Builder(context)
    adbuilder.setMessage(
        String.format(
            context.resources.getString(R.string.shutdown_dialog_message), context.resources.getString(R.string.WEBWRAP_TITLE)
        )
    ).setPositiveButton(
        R.string.shutdown_dialog_positive
    ) { _: DialogInterface?, _: Int ->
        Appelflap.get(context).geckoRuntimeManager?.shutdown()
    }.setNegativeButton(
        R.string.shutdown_dialog_negative
    ) { _: DialogInterface?, _: Int ->
    }
    return adbuilder.create()
}
