@file:JvmName("Const")

package org.nontrivialpursuit.appelflap

import android.os.Build

const val EEKHOORN_DEBUG_PORT = 9090
const val EEKHOORN_DEBUG_CREDENTIALS = "appel:appel"
const val PREFS_NAME = "settings"
const val DEBUG_SITEURL_PREFS_NAME = "debug_siteurls"
const val NODE_ID_KEY = "nodeID"
const val GECKOVIEW_FILE_URI_TO_TEMPFILE_EXPIRY = 24L * 60L * 60L * 3600L * 1000L
const val URINATE_TEMPDIR = "URInate"
const val APPELFLAP_WEBEXTENSION_PATH = "appelflap_webextension"
const val APPELFLAP_WEBEXTENSION_SERVERINFO_FILENAME = "serverinfo.json"
const val APPELFLAP_WEBEXTENSION_PEERID_FILENAME = "peerid.json"
const val APK_MIMETYPE = "application/vnd.android.package-archive"

val USERAGENT = "${BuildConfig.APPLICATION_ID}-${BuildConfig.VERSION_NAME}/${Build.PRODUCT}"

const val CACHE_INJECTION_INTERVAL = 60L  // in seconds. Usually, the event-based callbacks will have already injected caches if appropriate.
