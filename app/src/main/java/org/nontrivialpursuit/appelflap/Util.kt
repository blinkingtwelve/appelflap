@file:JvmName("Util")

package org.nontrivialpursuit.appelflap

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.webkit.URLUtil
import org.nontrivialpursuit.eekhoorn.createTempdir
import org.nontrivialpursuit.ingeblikt.slurp
import org.nontrivialpursuit.libkitchensink.streamStreamToStream
import java.io.File
import java.io.InputStream
import java.util.*

class Logger(forClass: Any) {
    val classname = forClass::class.qualifiedName

    fun d(msg: String) {
        Log.d(classname, msg)
    }

    fun d(msg: String, tr: Throwable?) {
        Log.d(classname, msg, tr)
    }

    fun e(msg: String) {
        Log.e(classname, msg)
    }

    fun e(msg: String, tr: Throwable?) {
        Log.e(classname, msg, tr)
    }

    fun i(msg: String) {
        Log.i(classname, msg)
    }

    fun i(msg: String, tr: Throwable?) {
        Log.i(classname, msg, tr)
    }

    fun v(msg: String) {
        Log.v(classname, msg)
    }

    fun v(msg: String, tr: Throwable?) {
        Log.v(classname, msg, tr)
    }

    fun w(msg: String) {
        Log.w(classname, msg)
    }

    fun w(msg: String, tr: Throwable?) {
        Log.w(classname, msg, tr)
    }

    fun wtf(msg: String) {
        Log.wtf(classname, msg)
    }

    fun wtf(msg: String, tr: Throwable?) {
        Log.wtf(classname, msg, tr)
    }
}

fun streamToString(theinput: InputStream): String {
    val scanner = Scanner(theinput).useDelimiter("\\A")
    return if (scanner.hasNext()) scanner.next() else ""
}

fun AssetManager.deepcopy(sourcepath: String, destdir: File) {
    this.list(sourcepath)?.forEach {
        when (this.list(File(sourcepath, it).toString())!!.size) {
            0 -> streamStreamToStream(this.open(File(sourcepath, it).toString()), File(destdir, it).outputStream())
            else -> this.deepcopy(File(sourcepath, it).toString(), File(destdir, it).also { it.mkdir() })
        }
    }
}

@Suppress("DEPRECATION")
fun Window.setFullScreenFlags() {
    this.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
}

fun FileURInate(context: Context, contentUri: Uri): Uri {
    // Read content-uri contents into a file we can get a file-uri for, and return that.
    if (contentUri.getScheme() == "file") return contentUri

    val tempdir: File = File(context.getCacheDir(), URINATE_TEMPDIR).apply { mkdirs() }

    // cleanup old stuff
    val cutoff = System.currentTimeMillis() - GECKOVIEW_FILE_URI_TO_TEMPFILE_EXPIRY
    tempdir.listFiles()!!.toList().forEach {
        if (it.lastModified() < cutoff) {
            it.deleteRecursively()
        }
    }

    return createTempdir(tempdir, URINATE_TEMPDIR).let { the_tmpdir ->
        File(the_tmpdir, URLUtil.guessFileName(contentUri.toString(), null, null)).let { thetmpfile ->
            context.contentResolver.openInputStream(contentUri)?.let { instream ->
                thetmpfile.outputStream().buffered().use { outstream ->
                    outstream.slurp(instream)
                }
            }
            Uri.fromFile(thetmpfile)
        }
    }
}