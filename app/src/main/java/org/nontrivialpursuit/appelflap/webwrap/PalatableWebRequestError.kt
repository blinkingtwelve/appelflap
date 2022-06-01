package org.nontrivialpursuit.appelflap.webwrap

import org.mozilla.geckoview.WebRequestError

val ERROR_FIELD_IDENTIFIER_REX = Regex("^ERROR_(CATEGORY_)?")

val REQUEST_ERRORS = WebRequestError::class.java.declaredFields.toList().mapNotNull {
    runCatching { it[0] as Int }.getOrNull()?.let { thevalue -> thevalue to it.name.split(ERROR_FIELD_IDENTIFIER_REX)[1] }
}.toMap()

fun weberr(errorcode: Int): String {
    return REQUEST_ERRORS[errorcode] ?: errorcode.toString()
}