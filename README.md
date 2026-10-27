# AI Collab Workspace — Multi-AI Collaboration for Android

A native Android application built with Kotlin and Jetpack Compose designed for a **Moto G Power 2025** running Android 16 (MediaTek Dimensity 6300, 8 GB RAM, 120 Hz display).

AI Collab Workspace enables group-chat collaboration across multiple AI providers (**Google Gemini**, **OpenAI**, and **Anthropic Claude**), allowing users to route messages between them, execute sandboxed workspace tools, manage editable code and document artifacts, and perform bounded agent-to-agent reviews.

---

## Architecture & Features

### 1. Multi-Provider Architecture
- **Unified Adapter Interface (`AiProviderAdapter`):**
  - **Google Gemini:** Direct REST API to `https://generativelanguage.googleapis.com/v1beta/models/...:generateContent` with structured function declarations, tool response loops, and token tracking. Can use user-supplied key or `BuildConfig.GEMINI_API_KEY`.
  - **OpenAI:** Direct REST API to `https://api.openai.com/v1/chat/completions` supporting `gpt-4o-mini`, `gpt-4o`, `gpt-4-turbo`, tool calls, and token usage.
  - **Anthropic Claude:** Direct REST API to `https://api.anthropic.com/v1/messages` supporting `claude-3-5-haiku` and `claude-3-5-sonnet` with tool-use blocks and token tracking.
- **Verified Connectivity:** The app does not assume a provider is connected until a real HTTP verification call succeeds via the "Verify" button or active generation.
- **Independent Operation:** Gemini is usable on its own; OpenAI and Anthropic are optional.

### 2. Security & Credentials
- **Hardware-Backed Keystore:** API keys are encrypted at rest using AES-256-GCM via the Android Keystore (`AndroidKeyStore`).
- **Zero Plaintext Key Storage:** Keys are never committed or logged, and only masked keys (`AIza••••••••1234`, `sk-••••••••5678`) are displayed in the UI.
- **Security Notice:** The app explicitly informs the user that client-side Keystore encryption provides strong on-device defense-in-depth, but cannot guarantee protection if the physical device itself is compromised or rooted.

### 3. Sandboxed Tool Execution System
The tool registry provides bounded, safe capabilities strictly confined to the application's internal sandboxed directory (`context.filesDir/workspace`):
1. `list_files(subpath)`: List directory contents safely.
2. `read_file(path)`: Read file text with strict path traversal checking.
3. `create_file(path, content)`: Create or update a file.
4. `edit_file(path, find_text, replace_text)`: String replacement in file.
5. `search_files(query)`: Recursive text search across workspace files.
6. `validate_json_or_config(content, format)`: Syntax validation for JSON and properties.
7. `calculate_expression(expression)`: Safe math evaluator without eval().
8. `preview_diff(path, new_content)`: Generates line-by-line unified diffs.

#### Consequential Action Approvals
Whenever an agent invokes a tool that would overwrite or modify an existing file, the app intercepts execution, displays a **Tool Approval Dialog** with a unified diff preview, and waits for explicit user approval before applying changes.

### 4. Shared Conversation & Agent Review
- **Routing:** Broadcast to all enabled agents or route to individual providers.
- **Distinct Identities:** Unique brand colors, avatars, and labels for Gemini (Google Blue), OpenAI (Emerald), Anthropic (Warm Coral), and User.
- **Agent-to-Agent Review:** Send an agent's response to another agent for critique with strictly bounded review passes (1 or 2 rounds maximum) to prevent infinite loops.
- **Controls:** Immediate Stop button for cancellation, retry buttons on failed requests, and per-agent error diagnostics.

### 5. Artifact Workspace
- Real filesystem files in sandboxed storage (`/workspace`).
- Monospace editor with line numbers and save summaries.
- Version history snapshots created on every edit, with one-tap version restore.
- Unified diff viewer highlighting added (`+`) and removed (`-`) lines.
- Sandboxed HTML live preview via restricted Android WebView (`allowFileAccess = false`, `allowContentAccess = false`).
- System share sheet integration for exporting artifacts.

### 6. Spend & Quota Controls
- Per-provider token usage counters (prompt tokens, completion tokens, total tokens).
- Approximate USD cost estimation.
- Configurable warning threshold limit (e.g. 100,000 tokens) with auto-pause to prevent unexpected expenditures.

---

## Target Device Optimizations (Moto G Power 2025)
- **120 Hz Display:** Minimal recomposition overhead, `remember` and `derivedStateOf` for list operations, optimized LazyColumn rendering.
- **MediaTek Dimensity 6300 & 8 GB RAM:** Direct coroutine concurrency without heavy dependency injection overhead.
- **Android 16 (API 36):** Full edge-to-edge support with proper WindowInsets.

---

## Building & Testing

### Compile the APK:
Use the standard build system or:
```bash
gradle :app:assembleDebug
```

### Run Unit & Robolectric Tests:
```bash
gradle :app:testDebugUnitTest
```
