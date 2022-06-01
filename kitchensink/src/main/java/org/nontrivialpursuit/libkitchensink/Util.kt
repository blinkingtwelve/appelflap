package org.nontrivialpursuit.libkitchensink

import java.io.InputStream
import java.io.OutputStream

fun streamStreamToStream(inStream: InputStream, outStream: OutputStream) {
    val buf = ByteArray(4096)
    var amt_read: Int
    while (inStream.read(buf).also { amt_read = it } != -1) {
        outStream.write(buf, 0, amt_read)
    }
    outStream.close()
    inStream.close()
}


fun ByteArray.hexlify() = joinToString("") { "%02x".format(it) }

fun String.launder(allowed: Regex): String {
    return this.toCharArray().filter { allowed.matches(it.toString()) }.joinToString("")
}

fun String.percent_encode(): String {
    val digits_range = 0x30..0x39
    val caps_range = 0x41..0x5A
    val letters_range = 0x61..0x7a

    fun tran(c: Char): String {
        val cp = c.toInt()
        return when (cp) {
            in digits_range, in caps_range, in letters_range -> "$c"
            else -> "%%%02X".format(cp)
        }
    }
    return this.toCharArray().map(::tran).joinToString("")
}