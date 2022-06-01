package org.nontrivialpursuit.eekhoorn.interfaces

import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge

interface HttpEekhoornInterface {
    fun shutdown()
    fun get_portno(): Int
    val credentials: Triple<String, String, String>
    var appelflapBridge: AppelflapBridge
    val koekoekBridge: KoekoekBridge
}