package org.nontrivialpursuit.appelflap

import org.nontrivialpursuit.ingeblikt.pathjoin
import java.io.File
import java.util.*

val DIGITS = "0123456789".toSet()
const val PROC_DIR = "/proc"


fun pgrep(regex: Regex): List<Int> {
    return File("/proc").listFiles { thefile, thename -> DIGITS.containsAll(thename.toSet()) && thefile.isDirectory }?.map { pid_dir ->
        val namefile = File(pid_dir, "cmdline")
        return@map when (namefile.canRead()) {
            true -> regex.matchEntire(namefile.readText().trim('\u0000'))?.let {
                pid_dir.name.toIntOrNull()
            }
            else -> null
        }
    }?.filterNotNull() ?: LinkedList<Int>()
}


fun pgrep_first(regex: Regex): Int? {
    File("/proc").listFiles { thefile, thename -> DIGITS.containsAll(thename.toSet()) && thefile.isDirectory }?.forEach { pid_dir ->
        File(pid_dir, "cmdline").takeIf {
            it.canRead() && regex.matchEntire(it.readText().trim('\u0000'))?.let { true } ?: false
        }?.let {
            return pid_dir.name.toIntOrNull()
        }
    }
    return null
}


fun waitpid(pid: Int) {
    val procdir = File(pathjoin(PROC_DIR, "${pid}"))
    while (procdir.exists()) {
        Thread.sleep(10)
    }
}