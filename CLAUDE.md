# CLAUDE.md — Agent Guidelines & Exploration Policy

## Exploration and Indexing Policy (jCodemunch & jDocMunch)

Preserve context tokens at all costs. Use **jCodemunch** for code navigation and **jDocMunch** for documentation/textual navigation. These are not suggestions and not "when convenient" — they are the required entry points. Do not dump full files and do not use raw shell tools for exploration.

**The rule, stated as a hard constraint:**

- Code file → jCodeMunch. Never `Read`/`Grep`/`Glob`/`Bash` to explore it.
- Doc or textual file (`.md`, `.txt`, `.xml`, `.json`, `.rst`, `.html`) → jDocMunch. Never `Read`/`Grep`/`Glob`/`wc`/`cat`/`head` to explore it.
- **Only exception (both tools):** you are about to edit the file, and the harness requires a `Read` before `Edit`/`Write` will succeed. Read to edit, never read to explore.

If you catch yourself reaching for `Read` on a `.md` file to find out what it says, that is the violation. Search sections first, then pull only the sections you need.

**Session Start & Index Maintenance:**
1. **Refresh Code Index**: Run `resolve_repo { "path": "." }` / `uvx jcodemunch-mcp index .` to verify/refresh the code index (750 source code files, ~4,462 symbols).
2. **Refresh Doc Index**: Verify/refresh the documentation index via `doc_list_repos`, then `index_local { "path": ".", "name": "keyboard" }` if stale (995 docs/schemas/textual files, ~13,455 sections).
   - **The canonical doc repo identifier is `local/keyboard`.** Pass exactly that as `repo` on every jDocMunch call.
   - `doc_list_repos` also lists stale forks — `local/keyboard-docs` and `local/keyboard-local-docs`. These are **obsolete snapshots**. Do not read from them; their content is months behind HEAD and will produce stale evidence.
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
- If PostToolUse hooks are installed (Claude Code only), edited files are auto-reindexed
- Otherwise, call `register_edit` with edited file paths to invalidate caches and keep the index fresh
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
