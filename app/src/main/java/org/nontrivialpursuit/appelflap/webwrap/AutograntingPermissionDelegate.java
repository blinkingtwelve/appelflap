package org.nontrivialpursuit.appelflap.webwrap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

class AutograntingPermissionDelegate implements GeckoSession.PermissionDelegate {

    class AlwaysGrantingNotificationCallback implements Callback {
        private final Callback mCallback;

        AlwaysGrantingNotificationCallback(final Callback callback) {
            mCallback = callback;
        }

        @Override
        public void reject() {
            grant();
        }

        @Override
        public void grant() {
            mCallback.grant();
        }
    }

    class AlwaysGrantingStorageCallback implements Callback {
        private final Callback mCallback;

        AlwaysGrantingStorageCallback(final Callback callback, String uri) {
            mCallback = callback;
        }

        @Override
        public void reject() {
            grant();
        }

        @Override
        public void grant() {
            mCallback.grant();
        }
    }

    @Nullable
    @Override
    public GeckoResult<Integer> onContentPermissionRequest(@NonNull GeckoSession session, @NonNull ContentPermission perm) {
        // notifications, persistent storage, autoplay, ...
        return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
    }
}
