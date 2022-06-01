package org.nontrivialpursuit.ingeblikt

import java.util.*

const val RFC7231_MONTHNAMES = "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec"
val MONTHMAP = RFC7231_MONTHNAMES.split('|').zip(0..11).toMap()
val RFC7231_DATETIME_REX = "(Mon|Tue|Wed|Thu|Fri|Sat|Sun), ([0123][0-9]) (${RFC7231_MONTHNAMES}) (2[0-9]{3}) ([012][0-9]):([0-5][0-9]):([0-6][0-9]) GMT".toRegex()

@Suppress("DEPRECATION")  // use GregorianCalendar, they say... but that one interprets the time as if it's in the system's timezone without any way to specify another, and I want UTC, which Date does out of the box.
fun parse_rfc7231datetime(datetimestr: String): Date? {
    val match = RFC7231_DATETIME_REX.matchEntire(datetimestr) ?: return null
    val (_, day_of_month, monthname, year, hour, minute, second) = match.destructured
    return Date(
        Integer.parseInt(year) - 1900,
        MONTHMAP[monthname]!!,
        Integer.parseInt(day_of_month),
        Integer.parseInt(hour),
        Integer.parseInt(minute),
        Integer.parseInt(second)
    )
}