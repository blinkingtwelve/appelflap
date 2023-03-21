package org.nontrivialpursuit.ingeblikt

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

open class KoekoekException(msg: String) : Exception(msg)


fun pathjoin(components: List<String>): String {
    return pathjoin(*components.toTypedArray())
}

fun pathjoin(vararg components: String): String {
    return components.joinToString(File.separator)
}

fun pathjoin(vararg components: File): File {
    return components.reduce { acc, appendfile -> File(acc, appendfile.toString()) }
}

fun ByteArray.md5(): ByteArray {
    return MessageDigest.getInstance("MD5").digest(this)!!
}

enum class MEAL_SIZE {
    NOT_ENOUGH,
    RIGHT_ON,
    TOO_MUCH,
}


class ProgressCallbackDampener(val every: Long, val callback: (Long, Long?) -> Unit) {
    var chunk_cnt = 0L
    fun progress_callback(read_sofar: Long, max: Long?) {
        if (((max != null) && (read_sofar >= max)) || ((read_sofar / every) > chunk_cnt)){
            chunk_cnt++
            callback(read_sofar, max)
        }
    }
}


fun OutputStream.slurp(
        sin: InputStream,
        max_to_read: Long? = null,
        digester: MessageDigest? = null,
        progress_callback: ((Long, Long?) -> Unit)? = null): MEAL_SIZE {
    val txbuf = ByteArray(BUFSIZE)
    var read_sofar = 0L
    while (max_to_read?.let { read_sofar < it } != false) {
        val amt_read = sin.read(txbuf,
                                0,
                                max_to_read?.let { (it - read_sofar).toInt().let { if (it < BUFSIZE) it else BUFSIZE } } ?: BUFSIZE)
        if (amt_read == -1) break
        digester?.update(txbuf, 0, amt_read)
        this.write(txbuf, 0, amt_read)
        read_sofar += amt_read
        progress_callback?.invoke(read_sofar, max_to_read)
    }
    return when ((sin.read() == -1) to (max_to_read == null || max_to_read == read_sofar)) { // no data left to read
        true to true -> MEAL_SIZE.RIGHT_ON
        true to false -> MEAL_SIZE.NOT_ENOUGH // data left to read
        false to true -> MEAL_SIZE.TOO_MUCH
        else -> null
    } ?: throw RuntimeException("But that's impossible!")
}

fun Semaphore.runWith(run: Runnable, nb_permits: Int = 1, block: Boolean = true): Boolean {
    try {
        if (block) {
            this.acquire(nb_permits)
        } else {
            if (!this.tryAcquire(nb_permits)) return false
        }
        run.run()
        return true
    } finally {
        this.release(nb_permits)
    }
}

object Lockchest {
    // singleton for easy sharing of named 1-permit semaphores across threads
    // semaphores are not GCed when no longer referenced, so don't create zillions
    private val sems = ConcurrentHashMap<String, Semaphore>()
    fun acquire(name: String) {
        sems.getOrPut(name, { Semaphore(1) }).acquire()
    }

    fun release(name: String) {
        sems.getOrPut(name, { Semaphore(1) }).release()
    }

    fun tryAcquire(name: String): Boolean {
        return sems.getOrPut(name, { Semaphore(1) }).tryAcquire()
    }

    fun get(name: String): Semaphore {
        return sems.getOrPut(name, { Semaphore(1) })
    }

    fun status(): Map<String, Pair<Int, Int>> {
        return sems.map { it.key to (it.value.availablePermits() to it.value.queueLength) }.associate { it }
    }

    fun unClog(): Map<String, Int> {
        return sems.map { it.key to it.value.queueLength.also { ql -> it.value.release(ql) } }.associate { it }
    }

    fun runWith(name: String, run: Runnable, nb_permits: Int = 1, block: Boolean = true) {
        this.get(name).runWith(run, nb_permits = nb_permits, block = block)
    }
}

fun copyConsistent(src: File, dest: File? = null, hash: ByteArray? = null): File {
    // Copies a file until it's read with the same result twice in a row (i.e. no smear)
    val outfile = dest ?: File.createTempFile("${src.name}", ".tmp_copy", src.parentFile)
    val hasher = MessageDigest.getInstance("md5")
    src.inputStream().buffered().use { instream ->
        outfile.outputStream().buffered().use { outstream ->
            outstream.slurp(instream, null, hasher)
            outstream.flush()
        }
    }
    val thehash = hasher.digest()
    return when (hash.contentEquals(thehash)) {
        true -> outfile
        false -> {
            if (dest == null) outfile.delete()  // clean up tempfile
            copyConsistent(src, dest, thehash)
        }
    }
}

class DevNullStream : OutputStream() {
    override fun write(p0: Int) {}
    override fun write(b: ByteArray) {}
    override fun write(b: ByteArray, off: Int, len: Int) {}
}

fun File.hashContents(hasher: MessageDigest = MessageDigest.getInstance("MD5")): ByteArray {
    DevNullStream().slurp(this.inputStream().buffered(), null, hasher)
    return hasher.digest()
}