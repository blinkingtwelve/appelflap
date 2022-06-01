package org.nontrivialpursuit.appelflap.peddlenet

import org.nontrivialpursuit.appelflap.Logger

interface ServiceHandler {
    fun start(): Boolean
    fun stop()
    fun restart()
    val log: Logger
    var is_running: Boolean
}