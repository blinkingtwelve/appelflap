package org.nontrivialpursuit.eekhoorn

import java.io.File
import org.eclipse.jetty.server.Response

fun createTempdir(basedir: File, prefix: String): File {
    var counter = 0
    while (counter < 10e4) {
        val tempDir = File(basedir, "$prefix.$counter")
        if (tempDir.mkdir()) {
            return tempDir
        }
        counter++
    }
    throw IllegalStateException("Failed to create tempdir")
}

fun Response.sendErrorText(code: Int, message: String?) {
    this.setStatus(code)
    message?.also {
        this.contentType = TEXT_HTTP_CONTENTTYPE
        this.outputStream.writer().use { out ->
            out.write(it)
        }
    }
    this.closeOutput()
}

fun Response.sendErrorText(code: Int) {
    this.sendErrorText(code, null)
}