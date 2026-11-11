# OmniBoard Architecture & Work-Mode Status Presentation Research Report

## Task Context
- **Task ID**: `t_192f4ec2`
- **Title**: OmniBoard code audit: current state, capabilities, and Work-mode status presentation feasibility
- **Board**: `keyboard` (`~/.hermes/kanban/boards/keyboard/kanban.db`)
- **Date**: 2026-10-28

---

## 1. Executive Summary & Verification of Current State

OmniBoard (`/home/sam/projects/keyboard`) is Sam's personal Android soft keyboard (forked and specialized from FlorisBoard). A comprehensive codebase audit, verification of git commit history, and inspection of recent merged fixes show:
1. **Recent Code & Verification Baseline**:
   - Head commit: `87dbba44c voice: watchdog the transcription mutex; drain skips stuck takes` (merged and verified by task `t_690341e0`).
   - Prior critical fixes: `2c4ecac0b` (hardened spatial coordinates across keyboard layouts, Stage 06) and `ce8d2a8a` (resolved Compose snapshot crash in `describeRows`).
   - Build status: Gradle build passes (`assembleDebug`), automated tests passing (14 unit tests green).
2. **Current Capabilities**:
   - **Voice Subsystem**: Robust audio capture (`Recorder`, `WavTools`), watchdog-bounded local/remote Whisper transcription (`WhisperClient` via OkHttp relay to `openai`, `titan`, or `weakling`), room-persisted takes (`VoiceDatabase`, `VoiceTakeDao`), and animated UI (`WhisperBar`, `VoiceVisualizer`).
   - **AI / Text Assistance**: Gemma 2B/local endpoint completion (`GemmaClient`), invoked via `KeyCode.AI_GENERATE` (-307), with prompt modes `REWRITE`, `REPLY`, and `CONTINUE`.
   - **Smartbar System**: Jetpack Compose-based UI (`Smartbar.kt`, `SmartbarLayout.kt`, `QuickActionsRow.kt`), supporting actions rows, candidate suggestions, clipboard chips, inline autofill, and mode transitions (e.g. `ImeUiMode.VOICE`).
   - **Work-Mode Status**: Currently **0 lines of existing Work-mode or Hermes Kanban UI code** in the Android app. No prior "Work-mode" feature has been committed.

---

## 2. Detailed Technical Audit of Subsystems

### 2.1 Voice Subsystem & Whisper Relay
- **Implementation**: Located in `dev.patrickgold.florisboard.ime.voice` and `dev.patrickgold.florisboard.net.WhisperClient`.
- **Hardware & Backend Routing**: Supports three transcription providers (`OPENAI_CLOUD`, `TITAN_LOCAL`, `WEAKLING_LOCAL`). Integrates directly with the Brokentooth cluster topology (Weakling for CPU transcription, Titan for GPU whisper transcription).
- **State Management**: Reactive state exposed via Kotlin Coroutines `StateFlow` (`takes`, `transcriptionProvider`, `outputMode`, `pendingFiles`).
- **Resilience**: Watchdog timer bounds the transcription lock (`withTimeoutOrNull(20_000L)`) preventing audio drain lockups.

### 2.2 Text Processing & AI Assistance
- **Implementation**: `dev.patrickgold.florisboard.ime.nlp.GemmaClient` and `KeyboardManager.kt` (lines 1466–1509).
- **Functionality**: Triggers on `KeyCode.AI_GENERATE`. Context-aware: inspects editor selection for rewrite, clipboard history for reply drafting, or pre-cursor context for text continuation.
- **Persona**: Uses embedded system prompt (`gemma_persona.txt`) capturing Sam's casual, technically concise tone.

### 2.3 Smartbar Architecture & Action System
- **Implementation**: `Smartbar.kt`, `SmartbarMainRow`, `QuickActionsRow.kt`.
- **Layouts**: Supports `ACTIONS_ONLY`, `SUGGESTIONS_ACTIONS_SHARED`, `SUGGESTIONS_ACTIONS_EXTENDED`.
- **Switching Mechanism**: The central area in `SmartbarMainRow` swaps between `CandidatesRow()`, `InlineSuggestionsUi()`, and `WhisperBar()` based on `state.imeUiMode`.

---
## 3. Feasibility Analysis: Work-Mode Status Presentation

### 3.1 Proposed "Work-Mode" Concept
In Sam's Brokentooth workflow, Hermes tasks run on local/remote nodes (Sleeper, Sisyphus, Weakling, Titan) coordinated via Kanban SQLite databases (`~/.hermes/kanban.db` and board-specific DBs). When Sam is mobile (on his Galaxy S25 Ultra), OmniBoard serves as the primary input interface.

"Work-mode" status presentation would surface active task status, run states, or agent notifications directly inside OmniBoard.

### 3.2 Evaluation of Architectural Approaches

| Approach | Mechanism | Latency / Battery Impact | Code Footprint & Complexity | Reliability / Sandbox Constraint | Recommendation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A. Polling SQLite directly over ADB / ContentProvider** | Android ContentProvider or local file access | High battery drain; file permission boundaries on Android 14+ | High; requires storage access framework or custom sync service | Fragile; Android sandboxing restricts cross-app SQLite reads | **Not Recommended** |
| **B. Hermes HTTP / SSE Status Gateway (Relay)** | OkHttp client query to lightweight gateway / Tailscale endpoint (like WhisperClient) | Minimal: poll on keyboard open (debounced) or SSE push over local Wi-Fi / Tailscale | Low-Medium; mirrors existing `WhisperClient` pattern | High; robust, uses standard network permissions already granted | **Recommended** |
| **C. Push via System Notification Mirroring** | Android Notification Listener Service inspecting Hermes pushes | Near-zero polling overhead; event-driven | Medium; requires `NotificationListenerService` system permission | Dependent on Hermes sending push notifications (e.g. Telegram / NTFY) | **Viable Alternative** |

### 3.3 Recommended Smartbar Integration Architecture
1. **UI Surface**:
   - Introduce a new `ImeUiMode.WORK` or an active state chip within `SmartbarMainRow` (analogous to how `WhisperBar` mounts when `uiMode == ImeUiMode.VOICE`).
   - Alternatively, render a compact status chip in `QuickActionsRow` or the left/right action anchor displaying active agent state (e.g. `[● titan: running t_192f4ec2]`).
2. **Network Model**:
   - A clean Kotlin coroutine client (`HermesStatusClient`) using OkHttpClient with timeout/caching.
   - Endpoint: Hermes gateway endpoint (e.g. `http://sleeper.tailscale:port/api/status` or `http://weakling:port/api/board/active`).
3. **Theme & Design System**:
   - Must respect Snygg theme tokens (`FlorisImeUi`) so LCARS theme styling and contrast remain uniform.

---

## 4. Unresolved Questions & External Blockers

1. **Coordinator Dependency Deadlock in Board History**:
   - Parent card `t_ed4607a1` recorded a deadlock where the native goal judge rejected completion twice. However, child tasks `t_192f4ec2` and sibling `t_0ef5361a` are independently dispatched research/audit runs.
2. **Target Hermes Service Contract**:
   - Hermes Kanban runs as a CLI SQLite system on host nodes. For Android to consume task events without raw SQLite file sharing, Hermes needs an HTTP status endpoint or gateway notifier webhook. This boundary must be formalized before implementation begins.

---
## 5. Next Steps for Downstream Cards
- Feed this audit directly into `t_54917c0d` (review) and `t_653c1c3d` (next Work-mode feature proposal).
