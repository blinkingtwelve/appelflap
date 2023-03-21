package org.nontrivialpursuit.appelflap

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import io.github.rybalkinsd.kohttp.dsl.httpGet
import io.github.rybalkinsd.kohttp.ext.asStream
import io.github.rybalkinsd.kohttp.ext.url
import okhttp3.OkHttpClient
import org.nontrivialpursuit.appelflap.peddlenet.*
import org.nontrivialpursuit.eekhoorn.GIFT_PATH
import org.nontrivialpursuit.ingeblikt.Lockchest
import org.nontrivialpursuit.ingeblikt.MEAL_SIZE
import org.nontrivialpursuit.ingeblikt.ProgressCallbackDampener
import org.nontrivialpursuit.ingeblikt.slurp
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

const val DOWNLOAD_FILENAME_PREFIX = "temp-"
val UPDATE_APK_REGEX = Regex("^(\\d+)\\.apk$")
val LIBXUL_REGEX = Regex("^lib/([^/]+)/libxul.so")

class AppUpdate private constructor(val context: Context) {

    @Suppress("DEPRECATION")
    companion object {
        private var au: AppUpdate? = null

        @Synchronized
        fun getInstance(context: Context): AppUpdate { // Singleton
            return au ?: AppUpdate(context)
        }

        val SIGS_PLEASE_FLAG = PackageManager.GET_SIGNATURES or 0x08000000
        val INTENT_ACTION_INSTALL_PACKAGE = Intent.ACTION_INSTALL_PACKAGE

        fun getCerts(pkginfo: PackageInfo): Set<Signature> {
            val res = if (Build.VERSION.SDK_INT >= 28) { // Android 9+
                pkginfo.signingInfo?.signingCertificateHistory?.toSet() ?: setOf<Signature>()
            } else {
                pkginfo.signatures?.toSet() ?: setOf<Signature>()
            }
            return res
        }
    }

    val log = Logger(this)
    val pünktlich_httpclient = OkHttpClient.Builder().connectTimeout(PEER_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(PEER_READ_TIMEOUT, TimeUnit.MILLISECONDS).build()
    val downloadDir = File(context.cacheDir, APKUPGRADE_DIR).apply { mkdirs() }

    val my_archiveinfo: PackageInfo = context.packageManager.getPackageInfo(
        context.packageName, SIGS_PLEASE_FLAG
    )

    val my_certs: Set<Signature> = getCerts(my_archiveinfo)

    fun getUpgradeIntent(): Intent? {
        return get_upgrade()?.let {
            Intent(INTENT_ACTION_INSTALL_PACKAGE).apply {
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
        return get_upgrade(downloadDir.listFiles()?.toList())?.second ?: 0
    }


    fun cleanup(with_lock: String? = null) {
        val torun: () -> Unit = {
            downloadDir.listFiles()?.toList()?.also { thefilelist ->
                val toKeep = get_upgrade(thefilelist)?.first
                thefilelist.filter { it != toKeep }.forEach { it.deleteRecursively() }
            }
        }
        with_lock?.let { Lockchest.runWith(it, torun) } ?: torun.invoke()
    }


    fun get_upgrade(listing: List<File>? = null): Pair<File, Int>? {
        val the_listing = listing ?: downloadDir.listFiles()?.toList()
        return the_listing
            ?.mapNotNull { UPDATE_APK_REGEX.find(it.name)?.destructured?.component1()?.toIntOrNull()?.let { version -> it to version } }
            ?.maxByOrNull { it.second }?.takeIf { it.second > BuildConfig.VERSION_CODE }
    }


    fun verify_apk_update(apkfile: File): Pair<Boolean, Long> {
        val candidate_pkginfo: PackageInfo = context.packageManager.getPackageArchiveInfo(
            apkfile.path, SIGS_PLEASE_FLAG
        )!!
        val candidate_versioncode = PackageInfoCompat.getLongVersionCode(candidate_pkginfo)
        if (candidate_versioncode <= BuildConfig.VERSION_CODE || candidate_pkginfo.packageName != BuildConfig.APPLICATION_ID) return false to 0
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                candidate_pkginfo.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT
            } else {
                true  // This makes Android < 7.0 (which can't read the minSdkVersion from a package) go ahead with an upgrade, regardless. Let's just hope it's compatible. Further down the line the actual upgrade installer might bomb out in the users face if the upgrade is not compatible.
            }) return false to 0
        if (!checkSignature(candidate_pkginfo) || !checkABI(apkfile)) return false to 0
        return true to candidate_versioncode
    }


    fun grab_apk(peer: BonjourPeer, notifier: APKUpdateNotification? = null): Boolean {
        val apk_url = "${peer.url()}$GIFT_PATH"
        val apkgrab_resp = kotlin.runCatching {
            httpGet(pünktlich_httpclient) {
                url(apk_url)
            }
        }.getOrNull() ?: return false
        apkgrab_resp.use { resp ->
            if (resp.code == 200) {
                val expected_size = resp.header("Content-Length")?.toLongOrNull() ?: return false
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
                        val tmpfile = @Suppress("DEPRECATION") createTempFile("${DOWNLOAD_FILENAME_PREFIX}${peer.appversion}", ".apk", downloadDir)
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
        }
        return false
    }
}