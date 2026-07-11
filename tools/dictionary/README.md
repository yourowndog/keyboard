# Dictionary maintenance tools

These scripts directly rewrite packaged dictionary assets. They are maintenance
operations, not part of the routine harvest snapshot workflow.

- `inject_anchors.py` adds a curated set of phrase continuations.
- `rescale_bigrams.py` applies nonlinear frequency scaling.
- `clean_bigram_spam.py` removes a fixed set of SMS tokens and development
  jargon patterns.

Run them only from the repository root, review the asset diff, and execute the
autocorrect tests afterward. They are intentionally separated from
`tools/harvesting/`: harvesting captures and derives evidence; these tools
mutate shipped runtime data.

`dict_sources/` contains large source corpora and its own conversion input.
`utils/` contains older upstream dictionary/config generators. Neither is a
runtime asset directory.
