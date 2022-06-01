#!/bin/bash -eux

# This is not a portable script. It's more of a memory support / convenience for those who need to patch GeckoView.

ZIPALIGN="/opt/android-sdk-update-manager/build-tools/30.0.3/zipalign"

echo "Press Enter to pick upstream build"
read
UPSTREAM_BUILD=$(fd 'geckoview-.*\.aar$' ~/.gradle/caches/modules-2 -E 'geckoview-default' | fzf --keep-right)
echo "Press Enter to pick our build"
read
OUR_BUILD=$(fd 'geckoview-.*\.aar$' ~/devschuur/mozilla-central/objdir-frontend/gradle/build/mobile/android/geckoview/maven/org/mozilla/geckoview/geckoview-default | fzf --keep-right)
MONGREL_TMPDIR=$(mktemp --directory --suffix=_mongrel --tmpdir=`dirname "${OUR_BUILD}"`)
mkdir ${MONGREL_TMPDIR}/our_omni.ja
cp --reflink=auto "${UPSTREAM_BUILD}" "${MONGREL_TMPDIR}"
MONGREL=${MONGREL_TMPDIR}/`basename "${UPSTREAM_BUILD}"`

# transplant select omni.ja JSM stuff
unzip "${UPSTREAM_BUILD}" assets/omni.ja -d "${MONGREL_TMPDIR}"
unzip "${OUR_BUILD}" assets/omni.ja -d "${MONGREL_TMPDIR}/our_omni.ja"
cd "${MONGREL_TMPDIR}"/assets
mkdir -p modules/addons
mkfifo modules/GeckoViewWebExtension.jsm
mkfifo modules/addons/XPIInstall.jsm
(unzip -p "${MONGREL_TMPDIR}/our_omni.ja/assets/omni.ja" modules/addons/XPIInstall.jsm > modules/addons/XPIInstall.jsm)&
(unzip -p "${MONGREL_TMPDIR}/our_omni.ja/assets/omni.ja" modules/GeckoViewWebExtension.jsm > modules/GeckoViewWebExtension.jsm)&
zip --fifo --recurse-paths omni.ja modules/
cd -

# transplant classes.jar
mkfifo "${MONGREL_TMPDIR}/classes.jar"
(unzip -p "${OUR_BUILD}" classes.jar > "${MONGREL_TMPDIR}/classes.jar")&
cd "${MONGREL_TMPDIR}"
zip --fifo --compression-method store "${MONGREL}" classes.jar assets/omni.ja
cd -

# overwrite our build with the update
${ZIPALIGN} -f -p 8 "${MONGREL}" "${OUR_BUILD}"

# cleanup & fixup
rm -rf "${MONGREL_TMPDIR}"
sha1sum "${OUR_BUILD}" | cut -d ' ' -f 1 > "${OUR_BUILD}".sha1
md5sum "${OUR_BUILD}" | cut -d ' ' -f 1 > "${OUR_BUILD}".md5