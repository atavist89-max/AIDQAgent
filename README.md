# DQ Agent — AI-Powered Data Quality Intelligence

> **Autonomous analysis of data quality failures using on-device LLM inference.**  
> Built for financial data operations. Runs entirely on Android with local AI.

---

## 🆕 Fast GaaS Branch: Lightweight Governance-as-a-Service

This is the **`gaas-fast`** branch. It streamlines the GaaS layer into a fast, configurable policy prompt manager with automatic retry and human arbitration.

### What's Different from GaaS Enhancement?

| | **GaaS Enhancement** | **Fast GaaS** |
|---|---|---|
| **Governance model** | Trust scores, agent autonomy, negotiation, escalation levels, audit trails | Simple user-written policy prompts per gate |
| **Human intervention** | First-class queue system with approval/rejection | Final arbitration after 3 automatic retries |
| **Governance tab** | 5 sub-screens (Dashboard, Agent Detail, Policy Editor, Decision Queue, Audit Playback) | Single scrollable configuration screen |
| **Policy engine** | Hardcoded regex rules + visual condition builder | Free-text policy prompts editable per gate |
| **State files** | 8+ files including trust scores, policy rules, audit logs | Essential pipeline outputs + 2 config files |

---

## The Problem: Detection ≠ Intelligence

Current data quality monitoring excels at **detection**:
- ✓ Identify null values
- ✓ Flag schema drift  
- ✓ Measure freshness delays

It fails at **contextual reasoning**:
- ✗ Does this null corrupt tomorrow's regulatory report?
- ✗ Is this the 4th failure from the same owner indicating systemic overload?
- ✗ Which upstream system change caused this cascade?

**The cost:** Data stewards spend 2 hours per alert gathering context from documentation, catalog tools, and historical logs. Critical failures wait while humans do detective work.

---

## The Solution: 6-Stage Reasoning Pipeline with Fast GaaS

DQ Agent transforms raw DQ alerts into **executive intelligence** through a constraint-engineered pipeline designed for on-device LLM limits (~2,000 tokens).

### Stage Architecture

| Stage | Function | Token Budget | Output |
|-------|----------|--------------|--------|
| **1. Triage** | Severity assessment + downstream report impact | ~400 | `FULL_ANALYSIS` or `MINIMAL` |
| **2. Context Builder** | Entity lookup, column definitions, source system ID | ~1,200 | Condensed business context |
| **3. Pattern Detector** | Owner workload correlation, entity group health | ~1,000 | Systemic issue detection |
| **4a. Upstream Researcher** | Technical deep dive: architecture, root cause hypothesis | ~1,500 | Technical briefing (200-250 words) |
| **4b. Downstream Researcher** | Business impact: cascade analysis, stakeholder priority | ~1,300 | Impact assessment (200-250 words) |
| **4c. Synthesizer** | Executive narrative synthesizing both research reports | ~1,900 | Stewardship report (350-400 words) |

**Constraint Management:** 10-second thermal cooldown between stages prevents GPU throttling on mobile hardware.

### Fast GaaS Gate System

Between each stage is a configurable governance gate:

| Gate | Between Stages | Default Policy Prompt |
|------|---------------|----------------------|
| **Gate 1** | Stage 1 → Stage 2 | Ensure triage decision is consistent with severity and downstream impact |
| **Gate 2** | Stage 2 → Stage 3 | Verify entity metadata and catalog columns are correctly matched |
| **Gate 3** | Stage 3 → Stage 4A | Confirm pattern detection considers owner workload and group health |
| **Gate 4** | Stage 4A → Stage 4B | Validate upstream analysis uses actual system names and specific root-cause hypotheses |
| **Gate 5** | Stage 4B → Stage 4C | Validate downstream impact assessment prioritizes Class 2 reports and identifies stakeholders |

**Auto-Retry Loop:**
1. Stage completes and writes output JSON
2. If gate policy is enabled, the GaaS agent reviews the output using the fixed system prompt + user-defined policy prompt
3. If **APPROVED**, the gate turns green and the pipeline proceeds
4. If **REJECTED**, the gate turns red and the pipeline auto-retries:
   - **LLM stages (4A, 4B, 4C):** Re-run with GaaS feedback bullets injected into the prompt
   - **Deterministic stages (1, 2, 3):** Send the same cached output back for re-review
5. After **3 rejections**, human intervention is required with exactly two options:
   - **Accept Station Version** — override the gate (orange) and proceed with the agent's output
   - **Accept GaaS Assessment** — treat rejection as final and abandon the pipeline for this alert

**Gate Visual States:**
- 🔘 Gray hollow diamond — no policy configured
- 🔵 Blue — policy enabled, awaiting train
- 🟡 Yellow dashed — GaaS agent reviewing
- 🔴 Red pulsing — rejected
- 🟢 Green — approved
- 🟠 Orange — human overridden

### What This Enables

**Before (Current State):**
> "ALERT: Unexpected value distribution shift in LINK_ORDER_CUSTOMER. Severity: Critical."

**After (With DQ Agent):**
> **IMPACT:** Critical — affects Order-to-Customer Traceability report (operational) and 2 downstream executive dashboards. Predicted cascade to 3 additional datasets within 24 hours.  
> **ROOT_CAUSE:** Source system schema change during maintenance window (4 hours ago). Correlates with 3 other Adaptability failures.  
> **ACTION:** Contact API owner to revert validation rule — do not patch individual records.  
> **URGENCY:** 12 hours remaining of 1-day Critical resolution target. Owner has 4 open Critical failures indicating systemic governance gap, not isolated incident.

**Time savings:** 2 hours → 45 seconds per alert.

---

## Technical Implementation

### Hardware Stack

- **Device:** Samsung Galaxy S25+ (Android 16+, API 36)
- **LLM:** Gemma 4 E2B (~2.5GB) via Google LiteRT-LM
- **RAM:** 6GB available (2.5GB model overhead + 3.5GB system)
- **Development:** GitHub Codespaces (cloud IDE, no local Android SDK needed)
- **Deployment:** GitHub Actions CI → USB sideloading

### On-Device Data Architecture

```
/storage/emulated/0/Download/GhostModels/DQAgent/
├── data/                      # Read-only reference data
│   ├── dq_alerts.json         # 24 DQ checks (13 failing, 11 passing)
│   ├── entities.json          # 23 business entities with definitions
│   ├── entity_groups.json     # 8 functional groups with ownership
│   ├── catalog_columns.json   # 50+ column-level technical metadata
│   ├── reports.json           # 15 downstream report definitions
│   └── dq_knowledge.json      # Severity rules, dimension definitions, KPI thresholds
├── state/                     # Runtime pipeline state
│   ├── stage1.json            # Triage output
│   ├── stage2.json            # Context builder output
│   ├── stage3.json            # Pattern detector output
│   ├── stage4a.json           # Upstream researcher output
│   ├── stage4b.json           # Downstream researcher output
│   ├── stage4c.json           # Synthesizer output
│   ├── gaas_policies.json     # 5 gate policies (enabled + prompt per gate)
│   ├── station_prompts.json   # 6 station agent prompts
│   └── gaas_block.json        # Transient block state during retry loops
└── demo_input/                # File drop trigger for new alerts
    └── new_alert.json
```

### Key Engineering Decisions

**Why JSON files for state?**  
Gemma 4 E2B is stateless and single-threaded. Passing state via filesystem allows:
- Pipeline resumption after thermal throttling
- Debuggability (inspect any stage's output)
- Minimal token waste (no "remembering" previous context)

**Why 4 stages?**  
Token limit (~2,000) requires chunking. Each stage fits within budget while accumulating context. Alternative (single massive prompt) crashes the engine.

**Why mobile?**  
- Zero cloud dependencies (financial data stays on-device)
- Demonstrates edge AI capabilities
- Realistic constraint engineering (battery, thermal, memory)

---

## Project Structure

```
├── .github/workflows/build.yml     # CI: builds debug APK on every push/PR
├── app/build.gradle                # App-level build config (SDK 36, Compose, LiteRT-LM)
├── app/src/main/AndroidManifest.xml# App manifest: MANAGE_EXTERNAL_STORAGE + native libs
├── app/src/main/java/com/llmtest/
│   ├── MainActivity.kt             # Entry point: BottomNav (Create/Analyze/Governance), engine init, file polling
│   ├── CreatorScreen.kt            # 4-tier cascading dropdown alert builder with auto-fill from JSON
│   ├── GhostPaths.kt               # Centralized hard-coded paths to model, data, state, input
│   ├── DQDataClasses.kt            # @Serializable data models: Alert, Entity, Report, Catalog, AnalysisState
│   ├── GaaSDataClasses.kt          # Fast GaaS data models: GaaSPolicy, StationPrompt, GaaSBlockState, GateVisualState
│   ├── GovernanceConfig.kt         # Reads/writes gaas_policies.json and station_prompts.json with defaults
│   ├── GaaSController.kt           # Fast GaaS orchestrator: gate review, retry loop, human intervention state
│   ├── DQPipelineManager.kt        # Orchestrates Stage 1→4a→4b→4c with 10s thermal cooldowns + GaaS gate reviews
│   ├── Stage1Triage.kt             # Severity + downstream report impact → FULL_ANALYSIS or MINIMAL
│   ├── Stage2ContextBuilder.kt     # Entity lookup, column defs, source system → condensed context string
│   ├── Stage3PatternDetector.kt    # Owner workload correlation, group health → pattern detection
│   ├── Stage4UpstreamResearcher.kt # Technical Data Architect: root cause hypothesis + investigation path
│   ├── Stage4DownstreamResearcher.kt# Business Impact Analyst: cascade + stakeholder notification priority
│   ├── Stage4Synthesizer.kt        # Senior Data Steward: executive narrative for CDO briefing (on-demand tap-to-view)
│   ├── Stage4Synthesis.kt          # Legacy monolithic synthesis (retained for reference)
│   ├── EntityData.kt               # Centralized entity metadata loader and cache
│   ├── BugLogger.kt                # File-based timestamped logger (logs/bug_log.txt)
│   ├── GovernanceScreen.kt         # Single-scroll Governance tab: system prompt, 5 policy cards, 6 station editors
│   ├── HumanInterventionScreen.kt  # Full-screen arbitration UI after 3 failed auto-retries
│   ├── MetroMap.kt                 # Interactive pipeline visualization: stations, gates, train, pan/zoom, tooltips
│   ├── MetroStation.kt             # Stage station composable (no trust score pill)
│   ├── MetroGate.kt                # Governance gate composable with 6 visual states
│   ├── MetroTrain.kt               # Moving alert card with "Waiting at Gate" message when blocked
│   ├── MetroMapViewModel.kt        # Animation state for train position and agent thoughts
│   ├── AgentThoughtPanel.kt        # Expandable reasoning panel with markdown-formatted text
│   └── PolicyEvaluationPanel.kt    # Policy result display cards
├── build.gradle                    # Root project plugins (Android, Kotlin, Compose, Serialization)
├── settings.gradle                 # Project name + repository config (Google, Maven Central)
├── gradle.properties               # AndroidX, compile SDK override, JVM heap settings
├── gradle/wrapper/gradle-wrapper.properties  # Gradle version
├── demo_alert.json                 # Golden demo alert (LINK_ORDER_CUSTOMER, Critical, Adaptability)
└── README.md                       # This file
```

### What Each Component Does

| File | Responsibility |
|------|----------------|
| `MainActivity.kt` | Entry point with BottomNavigationBar (Create + Analyze + Governance tabs). Initializes LiteRT-LM engine, GaaS layer, and starts 5-second file poll loop. |
| `CreatorScreen.kt` | 4-tier cascading dropdown form (Type → Source → Dataset → Check). Auto-fills check name, severity, dimension, and owner email from JSON lookups. Writes alert JSON to `demo_input/new_alert.json`. |
| `GhostPaths.kt` | Single source of truth for all absolute file paths on device. Validates model availability (`>1GB`) and DQ data directory presence. |
| `DQDataClasses.kt` | Kotlinx Serialization data classes consumed by all stages. `AnalysisState` is the accumulator passed from Stage 1→4c. |
| `GaaSDataClasses.kt` | Fast GaaS data models: policies, station prompts, block state, gate visual states, review results. |
| `GovernanceConfig.kt` | Manages `gaas_policies.json` and `station_prompts.json`. Creates sensible defaults on first launch. |
| `GaaSController.kt` | Fast GaaS orchestrator. Checks gap policies, builds review prompts, parses LLM responses, manages gate visual states, handles human intervention resolution. |
| `DQPipelineManager.kt` | CoroutineScope-driven orchestrator. Handles stage progression through 1→2→3→4a→4b→4c with 10s `delay()` cooldowns. After each stage, triggers GaaS gate review with up to 3 auto-retries before human intervention. |
| `Stage1Triage.kt` | Loads `reports.json`, checks if the alert dataset feeds any executive (`Class 2`) reports. Decides `FULL_ANALYSIS` vs `MINIMAL`. Writes `stage1.json`. |
| `Stage2ContextBuilder.kt` | Loads `entities.json`, `catalog_columns.json`, `entity_groups.json`. Builds a condensed business context string (<1,200 tokens). Writes `stage2.json`. |
| `Stage3PatternDetector.kt` | Loads `dq_alerts.json` to count owner failures and compute entity group health score by functional group. Detects `owner_overload`, `group_collapse`, or `isolated_incident`. Writes `stage3.json`. |
| `Stage4UpstreamResearcher.kt` | Technical Data Architect sub-agent. Reads editable prompt from `station_prompts.json`. Analyzes source system architecture, builds root cause hypothesis. Writes `stage4a.json`. |
| `Stage4DownstreamResearcher.kt` | Business Impact Analyst sub-agent. Reads editable prompt from `station_prompts.json`. Assesses cascade chains, stakeholder priority. Writes `stage4b.json`. |
| `Stage4Synthesizer.kt` | Senior Data Steward sub-agent. Reads editable prompt from `station_prompts.json`. Synthesizes upstream + downstream research into executive narrative. Writes `stage4c.json`. Report is viewed on-demand by tapping the station. |
| `BugLogger.kt` | Thread-safe file logger. Logs every stage transition, file I/O error, and LLM exception to app-private storage. Accessible via "Logs" button in UI. |
| `GovernanceScreen.kt` | Single-scroll Governance configuration tab. Section A: fixed system prompt card. Section B: 5 policy cards with toggle + prompt field. Section C: 6 station-agent editor cards. |
| `HumanInterventionScreen.kt` | Arbitration UI shown after 3 failed GaaS retries. Displays station output, GaaS feedback, attempt history, and two buttons: Accept Station Version or Accept GaaS Assessment. |
| `MetroMap.kt` | Interactive canvas-based pipeline visualization. 6 stations, 5 gates, animated train, pan/zoom, formatted tooltips. Shows human intervention screen overlay when required. |
| `MetroStation.kt` | Circular station composable with icon, label, and editable agent name. Pulsing animation when active. Tapping a completed station opens its on-demand report view. |
| `MetroGate.kt` | Diamond-shaped gate composable supporting 6 visual states (inactive, active, reviewing, rejected, approved, overridden). |
| `MetroTrain.kt` | Alert card that moves along the pipeline. Shows dataset, severity, dimension, elapsed time, progress, and "Waiting at Gate" message when blocked. |
| `AgentThoughtPanel.kt` | Expandable bottom panel showing the active agent's reasoning with proper markdown formatting (bold, italic, bullets). |

---

## Data Model Integration

The system consumes 5 integrated data sources:

1. **DQ Alerts:** Check results with severity, dimension, owner
2. **Business Entities:** Logical data model with descriptions and groupings
3. **Entity Groups:** Functional ownership (Transaction Intake, Master & Relationship Hub, etc.)
4. **Catalog Columns:** Technical metadata (source systems, column definitions, sample values)
5. **Reports:** Downstream consumers with classification (Class 0=Operational, 1=Management, 2=Executive)

**Correlation Logic:**
- Dataset → Entity → Group → Owner (hierarchy traversal)
- Alert → Reports → Impact assessment (downstream tracing)
- Owner → Open failures → Workload pattern (systemic detection)
- Source system → Multiple alerts → Upstream root cause (cascade prediction)

---

## Demo Protocol

### Standard Pipeline Demo

1. **Screen Mirroring:** Use scrcpy, Samsung Link to Windows, or AirDroid to mirror S25+ to laptop
2. **Data Preparation:** Pre-load `dq_alerts.json` with 1 "completed" analysis for credibility
3. **Open the App:** Tap "Create" tab in the Bottom Navigation Bar
4. **Build the Alert:** Select `postgres` → `beta_hub` → `LINK_ORDER_CUSTOMER` → choose from 5–7 check names. Watch auto-fill populate severity, dimension, and owner email.
5. **Send:** Tap "Send DQ Alert" → Toast confirms delivery
6. **Switch to Analyze:** Tap "Analyze" tab. Watch the **Metro Map visualization** — a train moves through 6 stations (Stage 1→4c) with 5 governance gates between them. Tap any station or gate for a tooltip explaining what that step does. The train pauses and pulses when a gate rejects output.
7. **The Reveal:** Tap any completed station (Stage 4a, 4b, or 4c) to view its report on-demand. The full Executive Stewardship Report, technical briefing, and impact assessment are no longer auto-presented — they open when you choose.

### Governance Configuration Demo

1. **Open Governance:** Tap the **Governance** tab. See the fixed system prompt card at the top (collapsible).
2. **Enable a Gate:** Toggle "Enable Gate" on Gate 1. Enter a custom policy prompt like "Ensure severity is Critical before allowing FULL_ANALYSIS."
3. **Edit Agent Names & Prompts:** Scroll to any stage card. Rename the agent (e.g., change "Upstream Researcher" to "Root Cause Analyst") and modify its system prompt. Tap Save. The new name appears in the Metro Map and Agent Thought Panel.
4. **Configure JSON Sources:** In each stage card, toggle which JSON files the stage should read at runtime — on-device reference files and previous stage outputs are available as filter chips.
5. **Trigger Analysis:** Switch to Create, build an alert, and send it. Switch to Analyze and watch the enabled gate actively review the stage output.

### Human Intervention Demo

1. **Configure Strict Policy:** In Governance, enable Gate 3 with prompt "Always reject output that mentions isolated_incident."
2. **Trigger Rejection:** Create an alert that will produce pattern output containing "isolated_incident."
3. **Watch Auto-Retry:** The gate turns red, then the pipeline retries automatically up to 3 times.
4. **Arbitrate:** After 3 failures, the Human Intervention screen appears. Review the attempt history and tap **Accept Station Version** to override (gate turns orange) or **Accept GaaS Assessment** to abandon.

**Total demo time:** 3 minutes from creation to governance insight.

**Alternative (file drop still works):** Drag `demo_alert.json` to `DQAgent/demo_input/` for scripted presentations.

---

## Build & Deploy

### Prerequisites

- JDK 21+ (GitHub Actions handles this)
- Android SDK 36 (GitHub Actions handles this)
- Gemma 4 E2B model file (`gemma-4-e2b.litertlm`, ~2.5GB)

### Build

```bash
# In GitHub Codespace
./gradlew assembleDebug

# Or download CI artifact from GitHub Actions
```

### Install

```bash
# Via USB (MTP mode)
adb install app-debug.apk
# Or transfer APK to phone Downloads and tap to install
```

### Grant Permissions

App requires `MANAGE_EXTERNAL_STORAGE` (Android 11+) to read:
- LLM model from `/Download/GhostModels/`
- DQ data files from `/Download/GhostModels/DQAgent/data/`
- GaaS config files from `/Download/GhostModels/DQAgent/state/`

---

## UI & Interaction Notes

- **Locked portrait orientation** — prevents accidental screen rotation from resetting pipeline state/presets during analysis.
- **On-demand stage reports** — Stage 4a, 4b, and 4c reports are not auto-presented. Tap a completed station in the Metro Map to open its full report. This keeps the Analyze tab clean until you want the detail.
- **Persistent stage visibility** — after the pipeline completes, all station outputs remain visible and tappable in the Metro Map.
- **Markdown text rendering** — all generated text (agent thoughts, GaaS feedback, tooltips, intervention screen) renders actual bold, italic, and bullet formatting instead of raw markdown syntax.

## Project Status

**Branch:** `gaas-fast` — Lightweight Governance-as-a-Service with auto-retry and human arbitration

**Current:** MVP complete — 6-stage pipeline functional, Fast GaaS governance layer active, demo-ready

**Fast GaaS Features Delivered:**
- ✅ 5 Configurable Gate Gaps (toggle + custom policy prompt per gate)
- ✅ 6 Station-Agent Prompt Editors (editable system prompts for all stages)
- ✅ Auto-Retry Loop (up to 3 attempts with feedback injection for LLM stages)
- ✅ Human Intervention Screen (arbitration UI with attempt history)
- ✅ Metro Map Visualization (6 stations, 5 gates, 6 visual states, animated train)
- ✅ Markdown Text Rendering (bold, italic, bullets throughout UI)
- ✅ Fixed GaaS System Prompt (visible in Governance tab)
- ✅ Editable Agent Names (all 6 stages, flows to Metro Map and Agent Thought Panel)
- ✅ Per-Stage JSON Source Selection (on-device + previous stage outputs via filter chips)
- ✅ On-Demand Report Views (tap Stage 4a/4b/4c stations to open full reports)

**Next:** Integration with live DQ feed (replace file drop with real-time API)

**Stretch:** Azure OpenAI Service fallback for enterprise deployment

---

## License

Internal demonstration project for data operations team.

---

## Acknowledgments

- Google AI Edge (LiteRT-LM, Gemma 4 E2B)
- Kotlinx Serialization
- Jetpack Compose
- GitHub Codespaces (cloud development environment)
