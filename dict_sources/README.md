# Dictionary source corpora

Large upstream and personal inputs used to build OmniBoard dictionary assets.
The keyboard does not load files from this directory at runtime.

Packaged runtime data lives under `app/src/main/assets/ime/dict/`. Treat these
files as build inputs: record the generator and review generated asset diffs
before promotion. `convert_aosp_wordlist.py` converts the included AOSP source;
the broader legacy generators remain under `utils/`.
