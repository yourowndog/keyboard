# OmniBoard Agent Guide

OmniBoard is Sam's personal Android keyboard and daily driver. It is derived
from FlorisBoard, so package and class names frequently retain the FlorisBoard
name. Optimize for reliability and Sam's actual workflow, not hypothetical
distribution requirements.

<!-- This file is the single source of truth for agent instructions in this repo.
     CLAUDE.md is a one-line `@AGENTS.md` import, because Claude Code discovers
     CLAUDE.md and not AGENTS.md. Edit THIS file; do not add content to CLAUDE.md
     unless it is genuinely Claude-specific. -->

## Orientation

Read [docs/README.md](docs/README.md), then
[docs/development/agent-reorientation.md](docs/development/agent-reorientation.md)
and the relevant subsystem guide:

- Layouts, keys, geometry, popups, or customization:
  [docs/keyboard/README.md](docs/keyboard/README.md)
- Autocorrect, suggestions, dictionaries, phrases, or neural scoring:
  [docs/autocorrect/README.md](docs/autocorrect/README.md)
- Snygg or theme work: [docs/theming/README.md](docs/theming/README.md)
- Voice and AI integrations: [docs/architecture/voice-ai.md](docs/architecture/voice-ai.md)
- Builds and tests: [docs/development/building.md](docs/development/building.md)

Do not infer current behavior from historical journals, generated reports, or
documents calling themselves "reality." Superseded root handoffs and manuals
were removed after their durable knowledge was extracted.

## Working rules

- Preserve unrelated user changes. The worktree may already be dirty.
- Explain the behavioral reason before a non-trivial code change.
- Verify source claims against live code, tests, schemas, and assets.
- For layout/touch/theme/IME behavior, include device validation when source and
  unit tests cannot establish the result.
- Do not push, invoke the build factory, install an APK, or rewrite harvested
  data unless the task authorizes it.
- Keep plans in `ROADMAP.md`; keep current behavior in `docs/`.
- Update the closest canonical document when behavior changes. Avoid creating a
  new root-level handoff as a substitute.

## Build workflow

Development builds are local or use the `factory` remote on Beksinski. GitHub
Actions is not the normal build path. See the building guide before invoking a
remote build.

## Priority knowledge areas

The whole codebase matters, but layout/key programming/geometry and
autocorrect/neural scoring are especially easy to misunderstand. Use their
detailed guides and preserve hard-won behavior when changing them.

## Exploration and Indexing Policy (jCodemunch & jDocMunch)

Preserve context tokens at all costs. Use **jCodemunch** for code navigation and **jDocMunch** for documentation/textual navigation. These are not suggestions and not "when convenient" — they are the required entry points. Do not dump full files and do not use raw shell tools for exploration.

**The rule, stated as a hard constraint:**

- Code file → jCodeMunch. Never `Read`/`Grep`/`Glob`/`Bash` to explore it.
- Doc or textual file (`.md`, `.txt`, `.xml`, `.json`, `.rst`, `.html`) → jDocMunch. Never `Read`/`Grep`/`Glob`/`wc`/`cat`/`head` to explore it.
- **Only exception (both tools):** you are about to edit the file, and the harness requires a `Read` before `Edit`/`Write` will succeed. Read to edit, never read to explore.

If you catch yourself reaching for `Read` on a `.md` file to find out what it says, that is the violation. Search sections first, then pull only the sections you need.

**Session Start & Index Maintenance:**
1. **Refresh Code Index**: Run `resolve_repo { "path": "." }` / `uvx jcodemunch-mcp index .` to verify/refresh the code index (805 source code files, ~5,168 symbols).
2. **Refresh Doc Index**: Rebuild with `tools/docs/reindex_docs.py` — **not** a bare `index_local` call. The corpus is 89 prose files / ~893 sections; a default index pulls in ~2,500 sections of packaged assets, generated JSON, and wordlists that drown the prose. The script owns the 33-pattern exclusion list, pins local providers, and verifies the result. Pass `--incremental` to re-index only changed files (exclusions still apply, unchanged sections keep their summaries and vectors); add `--require-summarizer` to skip the run when Titan is down rather than write title-fallback summaries.
   - **The canonical doc repo identifier is `local/keyboard`.** Pass exactly that as `repo` on every jDocMunch call.
   - Nine obsolete forks (`local/keyboard-docs`, `local/keyboard-core-docs`, `local/omniboard-docs`, and others) were deleted on 2026-09-03; `local/keyboard` is now the only keyboard index. If a fork ever reappears in `doc_list_repos`, do not read from it — it will produce stale evidence.
   - Never trust a "successful" index without checking coverage: jDocMunch reports success even when it silently embedded a fraction of the corpus. The script's verification gate is the check — see its module docstring for the two defects it guards.
3. `suggest_queries` — when exploring unfamiliar areas of the codebase or documentation.

**Code Exploration (jCodemunch):**
- Always use jCodemunch-MCP tools for code navigation. Never fall back to Read, Grep, Glob, or Bash for code exploration.
- **Exception:** Use `Read` only when you need to edit a file — the agent harness requires a `Read` before `Edit`/`Write` will succeed.
- symbol by name → `search_symbols` (add `kind=`, `language=`, `file_pattern=`, `decorator=` to narrow)
- decorator-aware queries → `search_symbols(decorator="X")` to find symbols with a specific decorator (e.g. `@property`, `@route`); combine with set-difference to find symbols *lacking* a decorator (e.g. "which endpoints lack CSRF protection?")
- string, comment, config value → `search_text` (supports regex, `context_lines`)
- database columns (dbt/SQLMesh) → `search_columns`

**Reading code:**
- before opening any file → `get_file_outline` first
- one or more symbols → `get_symbol_source` (single ID → flat object; array → batch)
- symbol + its imports → `get_context_bundle`
- specific line range only → `get_file_content` (last resort)

**Repo structure:**
- `get_repo_outline` → dirs, languages, symbol counts
- `get_file_tree` → file layout, filter with `path_prefix`

**Relationships & impact:**
- what imports this file → `find_importers`
- where is this name used → `find_references`
- is this identifier used anywhere → `check_references`
- file dependency graph → `get_dependency_graph`
- what breaks if I change X → `get_blast_radius`
- what symbols actually changed since last commit → `get_changed_symbols`
- find unreachable/dead code → `find_dead_code`
- class hierarchy → `get_class_hierarchy`

## Documentation Exploration (jDocMunch)

Always use jDocMunch-MCP tools for documentation and textual navigation. Never fall back to `Read`, `Grep`, `Glob`, `wc`, `cat`, or `head` for doc exploration. Every call takes `repo: "local/keyboard"`.

**Finding the right doc:**
- what documents exist, how they nest → `get_toc_tree` (nested) or `get_toc` (flat, document order)
- topic, concept, or question → `search_sections` — returns *summaries only*, ranked. Start here for almost everything.
- narrow a search to one file → `search_sections(doc_path="...")`
- headings within one known file → `get_document_outline` — the doc equivalent of `get_file_outline`. **Call this before pulling content from any file.**

**Reading docs:**
- one section, full content → `get_section` (takes a `section_id` from search/TOC/outline)
- several sections at once → `get_sections` (batch — one call, not N)
- a section that reads too thin alone → `get_section_context` — returns ancestor headings + content + child summaries, with a `max_tokens` budget. **Use this instead of giving up and reading the whole file.**

**Query phrasing:**
- `search_sections` fuses BM25 + embeddings with reciprocal rank fusion (k=60), which structurally penalises an answer that is strong in one channel and absent from the other. This repo's `semantic_weight` is pinned to **0.95** (the allowed ceiling) via `tune_weights`. Verified on this corpus: exact-match recall for identifiers, filenames, and error strings is unaffected.
- **Sentence-form questions still under-rank even at 0.95.** jDocMunch's stopword list contains no interrogatives, and `title` carries 3x field weight — so a "why X" / "how Y" heading hijacks the lexical channel for any question phrased as prose. This corpus is full of such headings. For a prose question, pass `semantic_only: true`.
- For keyword, identifier, or error-string lookups, the default hybrid is correct — do not override it.

**Doc health:**
- undocumented areas → `get_doc_coverage`
- dangling cross-references → `get_broken_links`

**Staleness discipline:**
- Pass `verify: true` on `get_section`/`get_sections` when the answer is load-bearing — it hashes content against the index and detects source drift.
- jDocMunch is a navigation aid, not a substitute for the checkout. Before **editing** a doc, confirm the text at current HEAD.
- If the index materially disagrees with the checkout, stop and report the mismatch rather than reasoning from stale sections.

**Anti-patterns — these are the ways this policy actually gets violated:**
- `wc -l` on a set of docs to "check sizes" before reading them → use `get_toc`/`get_document_outline`; size is irrelevant when you retrieve by section.
- "the file is only ~70 lines, I'll just Read it" → short files are still whole-file dumps. `search_sections` + `get_section` costs less and stays targeted.
- `Read`ing four architecture docs up front "for context" → search for what the task needs; pull sections on demand.
- `grep`/`git ls-files | grep` to locate a doc by name or topic → `search_sections`, or `get_toc_tree` when you need the layout.

## Session-Aware Routing

**Opening move for any task:**
1. `plan_turn { "repo": "...", "query": "your task description", "model": "<your-model-id>" }` — get confidence + recommended files; the `model` parameter narrows the exposed tool list to match your capabilities at zero extra requests.
2. Obey the confidence level:
   - `high` → go directly to recommended symbols, max 2 supplementary reads
   - `medium` → explore recommended files, max 5 supplementary reads
   - `low` → the feature likely doesn't exist. Report the gap to the user. Do NOT search further hoping to find it.
3. **One-call shortcut for a concrete task** — `assemble_task_context { "repo": "...", "task": "..." }` returns a single token-budgeted, source-attributed context capsule. It auto-classifies the task (explore / debug / refactor / extend / audit / review), auto-extracts anchor symbols, and runs the intent-appropriate sequence of the tools below end-to-end — so you get the whole context in one request instead of chaining the primitives by hand. Prefer it over a manual chain when the task is well-defined; fall back to step 1's routing when you need to decide *whether* the feature exists first.

**Interpreting search results:**
- If `search_symbols` returns `negative_evidence` with `verdict: "no_implementation_found"`:
  - Do NOT re-search with different terms hoping to find it
  - Do NOT assume a related file (e.g. auth middleware) implements the missing feature (e.g. CSRF)
  - DO report: "No existing implementation found for X. This would need to be created."
  - DO check `related_existing` files — they show what's nearby, not what exists
- If `verdict: "low_confidence_matches"`: examine the matches critically before assuming they implement the feature

**After editing files:**
- **The code index self-heals.** A user-level PostToolUse hook (`~/.claude/settings.json`, matcher `Edit|Write`) runs `jcodemunch-mcp hook-posttooluse`, so code files you edit are reindexed automatically. This is Claude Code only — other agents must invalidate manually.
- **The doc index does not.** There is no equivalent jDocMunch hook. After editing `.md` files, call `register_edit` with the paths; rerun `tools/docs/reindex_docs.py` when the change is large enough to need fresh summaries and embeddings.
- If `register_edit` is unavailable to your agent, say so rather than silently reasoning from a stale index.
- For bulk edits (5+ files), always use `register_edit` with all paths to batch-invalidate

**Token efficiency:**
- If `_meta` contains `budget_warning`: stop exploring and work with what you have
- If `auto_compacted: true` appears: results were automatically compressed due to turn budget
- Use `get_session_context` to check what you've already read — avoid re-reading the same files

## Model-Driven Tool Tiering

Your jcodemunch-mcp server narrows the exposed tool list based on the model you are running as. To avoid wasting requests on primitives when a composite would do, always include `model="<your-model-id>"` in your opening `plan_turn` call.

Replace `<your-model-id>` with your active model:
- Claude Opus variants → `claude-opus-4-7` (or any `claude-opus-*`)
- Claude Sonnet variants → `claude-sonnet-4-6`
- Claude Haiku variants → `claude-haiku-4-5`
- GPT-4o / GPT-5 / o1 / Llama → use the model id as printed by your runner

The `model=` parameter rides on the existing `plan_turn` call — it does **not** add a separate tool invocation. If `plan_turn` is not appropriate for a non-code task, call `announce_model(model="...")` once instead.
