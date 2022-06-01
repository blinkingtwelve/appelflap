package org.nontrivialpursuit.appelflap.webwrap;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebPushDelegate;
import org.mozilla.geckoview.WebPushSubscription;
import org.nontrivialpursuit.appelflap.webpushpoll.PushRegistrations;
import org.nontrivialpursuit.appelflap.webpushpoll.Reginfo;

import java.security.SecureRandom;
import java.util.UUID;

public class FauxWebpushRegistrationHandler implements WebPushDelegate {

    final byte[] not_an_auth_secret = new byte[16];
    private PushRegistrations pushregs;

    public FauxWebpushRegistrationHandler(Context context) {
        this.pushregs = new PushRegistrations(context);
    }

    @Nullable
    @Override
    public GeckoResult<WebPushSubscription> onSubscribe(@NonNull String scope, @Nullable byte[] appserverkey) {
        // This implementation works different from webpush.
        // In a nutshell, we abuse the pubkey field as a per-registration token with which we can later poll an endpoint of choice 
        // (say, a Django application server) for messages directly.
        // No push server gets involved. It would be possible to base an implementation on Firebase Cloud Messaging instead, but
        // that presupposes the Play libraries installed. So we need to have a fallback polling mechanism anyway (something like this), and then
        // we might as well use that everywhere for simplicity, until we actually outgrow it a reason such as "we need near-real-time delivery because we are going to do chat via webpush now".
        byte browserPublicKey[] = new SecureRandom().generateSeed(65);  // No, not a pubkey. We'll use this as a token.
        String endpoint = UUID.randomUUID().toString();
        WebPushSubscription wps = new WebPushSubscription(scope, endpoint, appserverkey, browserPublicKey, not_an_auth_secret);
        // save the combo for use in polling and unsubscribe
        pushregs.storeRegistration(scope, browserPublicKey, endpoint);
        GeckoResult<WebPushSubscription> res = new GeckoResult<WebPushSubscription>();
        res.complete(wps);
        return res;
    }

    @Nullable
    @Override
    public GeckoResult<WebPushSubscription> onGetSubscription(@NonNull String scope) {
        Reginfo registered = pushregs.getReginfo(scope);
        if (registered == null) return null;
        WebPushSubscription wps = new WebPushSubscription(scope, registered.getRegid(), new byte[65], registered.getKey(), not_an_auth_secret);
        GeckoResult<WebPushSubscription> res = new GeckoResult<WebPushSubscription>();
        res.complete(wps);
        return res;
    }

    @Nullable
    @Override
    public GeckoResult<Void> onUnsubscribe(@NonNull String scope) {
        pushregs.deleteRegistration(scope);
        return null;
    }

}
