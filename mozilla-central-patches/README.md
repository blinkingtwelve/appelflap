# Patches to GeckoView

## What is this

Patches to be applied on top of GeckoView.

## When do you need this

Never. Use the maven repo as preconfigured in the Appelflap buildfiles.

## How to do this
1. Create a Mozilla build environment as per https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-quick-start.html. Also look at https://firefox-source-docs.mozilla.org/setup/configuring_build_options.html .
2. Choose the buildtype which pulls in prebuilt Geckoview engine artifacts. We don't want to actually build Firefox core, just the connecting Android machinery.
3. Apply the patches.
4. Build.
5. See Appelflap's gradle files for an example of how to refer to these builds so that your brand new GeckoView library is actually used in Appelflap.
6. For a production build for Jenkins and others to use, patch up an official Mozilla release artifact. See [transplant.sh](transplant.sh). Make sure to test different architectures (x86-64 emulator, and some ARM hardware - phone or tablet for instance).