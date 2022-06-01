package org.nontrivialpursuit.appelflap

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import io.github.rybalkinsd.kohttp.client.defaultHttpClient
import io.github.rybalkinsd.kohttp.client.fork
import io.github.rybalkinsd.kohttp.dsl.httpGet
import io.github.rybalkinsd.kohttp.ext.asStream
import io.github.rybalkinsd.kohttp.ext.url
import org.nontrivialpursuit.appelflap.peddlenet.*
import org.nontrivialpursuit.eekhoorn.GIFT_PATH
import org.nontrivialpursuit.ingeblikt.Lockchest
import org.nontrivialpursuit.ingeblikt.MEAL_SIZE
import org.nontrivialpursuit.ingeblikt.ProgressCallbackDampener
import org.nontrivialpursuit.ingeblikt.slurp
import java.io.File
import java.util.zip.ZipFile

const val DOWNLOAD_FILENAME_PREFIX = "temp-"
val UPDATE_APK_REGEX = Regex("^(\\d+)\\.apk$")
val LIBXUL_REGEX = Regex("^lib/([^/]+)/libxul.so")
val SIGS_PLEASE_FLAG = PackageManager.GET_SIGNATURES or 0x08000000

class AppUpdate private constructor(val context: Context) {

    companion object {
        private var au: AppUpdate? = null

        @Synchronized
        fun getInstance(context: Context): AppUpdate { // Singleton
            return au ?: AppUpdate(context)
        }
    }

    val log = Logger(this)
    val pünktlich_httpclient = defaultHttpClient.fork {
        connectTimeout = PEER_CONNECT_TIMEOUT
        readTimeout = PEER_READ_TIMEOUT
    }
    val downloadDir = File(context.cacheDir, APKUPGRADE_DIR).apply { mkdirs() }

    val my_archiveinfo: PackageInfo = context.packageManager.getPackageInfo(
        context.packageName, SIGS_PLEASE_FLAG
    )

    val my_certs: Set<Signature> = getCerts(my_archiveinfo)

    fun getUpgradeIntent(): Intent? {
        return get_upgrade()?.let {
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(
                    FileProvider.getUriForFile(
                        context, context.getString(R.string.APKUPGRADE_FILEPROVIDER_AUTHORITY), it.first
                    ), APK_MIMETYPE
                )
                setFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                )
            }
        }
    }

    fun getCerts(pkginfo: PackageInfo): Set<Signature> {
        val res = if (Build.VERSION.SDK_INT >= 28) { // Android 9+
            pkginfo.signingInfo?.signingCertificateHistory?.toSet() ?: setOf<Signature>()
        } else {
            pkginfo.signatures?.toSet() ?: setOf<Signature>()
        }
        return res
    }


    fun checkABI(apkpath: File): Boolean {/* Hack: There doesn't seem to be a nice PackageInfo API for this.
        So, in the APK, find which ABIs we have libxul.so (part of Geckoview) for.
        */
        val them_abis = ZipFile(apkpath).use {
            it.entries().toList().mapNotNull { LIBXUL_REGEX.find(it.name)?.destructured?.component1() }.toSet()
        }
        return them_abis.intersect(Build.SUPPORTED_ABIS.toSet()).isNotEmpty()
    }


    fun checkSignature(pkginfo: PackageInfo): Boolean {/*
        Check whether an APK is signed with any of the same certs that our own package is signed with (which would make it upgradable).
        Note that it's  not clear whether the contents of the package related to the PackageInfo have been verified at this point.
        */
        val them_sigs = getCerts(pkginfo)
        return them_sigs.intersect(my_certs).isNotEmpty()
    }


    fun max_available_version(): Int {
        return get_upgrade(downloadDir.listFiles().toList())?.second ?: 0
    }


    fun cleanup(with_lock: String? = null) {
        val torun = {
            val filelist = downloadDir.listFiles().toList()
            val toKeep = get_upgrade(filelist)?.first
            filelist.filter { it != toKeep }.forEach { it.deleteRecursively() }
        }
        with_lock?.let { Lockchest.runWith(it, torun) } ?: torun.invoke()
    }


    fun get_upgrade(listing: List<File>? = null): Pair<File, Int>? {
        val the_listing = listing ?: downloadDir.listFiles().toList()
        return the_listing
            .mapNotNull { UPDATE_APK_REGEX.find(it.name)?.destructured?.component1()?.toIntOrNull()?.let { version -> it to version } }
            .maxByOrNull { it.second }?.takeIf { it.second > BuildConfig.VERSION_CODE }
    }


    fun verify_apk_update(apkfile: File): Pair<Boolean, Int> {
        val candidate_pkginfo: PackageInfo = context.packageManager.getPackageArchiveInfo(
            apkfile.path, SIGS_PLEASE_FLAG
        )
        if (candidate_pkginfo.versionCode <= BuildConfig.VERSION_CODE || candidate_pkginfo.packageName != BuildConfig.APPLICATION_ID) return false to 0
        if (Build.VERSION.SDK_INT >= 24) {
            if (candidate_pkginfo.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) return false to 0
        }
        if (!checkSignature(candidate_pkginfo) || !checkABI(apkfile)) return false to 0
        return true to candidate_pkginfo.versionCode
    }


    fun grab_apk(peer: BonjourPeer, notifier: APKUpdateNotification? = null): Boolean {
        val apk_url = "${peer.url()}$GIFT_PATH"
        val resp = kotlin.runCatching {
            httpGet(pünktlich_httpclient) {
                url(apk_url)
            }
        }.getOrNull()
        if (resp?.code() == 200) {
            val expected_size = resp.header("Content-Length")?.let { it.toLongOrNull() } ?: return false
            if (expected_size > MAX_APK_FETCH_SIZE) {
                log.w("APK size of ${expected_size} deemed to large, url: ${apk_url}")
                return false
            }
            if (expected_size > downloadDir.freeSpace) {
                log.w("No space to store APK of (expected) size ${expected_size}, url: ${apk_url}")
                return false
            }
            kotlin.runCatching {
                resp.asStream()?.use {
                    val tmpfile = createTempFile("${DOWNLOAD_FILENAME_PREFIX}${peer.appversion}", ".apk", downloadDir)
                    fun progress_callback(sofar: Long, max: Long?) {
                        max?.also { themax ->
                            notifier?.showProgress(sofar, themax)
                        }
                    }
                    val dampener = ProgressCallbackDampener(every = 0x10000, ::progress_callback)
                    tmpfile.outputStream().slurp(it, expected_size, progress_callback = dampener::progress_callback)
                        .takeIf { it == MEAL_SIZE.RIGHT_ON }?.run {
                        verify_apk_update(tmpfile).takeIf { it.first }?.also {
                            tmpfile.renameTo(File(downloadDir, "${it.second}.apk"))  // For pickup by some other process
                            cleanup()  // And this is why we're not re-entrant. We'd delete any in-progress downloads :-/
                        }
                    } ?: tmpfile.delete()
                }
            }.exceptionOrNull()?.also {
                log.w("Indelible APK from ${apk_url}: ${it.message}") // TODO: disturbance mitigation: blocklist this peer for a while?
                notifier?.cancelProgress()
                return false
            }
            notifier?.cancelProgress()
            return true
        }
        return false
    }
}