package org.nontrivialpursuit.appelflap

import android.content.Context
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

val NODE_ID_RANGE = Math.pow(55.0, 3.0).toLong()..(Math.pow(55.0, 4.0)
    .toLong() - 1) // ~9M values, representable in 4 chars "base55" (digits and upper and lowercase ASCII letters, minus the set of '0Oo1Iil')

const val nicenumbers = "23456789"
const val niceletters = "abcdefghjkmnpqrstuvwxyz"
val niceletters_upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
val base55_palette = (nicenumbers + niceletters + niceletters_upper).toCharArray()
val base55_map = base55_palette.mapIndexed { ix, c -> c to ix }.toMap()

fun enbaseX(num: Long, alphabet: CharArray, exponent: Int = 1): String {
    if (num == 0L && exponent > 1) return ""
    val base = alphabet.size
    val magnitude = Math.pow(1.0 * base, 1.0 * exponent).toLong()
    val chomp = num % magnitude
    val sym = alphabet[(chomp / (magnitude / base)).toInt()].toString()
    val remaining = num - chomp
    if (num < base) return sym
    return enbaseX(remaining, alphabet, exponent + 1) + sym
}

fun debaseX(input: String, mapping: Map<Char, Int>, base: Long = 1): Long {
    if (base == 1L && input.length == 0) throw IllegalArgumentException("Can't interpret a zero-length string")
    val terminus = input.lastOrNull() ?: return 0L
    return debaseX(
        input.substring(0, input.length - 1),
        mapping,
        base * mapping.size
    ) + (mapping[terminus]!! * base)
}


val enbase55 = fun(num: Long): String { return enbaseX(num, base55_palette) }
val debase55 = fun(input: String): Long { return debaseX(input, base55_map) }


fun get_nodeID(context: Context): Long {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.contains(NODE_ID_KEY)) {
        prefs.edit().also {
            it.putLong(
                NODE_ID_KEY, SecureRandom().asKotlinRandom().nextLong(
                    NODE_ID_RANGE.first,
                    NODE_ID_RANGE.last
                )
            )
            it.commit()
        }
    }
    return prefs.getLong(NODE_ID_KEY, 0L)
}

fun get_friendly_nodeID(context: Context): String {
    return enbase55(get_nodeID(context))
}
