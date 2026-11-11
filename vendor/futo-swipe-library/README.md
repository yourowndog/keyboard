# Rebuilding `app/libs/futo-swipe-release.aar`

`app/libs/futo-swipe-release.aar` is a binary we build ourselves from FUTO's open-source
swipe engine. This directory holds everything needed to reproduce it, so the AAR is never
an unexplained blob in the tree.

## Why we patch upstream at all

FUTO's published library cannot be driven from Kotlin as-is. `SwipeDecoder.setMode()` takes
vocabularies as raw native `ITrie*` pointers, which FUTO's own keyboard supplies from its
AOSP dictionary code -- and no JNI entry point constructs one. A caller holding only the AAR
has no way to hand the engine a word list, so recognition returns nothing. (Their own
instrumented test calls a `SwipeDecoder(vocabPath = ...)` constructor that does not exist in
the published source; the snapshot is mid-refactor.)

`omniboard-vocab-trie.patch` closes that gap by exposing the trie the library **already
ships** (`swipe_decoder::Trie` / `load_trie_simple`) through three JNI functions and a small
Kotlin wrapper. It adds no decoding logic of its own. The patch also pins `ndkVersion` and
narrows the build to arm64-v8a.

## Reproducing

```sh
git clone --recursive https://gitlab.futo.org/keyboard/swipe-library
cd swipe-library
git checkout $(cat vendor/futo-swipe-library/UPSTREAM_COMMIT)   # 1b13f2c8
git apply /path/to/vendor/futo-swipe-library/omniboard-vocab-trie.patch
echo "sdk.dir=$ANDROID_HOME" > android/local.properties
./gradlew :android:assembleRelease
cp android/build/outputs/aar/android-release.aar \
   /path/to/keyboard/app/libs/futo-swipe-release.aar
```

Requires NDK 27.3.13750724 and CMake 3.22.1.

## Verify ExecuTorch actually linked

`cmake/executorch.cmake` treats a missing `libexecutorch.a` as *build without ExecuTorch* --
it emits a warning, not an error, and produces a stub with no neural net at all. Always
confirm the configure log says:

```
-- ExecuTorch support: ON
-- Found ExecuTorch: .../cmake-out-arm64-v8a/libexecutorch.a
```

A stub build installs and runs fine. It just never recognises anything.

## Models

The three `.pte` models under `app/src/main/assets/futo/` come from HuggingFace
`futo-org/futo-swipe`. Each keeps its own subdirectory because the engine selects a tuned
scoring profile from the *combination* of loaded models, keyed off each model's
`metadata.json` -- that file must sit beside its `.pte`.
