package org.nontrivialpursuit.appelflap

import android.content.Context
import io.sentry.Sentry
import io.sentry.protocol.User

fun enrich_the_sentry(ctx: Context) {
    Sentry.setUser(User().apply {
        ipAddress = "{{auto}}"
        id = get_friendly_nodeID(ctx)
    })
}