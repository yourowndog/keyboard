#!/usr/bin/env python3
"""Rebuild the jDocMunch documentation index for this repository, and refuse to
report success unless the result is actually usable.

Run it with the pinned jDocMunch package, from the repository root::

    ~/.local/bin/uvx --from 'jdocmunch-mcp[openai,fastembed]==1.139.1' \
        python tools/docs/reindex_docs.py

This script exists because three consecutive re-index attempts "succeeded" while
producing an index that could not answer a semantic query. Two distinct defects
caused that, and this script is the guard against both.

**1. Silent embedding blanking.** jDocMunch's embedding providers swallow
per-batch exceptions and substitute empty vectors::

    # jdocmunch_mcp/embeddings/provider.py, _OpenAICompatibleProvider
    except Exception:
        embeddings.extend([[] for _ in batch])

There is no retry, no backoff, and nothing propagates to the caller. A provider
that rate-limits therefore yields an index that reports success while silently
dropping most of the corpus -- the previous index embedded 837 of 2452 sections
(34%) and disabled semantic search, with no error anywhere. `verify_embeddings`
below re-derives coverage from the embeddings sidecar on disk rather than
trusting the indexer's own report, because the indexer's report is precisely
what was unreliable.

**2. Corpus drift.** jDocMunch indexes any textual file it recognises, so
generated JSON, Android manifests, build files, and multi-hundred-kilobyte
wordlists crowd out the prose. One generated file, `harvest_manifest.json`,
accounted for 535 sections -- 22% of the entire index -- and a bare scalar
section such as `neuralTop: 0.62` gives a summarizer nothing to summarize, so it
confabulates a plausible-sounding purpose. Keeping the exclusion list in version
control here makes the corpus boundary reviewable instead of living in whoever's
shell history rebuilt it last.

Both AI workloads run on hardware we own: embeddings in-process via FastEmbed,
summaries against Titan's llama.cpp router over Tailscale. See
``~/.local/bin/mcp-jdocmunch``, which sets the same environment for the MCP
server so interactive queries and this script agree.
"""

from __future__ import annotations

import json
import argparse
import os
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
REPO_NAME = "keyboard"

# Providers this index is expected to have been built with. A mismatch means
# something re-pointed the index at a cloud endpoint, which is how the corpus
# ended up rate-limited in the first place.
EXPECTED_EMBEDDING_PROVIDERS = {"fastembed", "sentence-transformers"}

# Paths excluded from the documentation corpus. Each entry is a gitignore-style
# pattern. Grouped by why it is excluded, because "why" is the part that decays.
EXCLUDE_PATTERNS = [
    # --- Build output, tooling, and vendored trees -----------------------
    "**/__pycache__/**",
    "**/build/**",
    "build/**",
    ".ccb/**",
    ".gradle/**",
    ".idea/**",
    "fastlane/**",
    "logs_llm/**",
    "node_modules/**",
    "vendor/**",
    # --- Formats that are not prose --------------------------------------
    "**/*.ipynb",
    "**/*.svg",
    # HTML here is either a rendered duplicate of a Markdown source
    # (research/swipe-training/REVIEW_BRIEF.html) or a UI shell
    # (tools/previewer/index.html). The Markdown original is authoritative.
    "**/*.html",
    # --- Android/native build descriptors --------------------------------
    # Structurally parseable, semantically empty: five AndroidManifest.xml
    # files plus lint/CMake config contributed ~59 sections of element names.
    "**/AndroidManifest.xml",
    "**/CMakeLists.txt",
    "**/lint.xml",
    "app/schemas/**",
    # Third-party library and license metadata, not documentation.
    "app/src/main/config/**",
    # Machine schema, not prose: 108 sections of `$ref`, `type`, and `const`
    # keys, 80 of which have titles short enough to be sent to the summarizer.
    # The Snygg concepts are documented in docs/theming/.
    "lib/snygg/schemas/**",
    # --- Packaged runtime assets -----------------------------------------
    # 181 packaged layout/theme JSON files. The layouts are documented in
    # docs/keyboard/; the assets themselves are data.
    "app/src/main/assets/**",
    "app/src/main/res/**",
    # --- Generated data products -----------------------------------------
    # Read these through tools/harvesting/ instead. harvest_manifest.json alone
    # was 535 sections of per-file bookkeeping.
    "data/harvest/derived/**",
    "data/harvest/raw/**",
    "data/harvest/reports/harvest_manifest.json",
    "research/swipe-training/*.json",
    "research/swipe-training/legacy/**",
    "training/data/**",
    # Extracted theme dumps: ~419 sections of colour tokens.
    "research/theme-archive/loose/**",
    # --- Wordlists and dictionaries --------------------------------------
    # Hundreds of kilobytes that parse as a single meaningless section. The
    # vocabulary work reads these directly; see tools/dictionary/.
    "**/*.cleaned.txt",
    "app/src/main/baseline-prof.txt",
    "dict_sources/personal_vocabulary.json",
    "research/swipe-training/futo_words_unique.txt",
    "research/swipe-training/target_swipe_vocabulary_supplement.txt",
]


SUMMARY_STATS: dict[str, int] = {"deterministic": 0, "split_retry": 0, "fallback": 0}


def install_summarizer_alignment_fix() -> None:
    """Stop the summarizer from attaching summaries to the wrong sections.

    jDocMunch summarizes eight sections per request and asks the model for a
    numbered list, then assigns each summary by the number the model emitted::

        # jdocmunch_mcp/summarizer/batch_summarize.py, _parse_response
        summaries[num - 1] = m.group(2).strip()

    Nothing checks that the model returned as many lines as there were
    sections. A section whose body is whitespace gives the model nothing to
    say, so it silently drops that entry and renumbers the rest from 1 -- and
    every remaining summary lands one slot early. Observed on
    docs/development/upstream-backport-radar.md, where the section
    "Feature 2.1: Numeric Field Composing" was captioned with the summary
    belonging to "Feature 2.2: Stylus / S-Pen Touch". The result is not a
    missing summary, which would be obvious; it is a confident and wrong one,
    which is not. jDocMunch's own guard (``and s.content``) rejects only empty
    bodies, and whitespace passes it.

    Two changes, applied by monkeypatch because the defect is upstream:

    1. Sections with no prose body never reach the model. They get their
       deterministic Tier 1 heading summary, which is what they would have
       received anyway, and stop perturbing their batch's numbering.
    2. A response that does not yield a summary for every section in the batch
       is rejected wholesale rather than applied positionally. The batch is
       bisected and retried, down to single sections, so one bad response can
       misalign nothing.
    """
    from jdocmunch_mcp.summarizer import batch_summarize as B

    def summarize_aligned(self, batch: list) -> None:
        if not batch:
            return
        try:
            text = self._call_api(B._build_prompt(batch))
        except Exception:
            text = ""

        summaries = B._parse_response(text, len(batch))
        if all(s.strip() for s in summaries):
            for section, summary in zip(batch, summaries):
                section.summary = summary
            return

        if len(batch) == 1:
            SUMMARY_STATS["fallback"] += 1
            batch[0].summary = B.title_fallback(batch[0])
            return

        # Incomplete response: never assign positionally, bisect and retry.
        SUMMARY_STATS["split_retry"] += 1
        midpoint = len(batch) // 2
        summarize_aligned(self, batch[:midpoint])
        summarize_aligned(self, batch[midpoint:])

    def summarize_one_batch(self, batch: list) -> None:
        pending = []
        for section in batch:
            if (section.content or "").strip():
                pending.append(section)
            else:
                SUMMARY_STATS["deterministic"] += 1
                section.summary = B.heading_summary(section) or B.title_fallback(section)
        summarize_aligned(self, pending)

    B._BaseSummarizer._summarize_one_batch = summarize_one_batch


def configure_providers() -> None:
    """Pin the local providers and trusted summary model for this corpus."""
    os.environ["JDOCMUNCH_EMBEDDING_PROVIDER"] = "fastembed"
    os.environ["JDOCMUNCH_SUMMARIZER_PROVIDER"] = "openai-compatible"
    os.environ["JDOCMUNCH_SUMMARIZER_URL"] = "http://100.104.232.94:8080/v1"
    os.environ["JDOCMUNCH_SUMMARIZER_MODEL"] = "qwen3.8-27b"
    os.environ["JDOCMUNCH_SUMMARIZER_API_KEY"] = "local"


def verify_embeddings(storage: Path) -> tuple[bool, str]:
    """Re-derive embedding coverage from disk, exactly.

    Every distinct section body must have a vector. Sections are embedded by
    content hash, so identical bodies legitimately share one vector and a plain
    vectors/sections ratio understates coverage -- compare hash sets instead.

    Deliberately independent of the indexer's own reporting: the indexer
    reported success on an index that was two-thirds empty.
    """
    index_file = storage / "local" / f"{REPO_NAME}.json"
    sidecar = storage / "local" / f"{REPO_NAME}.embeddings.jsonl"
    if not index_file.exists():
        return False, f"no index at {index_file}"
    if not sidecar.exists():
        return False, f"no embeddings sidecar at {sidecar}"

    index = json.loads(index_file.read_text())
    sections = index.get("sections", [])
    required = {s["content_hash"] for s in sections if s.get("content_hash")}

    header: dict = {}
    embedded: set[str] = set()
    blanks = 0
    for line in sidecar.read_text().splitlines():
        line = line.strip()
        if not line:
            continue
        record = json.loads(line)
        if record.get("_header"):
            header = record
            continue
        # Sidecar keys carry a provider-version suffix, e.g. "<sha>#pv1".
        content_hash = str(record.get("hash", "")).split("#")[0]
        if record.get("vector"):
            embedded.add(content_hash)
        else:
            blanks += 1

    provider = header.get("provider", "?")
    missing = required - embedded
    detail = (
        f"provider={provider} model={header.get('model', '?')} "
        f"vectors={len(embedded)}/{len(required)} distinct bodies "
        f"across {len(sections)} sections, blanks={blanks}"
    )

    if provider not in EXPECTED_EMBEDDING_PROVIDERS:
        return False, f"unexpected embedding provider -- {detail}"
    if blanks:
        return False, f"{blanks} blank vectors written -- {detail}"
    if missing:
        sample = ", ".join(sorted(missing)[:3])
        return False, f"{len(missing)} sections never embedded ({sample}) -- {detail}"
    return True, detail


def summarizer_reachable(timeout: float = 3.0) -> tuple[bool, str]:
    """Probe the configured summarizer before letting an index run start.

    A run that proceeds while the summarizer is unreachable does not fail --
    it silently writes title-fallback summaries for every section it touches,
    which is indistinguishable from a good index until someone searches. On a
    full rebuild that ruins the corpus; on an incremental run it quietly
    corrupts whichever files were just edited. Neither is worth the risk, so
    callers that cannot supervise the result should pass --require-summarizer
    and skip the run instead.

    Not a correctness guarantee: this cannot detect a reachable endpoint that
    returns empty `message.content` (the Qwen3-thinking failure mode that the
    `-nothink` model alias exists to avoid). It only rules out the common case
    of the host being down.
    """
    import json
    import urllib.error
    import urllib.request

    base = os.environ["JDOCMUNCH_SUMMARIZER_URL"].rstrip("/")
    want = os.environ["JDOCMUNCH_SUMMARIZER_MODEL"]
    req = urllib.request.Request(
        f"{base}/models",
        headers={"Authorization": f"Bearer {os.environ['JDOCMUNCH_SUMMARIZER_API_KEY']}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            served = {m.get("id") for m in json.load(resp).get("data", [])}
    except (urllib.error.URLError, OSError, ValueError) as exc:
        return False, f"{base} unreachable ({exc.__class__.__name__}: {exc})"
    if want not in served:
        return False, f"{base} is up but does not serve {want!r} (has: {sorted(served)})"
    return True, f"{base} serving {want}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__ and __doc__.splitlines()[0])
    parser.add_argument(
        "--incremental",
        action="store_true",
        help="Re-index only files that changed since the stored index. Exclusion "
             "patterns still apply, and unchanged sections keep their existing "
             "summaries and embedding vectors, so cost is proportional to the edit "
             "rather than to the corpus.",
    )
    parser.add_argument(
        "--require-summarizer",
        action="store_true",
        help="Probe the summarizer first and exit 0 without indexing if it is "
             "unreachable. For unsupervised callers (hooks): leaving a file stale "
             "is recoverable, writing title-fallback summaries silently is not.",
    )
    args = parser.parse_args(argv)

    configure_providers()

    if args.require_summarizer:
        ok, detail = summarizer_reachable()
        if not ok:
            print(f"skipping index run: {detail}", file=sys.stderr)
            return 0

    install_summarizer_alignment_fix()

    from jdocmunch_mcp.tools.index_local import index_local

    print(f"indexing {REPO_ROOT} as local/{REPO_NAME}")
    print(f"  embeddings: {os.environ['JDOCMUNCH_EMBEDDING_PROVIDER']}")
    print(f"  summaries : {os.environ['JDOCMUNCH_SUMMARIZER_MODEL']}"
          f" @ {os.environ['JDOCMUNCH_SUMMARIZER_URL']}")
    print(f"  exclusions: {len(EXCLUDE_PATTERNS)} patterns")

    result = index_local(
        path=str(REPO_ROOT),
        name=REPO_NAME,
        use_ai_summaries=True,
        use_embeddings=True,
        extra_ignore_patterns=EXCLUDE_PATTERNS,
        incremental=args.incremental,
    )

    for key in ("files_indexed", "documents", "sections", "sections_indexed",
                "semantic_search", "embedded_sections", "embedding_coverage"):
        if key in result:
            print(f"  reported {key}: {result[key]}")
    for warning in result.get("warnings", []) or []:
        print(f"  warning: {warning}")

    print(f"\nsummaries: {SUMMARY_STATS['deterministic']} deterministic"
          f" (no prose body), {SUMMARY_STATS['split_retry']} batches bisected"
          f" after an incomplete response, {SUMMARY_STATS['fallback']} fell back"
          f" to the section title")

    storage = Path(os.environ.get("JDOCMUNCH_STORAGE_PATH", Path.home() / ".doc-index"))
    ok, detail = verify_embeddings(storage)
    print(f"\nembedding verification: {'PASS' if ok else 'FAIL'}\n  {detail}")

    if not ok:
        print(
            "\nThe index is NOT usable for semantic search. Do not treat this as a\n"
            "successful re-index: jDocMunch reports success regardless.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
