package org.nontrivialpursuit.appelflap.webwrap;

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

    @Override
    public void onContentPermissionRequest(final GeckoSession session, final String uri, final int type, final Callback callback) {
        // notifications, persistent storage, autoplay, ...
        callback.grant();
    }
}
