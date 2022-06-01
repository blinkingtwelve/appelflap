# Bundle preloading

## What
An Appelflap product flavour's APK can come with baked-in bundles that will be installed on first app boot.

## Why
So that a first boot of the app, even when the apparatus is offline, can come up with the intended experience rather than splashing into a "you are offline" kind of error. Typically avoiding this would involve preloading a serviceworker and any caches associated with it.

## How
### Where to put the bundles
The desired bundle files (`.bundle` extension ) need to be placed in the `assets/ingeblikt_preload` folder of the product flavour as declared in the `sourceSets` section of Appelflap's `build.gradle`. Of course, such bundles will need to have been signed in a way accepted by the product flavour.
### How to acquire bundles
#### From an Android device
- Get it into the desired state, that is, get the serviceworker & co installed/updated by making Appelflap visit the wrapped website.
- Make the appropriate `PUT`s through the [API](docs/API/cache-publication.md#creatingupdating-a-cache-publication) to publish a cache. If you don't know anything better (for instance, the project doesn't use formal serviceworker versioning), leave the versioning up to Appelflap (it'll derive it from the latest `Date` server-created HTTP header that it finds in the cache.)
- Acquire the `.bundle` artefacts through a `GET` through that same [API](docs/API/cache-publication.md#downloading-a-cache-publication)
- Alternatively, if the device is rooted, you can find the bundles beneath `/data/data/org.nontrivialpursuit.appelflap.demo.demo/files/koekoekseieren/` and get them with `adb pull`.
#### From the Android emulator
Same as for an actual device, except that you can get them from the filesystem for sure (execute `adb root` to elevate privileges).
#### Using Eekhoorn & Firefox
An Android environment is not required to run the HTTP API; it can run on a PC as well (Linux/probably OSX/maybe Windows).
0. Launch a Firefox of the same version as the GeckoView engine, with a clean profile - `firefox --no-remote --ProfileManager` will enable you to do so, it will show the profile directory when mouseovering a profile name.
1. Let the serviceworker install by navigating to the wrapped website.
2. Terminate the Firefox process.
3. Build the Eekhoorn fatjar — `gradle fatjar` should do it. This yields a standalone redistributable `.jar` with all required libraries baked in.
4. Launch the fatjar and consult its parameter specification (`java -jar eekhoorn/build/libs/eekhoorn.jar --help`)
5. Exercise the API as described earlier.
6. The bundles can be picked up from the directory specified with the `--storagedir` parameter, or retrieved through the HTTP API.
#### Using the Koekoek CLI & Firefox
- Set up a Firefox profile as described before, then run the CLI utility (set up a launch config in the IDE for `org.nontrivialpursuit.ingeblikt.CLIutils.PackupCLI` with the `koekoek` library on the classpath). See its `--help` for the parameter specification.



## Verifying
To verify whether things are working as expected (a must, for a field release):
0. Take the test device offline
1. Uninstall the app to clear its state (or clear state through the in-app mechanism — the "paddle" launcher)).
2. Reinstall the app (or let the state reset mechanism reboot the app)
3. Verify that the wrapped website works as intended, offline
4. Witness that the injection of the preloaded bundles has been registered Subscriptions.

If things do not look as they should, redo the steps while capturing logs (`adb logcat`) to see what went down.