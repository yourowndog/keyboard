# Glide and Swipe Input

> Status: Canonical code map; operationally shelved  
> Last verified: 2026-07-11  
> Verified against: gesture detector, `GlideTypingManager`, classifier code,
> NLP scoring interface, packaged swipe assets, and swipe-training tools

Two features share “swipe” terminology:

1. Directional gestures on the keyboard or individual keys, mapped to actions.
2. Glide typing, where a continuous path produces word candidates.

## Live glide path

`TextKeyboardLayout` sends pointer events to the glide detector and
`GlideTypingManager`. The active evaluated character layout is supplied to the
classifier. Candidate words are produced by the classifier and may use the NLP
provider's word/context scoring interface.

Glide typing is disabled for password variations and depends on user
preferences and editor suitability.

The code path is present, but live validation on the target device has never
produced acceptable results. Treat glide as shelved behavior, not a supported
current capability, until a focused classifier effort revives it.

## Directional gestures

Keyboard, spacebar, and delete gestures map to configured `SwipeAction` values.
These include cursor movement, deletion, layout/subtype switching, hiding the
keyboard, and other commands. They are separate from glide word decoding.

## Shelved trained/precomputed work

`PrecomputedGestureCache` looks only for `ime/swipe/futo_swipes.bin` and treats
its absence as expected. No runtime source references the former 31 MB
`precomputed_gestures.json`; its generator was part of the shelved experiment.
That JSON artifact and its training scripts are preserved under
`research/swipe-training`, outside packaged Android assets.
