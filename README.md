# Appelflap

## What

A "kiosk mode" browser for a specific website of your choosing. It uses (and bundles) GeckoView, based on Firefox, as a browser component - rather than the more obvious choice of Webview (Android's built in browser component, based on Chrome). This gives us a stable and modern target even on old Android versions (7.0 and up are supported).

Appelflap is also delicious apple pastry from the Netherlands. 

### Links
- [upstream GeckoView changelog](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/doc-files/CHANGELOG)

## Developing / Configuring / Adapting to your site

### Development process

Basically, "monorepo" style, with customization/inheritance/overriding through the "product flavour" mechanism.

A wrapper app for a specific project is just a particular "product flavour" of the project.
Product flavours are defined using the buildtool config - see main `app/build.gradle`.
In the baseline case you can build an Appelflap wrapper by copying the Canoe product flavour sections, assigning to a handful of self-explanatory variables, and adding an icon. No coding required.

For more customization, look to the organization of the existing different product flavours to find examples of where to put project-specific artwork and texts, or how to handle customized versions of Java classes, or how to mock certain things out. Or read the Android documentation; https://developer.android.com/studio/build .

Currently there are still a bunch of references to things Eskola lingering in the codebase. This is historical accident. Whenever you stumble on something utterly Eskola-specific that should really only be enabled/visible in the Eskola product flavours, then it's a good opportunity for refactoring.

#### Signing & key management

Production builds need to be signed. The identity of an app is the appID + signature; so if you want to release an upgraded version of an app, you'll have to sign it with the same key as the version it's upgraded from is signed with. If the original keypair is lost, then that's it; you won't be able to release updates.

Store keypairs in a JKS store in the directory above your checkout, eg `../../my-keystore.jks`, then reference it from `app/build.gradle`. For a new build flavour, add a new key. One-key-per-project keeps things transferable; should there be a need to transfer maintenance of an app over to a different organization, then we can easily cut the key for just that one project out of our keystore and hand it over without giving them the ability to sign releases of our other apps.
If you want a GUI to operate on the key store, look to https://keystore-explorer.org/ .

#### In Just Four Easy Steps

So the steps from zero to playstore-releasable APK are:

1. Add appropriate flavour sections for your project to `app/build.gradle`
2. Add project-specific icon. Or just live with the default icon.
3. Arrange for key material.
4. Build with some invocation gradle like `app/gradlew -b ./build.gradle --configure-on-demand --console rich --no-daemon assembleXYZRelease` where `XYZ` is your chosen product flavour name. Find your APKs in `app/build/outputs/apk/XYZ/release`.

### Development

Get Android Studio. Load up the project. If all goes well Android Studio (or rather Gradle) will download the declared dependencies. Setting up JVMs and emulators and build/debug tasks can be a bit of a hazing process, especially on slow connections, so allocate some time for that.

Mostly things will work out of the box in Android Studio, as it understands the Gradle build files.
There's a special target `fatjar` in [Eekhoorn](docs/API/eikels.md) that Android Studio may not pick up on, you'll have to click something up or use the command line.

### Debugging

In debug builds you'll have access to regular ADB debugging to step through the Android code. In addition to that, you'll have access to the Firefox debugger to access the PWA web environment — open up `about:debugging` in a Firefox browser and follow the instructions.

If you're running on-device, you can project your 127.0.0.1:8000 (say you run Django there) to the device or emulator using `adb reverse tcp:8000 tcp:8000`. Then you can point the wrapper to http://127.0.0.1:8000 and it'll show your Django dev server. Often this is easier than trying to let the app connect to your dev server over the LAN.

On the emulator you might also make use of the addresses described [here](https://developer.android.com/studio/run/emulator-networking).

Note that updating the apps from peers (as in, letting the app itself install an updated APK it got from a peer that runs a debug build) will not work for debug builds as APKs signed with a debugging certificate can only be installed through ADB and not through the android APIs available to the app itself. So to test this functionality, you'll have to run signed APKs.
