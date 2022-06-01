package org.nontrivialpursuit.appelflap

import org.nontrivialpursuit.eekhoorn.interfaces.HttpEekhoornInterface
import org.nontrivialpursuit.eekhoorn.interfaces.KoekoekBridge
import org.nontrivialpursuit.eekhoorn.interfaces.KoekoekStub
import org.nontrivialpursuit.ingeblikt.AppelflapStub
import org.nontrivialpursuit.ingeblikt.interfaces.AppelflapBridge

// For Appelflap build flavours that are no more than a simple website wrapper
class EekhoornNop : HttpEekhoornInterface {
    override fun shutdown() {
    }

    override fun get_portno(): Int {
        return 0
    }

    override val credentials = Triple("nop", "nop", "nop")
    override var appelflapBridge: AppelflapBridge = AppelflapStub()
    override val koekoekBridge: KoekoekBridge = KoekoekStub()
}