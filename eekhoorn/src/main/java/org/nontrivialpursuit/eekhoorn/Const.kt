package org.nontrivialpursuit.eekhoorn

import org.nontrivialpursuit.ingeblikt.CacheType

const val FAVICON_PATH = "/favicon.ico"
const val GIFT_PATH = "/gift.apk"
const val API_PREFIX = "/appelflap"
const val EIKEL_PREFIX = "$API_PREFIX/eikel"
const val EIKEL_META_PREFIX = "$API_PREFIX/eikel-meta"
const val ACTIONS_PREFIX = "$API_PREFIX/do"
const val INFO_PREFIX = "$API_PREFIX/info"
const val SETTINGS_PREFIX = "$API_PREFIX/settings"
const val DOWNLOADS_PREFIX = "$API_PREFIX/downloads"

const val INGEBLIKT_PREFIX = "$API_PREFIX/ingeblikt"
const val PKI_OPS_PATH = "$INGEBLIKT_PREFIX/certchain"
const val INJECTION_LOCK_PATH = "$INGEBLIKT_PREFIX/injection-lock"
const val PUBLICATIONS_PATH = "$INGEBLIKT_PREFIX/publications"
const val DEBUG_TRICKS_PATH = "$INGEBLIKT_PREFIX/jeffreystube"
const val SUBSCRIPTIONS_PATH = "$INGEBLIKT_PREFIX/subscriptions"

val MD5_REGEX = Regex("^[a-z0-9]{32}$")
val KOEKOEK_BUNDLE_REGEX = Regex("^(${CacheType.values().joinToString("|")})\\.[a-z0-9]{32}\\.[a-z0-9]{32}\\.[0-9]{1,19}$")

const val HEADER_STORE_PREFIX = "X-Eekhoorn-Store-Header-"
val BASIC_AUTH_REX = Regex("""^(?<username>[^:"]+):(?<password>[^"]+)$""")
const val EIKEL_BODY_FILENAME = "body"
const val EIKEL_META_FILENAME = "meta.json"
const val EIKEL_SUBDIR = "eikels"
const val QUIESCENCE_HEADER = "X-Appelflap-Quiescence"

const val JSON_HTTP_CONTENTTYPE = "application/json; charset=UTF-8"
const val TEXT_HTTP_CONTENTTYPE = "text/plain; charset=UTF-8"