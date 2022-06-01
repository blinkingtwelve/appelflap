package org.nontrivialpursuit.eekhoorn

import org.eclipse.jetty.util.B64Code
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

fun gen_credentials(): Triple<String, String, String> {
    // Generates random username & password packing 47 bits of entropy (Good Enough™)
    val alphabet = ('a'..'z').toList()
    val rando = SecureRandom().asKotlinRandom()
    var username = (1..5).map { _ -> alphabet.random(rando) }
        .joinToString("")
    var password = (1..5).map { _ -> alphabet.random(rando) }
        .joinToString("")
    return Triple(username, password, as_requestheader(username, password))
}

fun as_requestheader(username: String, password: String): String {
    val encoded = B64Code.encode("${username}:${password}", Charsets.US_ASCII);
    return "Basic ${encoded}"
}