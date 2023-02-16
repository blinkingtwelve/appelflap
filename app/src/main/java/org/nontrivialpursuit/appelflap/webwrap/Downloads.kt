package org.nontrivialpursuit.appelflap.webwrap

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mozilla.geckoview.WebResponse
import org.nontrivialpursuit.appelflap.R
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.libkitchensink.DownloadDisplayDescriptor
import org.nontrivialpursuit.libkitchensink.DownloadDisplayDescriptorListing
import org.nontrivialpursuit.libkitchensink.hexlify
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

const val DOWNLOADS_DIR = "downloads"  // Needs to match up with the value in sharepaths_downloads.xml
const val DOWNLOADS_LOCKS_PREFIX = "Downloads"
const val NOTAG_MARKER = "NOTAG"
const val FILENAME_CHARS_ALLOWED = "[\\w.-]"
val ATTACHMENT_FILENAMES_REX = Regex(
    "attachment;.*?(?<filenametype1>filename\\*?) *= *\"(?<filename1>${FILENAME_CHARS_ALLOWED}+)\".*?(?: *; *(?<filenametype2>filename\\*?) *= *\"(?<filename2>${FILENAME_CHARS_ALLOWED}+)\")?.*?",
    RegexOption.IGNORE_CASE
)
const val METAFILE_EXTENSION = ".meta.json"
const val BODYFILE_EXTENSION = ".download"
const val ID_PATTERN = "^([0-9a-f]{32}\\.(?:${NOTAG_MARKER}|[0-9a-f]{32}))"
val METAFILE_REX = Regex("${ID_PATTERN}${Regex.escape(METAFILE_EXTENSION)}$")

open class DownloadException(msg: String) : Exception(msg)

class DownloadSizeException(msg: String) : DownloadException(msg)

class DownloadIllegalStateException(msg: String) : DownloadException(msg)

class DownloadsFileProvider : FileProvider()

class TempFileProvider : FileProvider()

@Serializable
data class DownloadDescriptor(val uri: String, val headers: Map<String, String>, var digest: String? = null) {
    companion object {
        fun create(uri: String, headers: Map<String, String>): DownloadDescriptor {
            return DownloadDescriptor(uri, headers.mapKeys { it.key.lowercase() })
        }
    }

    fun length(): Long? {
        return this.headers["Content-Length"]?.toLongOrNull()
    }

    fun idParts(): Pair<String, String> {
        return this.uri.hexHash() to (this.headers["etag"]?.hexHash() ?: NOTAG_MARKER)
    }

    fun mimetype(): String? {
        return Intent.normalizeMimeType(getMimeType(uri, headers))
    }

    fun filename(): String? {
        return getFilename(uri, headers)
    }

    fun nice_filename(): String {
        return filename() ?: idParts().let { "${it.first}.${it.second}.download" }
    }

    fun server_timestamp(): Long? {
        return headers["date"]?.let { parse_rfc7231datetime(it)?.time }
    }
}

fun String.hexHash(): String {
    return this.toByteArray().md5().hexlify()
}

fun WebResponse.getDownloadDescriptor(): DownloadDescriptor {
    return DownloadDescriptor.create(this.uri, this.headers)
}

fun Context.resFmt(id: Int, vararg args: String): String {
    return String.format(this.resources.getText(id).toString(), *args)
}

fun getMimeType(uri: String, headers: Map<String, String>): String? {
    // Preference order:
    // 1. from content-type header
    // 2. inferred from filename extension
    return headers["content-type"]?.split(",", limit = 2)?.firstOrNull() ?: MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(getFilename(uri, headers)?.let { File(it).extension })
}

fun getFilename(uri: String, headers: Map<String, String>): String? {
    // Preference order:
    // 1. from content-disposition header:
    // 1a. filename*
    // 1b. filename
    // 2. inferred from URL
    //
    // "Bugs":
    // - for content-disposition, supports only filenames with characters 0-9a-zA-Z_.+
    // - preference order is not followed if more than 2 of {filename, filename*} are specified (which would be strange anyway).
    return headers["content-disposition"]?.let {
        ATTACHMENT_FILENAMES_REX.matchEntire(it)?.let {
            val (f1, fn1, f2, fn2) = it.destructured
            when (f1) {
                "filename*" -> fn1
                else -> when (f2) {
                    "filename*" -> fn2
                    else -> fn1
                }
            }
        }
    } ?: URI(uri).path.trimEnd('/').split("/").last().takeIf { it.length > 0 }
}

enum class DownloadStatus {
    DOWNLOAD_NEW,
    DOWNLOAD_ASK_OVERWRITE,
    OPEN_EXISTING,
}

class DownloadEntry(val context: Context, val dlid: Pair<String, String>, response: WebResponse? = null) {
    val downloadDir = File(context.filesDir, DOWNLOADS_DIR).apply { mkdirs() }
    val metafile = File(downloadDir, "${dlid.first}.${dlid.second}${METAFILE_EXTENSION}")
    val bodyfile = File(downloadDir, "${dlid.first}.${dlid.second}${BODYFILE_EXTENSION}")
    val meta = response?.getDownloadDescriptor() ?: metafile.takeIf { it.exists() }?.let { thefile ->
        runCatching<DownloadDescriptor> { Json.decodeFromString(DownloadDescriptor.serializer(), thefile.readText()) }.fold({ it }, {
            thefile.delete()
            null
        })
    }

    constructor(context: Context, response: WebResponse) : this(context, response.getDownloadDescriptor().idParts(), response)

    fun download(inputStream: InputStream) {
        val meta = meta ?: throw DownloadIllegalStateException("Meta fields not initialized")
        val digester = MessageDigest.getInstance("MD5")
        try {
            bodyfile.setWritable(true)
            bodyfile.outputStream().buffered().use {
                if (it.slurp(
                        inputStream, max_to_read = meta.length(), digester
                    ) != MEAL_SIZE.RIGHT_ON) throw DownloadSizeException("Download stream size for ${meta.uri} does not match content-length of ${meta.length()}")
            }
            bodyfile.setWritable(false)  // mark as complete
            meta.digest = digester.digest().md5().hexlify()
        } catch (e: IOException) {
            bodyfile.delete()
        }
        metafile.writeText(Json.encodeToString(DownloadDescriptor.serializer(), meta))
    }

    fun androidOpen() {
        val meta = meta ?: throw DownloadIllegalStateException("Meta fields not initialized")
        context.startActivity(Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(
                FileProvider.getUriForFile(context, context.getString(R.string.DOWNLOADS_FILEPROVIDER_AUTHORITY), bodyfile),
                meta.mimetype() ?: "*/*"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let {
            Intent.createChooser(it, context.resFmt(R.string.download_openwith_choosermessage, meta.nice_filename()))
        })
    }


    fun androidShare() {
        val meta = meta ?: throw DownloadIllegalStateException("Meta fields not initialized")
        context.startActivity(
            ShareCompat.IntentBuilder(context).apply {
                setStream(
                    FileProvider.getUriForFile(
                        context, context.getString(R.string.DOWNLOADS_FILEPROVIDER_AUTHORITY), bodyfile
                    )
                )
                setChooserTitle(context.resFmt(R.string.download_share_choosermessage, meta.nice_filename()))
                setType("*/*")
            }.createChooserIntent()
        )
    }


    fun getStatus(): DownloadStatus {
        return when (metafile.exists()) {
            false -> {
                DownloadStatus.DOWNLOAD_NEW
            }
            true -> {
                // metadata already exists. Does the body file?
                when (bodyfile.exists()) {
                    false -> DownloadStatus.DOWNLOAD_NEW  // it was deleted, so we'll re-download it
                    true -> {
                        when (dlid.second == NOTAG_MARKER) {
                            true -> DownloadStatus.DOWNLOAD_ASK_OVERWRITE  // no etag, no strong identity - we'll have to ask whether to overwrite
                            false -> DownloadStatus.OPEN_EXISTING  // all good, no need to (re-)download
                        }
                    }
                }
            }
        }
    }

    fun toDownloadDescriptor(): DownloadDisplayDescriptor? {
        return meta?.let { desc ->
            DownloadDisplayDescriptor(
                desc.uri,
                desc.nice_filename(),
                bodyfile.length(),
                desc.server_timestamp() ?: bodyfile.lastModified(),
                desc.mimetype(),
                desc.idParts().let { "${it.first}.${it.second}" },
            )
        }
    }
}


class Downloads(val context: Context) {
    val downloadDir = File(context.filesDir, DOWNLOADS_DIR).apply { mkdirs() }

    fun listDownloads(): DownloadDisplayDescriptorListing {
        return DownloadDisplayDescriptorListing(downloadDir.listFiles()?.mapNotNull { thefile ->
            METAFILE_REX.matchEntire(thefile.name)?.let { therexmatch ->
                val idparts = therexmatch.groupValues.get(1).split(".", limit = 2).slice(0..1)
                DownloadEntry(context, idparts[0] to idparts[1]).takeUnless { it.bodyfile.canWrite() }?.let { dlentry ->
                    dlentry.toDownloadDescriptor()
                }
            }
        } ?: emptyList())
    }


    fun actuallyDownload(response: WebResponse, lock: Semaphore) {
        thread {
            try {
                response.body?.also {
                    val entry = DownloadEntry(context, response)
                    entry.download(it)
                    entry.androidOpen()
                }
            } finally {
                lock.release()
            }
        }
    }


    fun startDownload(response: WebResponse) {
        val lock = Lockchest.get("${DOWNLOADS_LOCKS_PREFIX}-$response.uri")

        // are we already downloading this?
        lock.tryAcquire().also {
            if (!it) {
                Toast.makeText(context, context.resFmt(R.string.download_already_downloading, response.uri), Toast.LENGTH_LONG).show()
                return
            }
        }

        val download = DownloadEntry(context, response)
        when (download.getStatus()) {
            DownloadStatus.DOWNLOAD_NEW -> actuallyDownload(response, lock)
            DownloadStatus.OPEN_EXISTING -> download.androidOpen()
            DownloadStatus.DOWNLOAD_ASK_OVERWRITE -> {
                val clickhandler = { _: DialogInterface, which: Int ->
                    when (which) {
                        AlertDialog.BUTTON_POSITIVE -> actuallyDownload(response, lock)
                        AlertDialog.BUTTON_NEGATIVE -> download.androidOpen()
                    }
                }
                AlertDialog.Builder(context)
                    .setTitle(context.resFmt(R.string.download_dialog_overwrite_question, download.meta!!.nice_filename()))
                    .setMessage(R.string.download_dialog_overwrite_explainer)
                    .setPositiveButton(R.string.download_dialog_overwrite_affirmative, clickhandler)
                    .setNegativeButton(R.string.download_dialog_overwrite_negative, clickhandler).create().show()
            }
        }
    }

    fun deleteDownload(actionSubject: Pair<String, String>): Boolean {
        return listOf(METAFILE_EXTENSION, BODYFILE_EXTENSION)
            .map { File(downloadDir, "${actionSubject.first}.${actionSubject.second}${it}").delete() }.all { it }
    }
}

