package org.nontrivialpursuit.appelflap.webwrap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebNotification;
import org.mozilla.geckoview.WebNotificationDelegate;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;
import org.nontrivialpursuit.appelflap.R;

import java.util.HashMap;

import static org.nontrivialpursuit.appelflap.webwrap.GeckoWrapKt.INTENT_EXTRA_NOTIFICATION_CLICK;

public class WebnotificationsHandler implements WebNotificationDelegate {

    private final GeckoRuntime geckoruntime;
    private final NotificationManager notyman;
    private final Context context;

    private final HashMap<String, Integer> mNotificationIDMap = new HashMap<>();
    private final HashMap<Integer, WebNotification> mNotificationMap = new HashMap<>();
    private int mLastID = 100;

    public WebnotificationsHandler(Context context, GeckoRuntime geckoruntime) {
        this.context = context;
        this.geckoruntime = geckoruntime;
        notyman = context.getSystemService(NotificationManager.class);
    }

    @Override
    public void onShowNotification(@NonNull WebNotification webnotification) {
        Intent clickIntent = new Intent(context, GeckoWrap.class)
                .putExtra(INTENT_EXTRA_NOTIFICATION_CLICK, webnotification.tag);
        PendingIntent dismissIntent = PendingIntent.getActivity(context, mLastID, clickIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle(webnotification.title)
                .setContentText(webnotification.text)
                .setSmallIcon(R.drawable.ic_webnotification)
                .setContentIntent(dismissIntent)
                .setAutoCancel(true);

        mNotificationIDMap.put(webnotification.tag, mLastID);
        mNotificationMap.put(mLastID, webnotification);

        if (webnotification.imageUrl != null && webnotification.imageUrl.length() > 0) {
            final GeckoWebExecutor executor = new GeckoWebExecutor(geckoruntime);

            GeckoResult<WebResponse> response = executor.fetch(
                    new WebRequest.Builder(webnotification.imageUrl)
                            .addHeader("Accept", "image")
                            .build());
            response.accept(value -> {
                Bitmap bitmap = BitmapFactory.decodeStream(value.body);
                if (bitmap != null) {
                    builder.setLargeIcon(Icon.createWithBitmap(bitmap));
                }
                notyman.notify(mLastID++, builder.build());
            });
        } else {
            notyman.notify(mLastID++, builder.build());
        }

    }

    @Override
    public void onCloseNotification(@NonNull WebNotification notification) {
        if (mNotificationIDMap.containsKey(notification.tag)) {
            int id = mNotificationIDMap.get(notification.tag);
            notyman.cancel(id);
            mNotificationMap.remove(id);
            mNotificationIDMap.remove(notification.tag);
        }
    }

    public void click(String sourceID) {
        Integer key = mNotificationIDMap.get(sourceID);
        WebNotification notification = mNotificationMap.get(key);
        if (notification != null) {
            notification.click();
            mNotificationMap.remove(key);
            mNotificationIDMap.remove(sourceID);
        }
    }
}
