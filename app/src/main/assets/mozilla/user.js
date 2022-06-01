// Autogrant a bunch of permissions
user_pref("permissions.default.camera", 1);
user_pref("permissions.default.desktop-notification", 1);
user_pref("permissions.default.geo", 1);
user_pref("permissions.default.image", 1);
user_pref("permissions.default.microphone", 1);
user_pref("permissions.default.shortcuts", 1);
user_pref("permissions.default.xr", 1);
user_pref("dom.webnotifications.requireuserinteraction", false);

// Disable needless checks and save some net traffic
user_pref("browser.safebrowsing.malware.enabled", false);
user_pref("browser.safebrowsing.phishing.enabled", false);
user_pref("browser.safebrowsing.downloads.enabled", false);

// Disable setting up any always-open push connection (would drain battery).
user_pref("dom.push.connection.enabled", false);

// Override this extension's UUID with something static so we can access the local filesystem with urls such as `moz-extension://appelflap/ohai.txt`
user_pref("extensions.webextensions.uuids", "{\"appelflap@appelflap.appelflap\":\"appelflap\"}");
