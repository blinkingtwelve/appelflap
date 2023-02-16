@file:Suppress("DEPRECATION")  // We love the deprecated LocalBroadcastManager for all the reasons that it's deprecated for.

package org.nontrivialpursuit.appelflap

import android.content.Context
import androidx.localbroadcastmanager.content.LocalBroadcastManager

fun getLocalBroadcastManager(context: Context): LocalBroadcastManager {
    return LocalBroadcastManager.getInstance(context)
}