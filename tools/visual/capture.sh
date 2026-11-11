#!/usr/bin/env bash
# Visual iteration loop: boot a headless emulator that mirrors icarion, install an
# OmniBoard APK with Sam's own preferences, and screenshot the keyboard in fixed states.
#
#   tools/visual/capture.sh <apk> <out-dir> [device-config.tar]
#
# Runs on sisyphus (KVM, Android SDK). See tools/visual/README.md.
set -euo pipefail

APK=${1:?apk}
OUT=${2:?out dir}
CONFIG=${3:-$HOME/omniboard-visual/device-config.tar}
SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/sam/.local/share/android-build-tools/android-sdk}}
AVD=omni-visual
IMAGE="system-images;android-35;google_apis;x86_64"
PKG=dev.patrickgold.florisboard.debug
IME=$PKG/dev.patrickgold.florisboard.FlorisImeService
PORT=5584
SERIAL=emulator-$PORT
export ANDROID_SDK_ROOT=$SDK ANDROID_AVD_HOME=$HOME/.android/avd
ADB="$SDK/platform-tools/adb -s $SERIAL"
mkdir -p "$OUT"

# icarion (Galaxy S25 Ultra): 1440x3120, density override 485.
if [[ ! -d $ANDROID_AVD_HOME/$AVD.avd ]]; then
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n $AVD -k "$IMAGE" -d pixel_7_pro --force
    cat >> "$ANDROID_AVD_HOME/$AVD.avd/config.ini" <<CFG
hw.lcd.width=1440
hw.lcd.height=3120
hw.lcd.density=480
hw.keyboard=no
hw.ramSize=4096
CFG
fi

if ! $ADB get-state >/dev/null 2>&1; then
    nohup "$SDK/emulator/emulator" -avd $AVD -port $PORT -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect -no-snapshot-save >"$OUT/emulator.log" 2>&1 &
    $ADB wait-for-device
    until [[ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do sleep 2; done
fi
$ADB shell wm density 485
$ADB shell settings put global window_animation_scale 0
$ADB shell settings put global transition_animation_scale 0
$ADB shell cmd uimode night yes

$ADB install -r "$APK" >/dev/null
$ADB shell am force-stop $PKG
if [[ -f $CONFIG ]]; then
    $ADB push "$CONFIG" /data/local/tmp/omni-config.tar >/dev/null
    $ADB shell chmod 644 /data/local/tmp/omni-config.tar
    $ADB shell run-as $PKG tar -xf /data/local/tmp/omni-config.tar
fi
# Android hands the IME back to the system keyboard after a package replace, sometimes a
# moment later, so set it until it sticks.
$ADB shell ime enable $IME >/dev/null
for _ in 1 2 3 4 5; do
    $ADB shell ime set $IME >/dev/null
    sleep 2
    [[ "$($ADB shell settings get secure default_input_method | tr -d '\r')" == "$IME" ]] && break
done
[[ "$($ADB shell settings get secure default_input_method | tr -d '\r')" == "$IME" ]] ||
    { echo "could not make OmniBoard the input method" >&2; exit 1; }
$ADB shell am force-stop com.google.android.settings.intelligence || true

shot() { sleep "${2:-1.5}"; $ADB exec-out screencap -p >"$OUT/$1.png"; echo "$OUT/$1.png"; }
# The keyboard's top edge, from the IME window's touchable region, for cropping.
kb_top() {
    $ADB shell dumpsys window windows | grep -A30 'Window{.*InputMethod' |
        sed -n 's/.*touchable region=SkRegion((0,\([0-9]*\),.*/\1/p' | head -1
}
# A focused text field: the Settings search box.
$ADB shell am start -W -a android.settings.APP_SEARCH_SETTINGS >/dev/null 2>&1 || \
    $ADB shell am start -W -n com.android.settings/.Settings >/dev/null
sleep 2
$ADB shell input keyevent KEYCODE_SEARCH || true
shot 01-idle 3
$ADB shell input text hel
shot 02-typing 3

# Crops for review: the whole keyboard, and the smartbar alone at full resolution.
TOP=$(kb_top); TOP=${TOP:-1800}
for f in 01-idle 02-typing; do
    magick "$OUT/$f.png" -crop "1440x$((3120 - TOP))+0+$TOP" +repage "$OUT/$f.keyboard.png"
    magick "$OUT/$f.png" -crop "1440x170+0+$TOP" +repage "$OUT/$f.smartbar.png"
done
echo "keyboard top: $TOP"
