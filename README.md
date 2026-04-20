# DQ Agent — AI-Powered Data Quality Intelligence

> **Autonomous analysis of data quality failures using on-device LLM inference.**  
> Built for financial data operations. Runs entirely on Android with local AI.

---

## 🆕 GaaS Branch: Governance-as-a-Service

This is the **`gaas-enhancement`** branch. It transforms DQ Agent from a reactive analysis tool into an **AI ecosystem that is itself governed**.

### What's Different from Main?

| | **Main Branch** | **GaaS Branch** |
|---|---|---|
| **Question answered** | "What broke?" | "Is our AI workforce making good decisions, and are they following bank policies?" |
| **User role** | Alert consumer | AI workforce manager |
| **Key feature** | 4-stage pipeline | Trust scores + policy engine + agent negotiation + audit forensics + Metro Map visualization |
| **APK name** | DQ Agent | **DQ Agent - GaaS** |
| **Installs alongside?** | — | ✅ Yes (`.gaas` applicationId suffix) |

### The "Wow Factor": Who Watches the AI Watchers?

The GaaS layer sits between the Stage 4 analysis agents and the final output, adding a meta-layer of AI governance:

- **Trust Score Management** — Each AI agent (Stage 1-4) has a persistent Trust Score (0-100). Scores evolve based on decision accuracy. Autonomy levels auto-adjust from "Training Wheels" to "Full Delegation."
- **Policy Engine with Dynamic Creation** — 5 pre-loaded bank policies (PII Prevention, Executive Contact Approval, Critical Alert Oversight, Audit Trail Completeness, Schema Drift Validation). The Data Quality Coordinator can create new policies in-app without restarting. Policies can be assigned to Metro Map gates so tooltips stay in sync automatically.
- **Agent Negotiation Mediator** — When Stage 4a (Technical) and 4b (Business) agents disagree, a structured "debate" interface activates with mediation proposals.
- **Escalation Router** — Four-level smart escalation: Auto-Resolve → Notify & Log → Request Approval → Emergency Human-in-Loop.
- **Audit & Forensics** — Immutable append-only decision trail with SHA-256 integrity. Playback any decision path. Export for regulatory compliance.

### Governance Cockpit (New Tab)

A third **"Governance"** tab joins Create and Analyze in the bottom navigation:

- **Trust Dashboard** — Card-based agent overview with Trust Score progress bars, trend arrows (↗ → ↘), and autonomy badges
- **Policy Center** — Toggle policies on/off, create new policies with visual condition builder
- **Decision Queue** — Swipeable approval cards with priority badges. Batch approve low-risk items.
- **Audit Playback** — Timeline visualization, search/filter by date/agent/policy, compliance export

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

## The Solution: 4-Stage Reasoning Pipeline

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
│   ├── dq_knowledge.json      # Severity rules, dimension definitions, KPI thresholds
│   ├── trust_scores.json      # 🆕 Agent trust profiles and score history
│   └── policy_rules.json      # 🆕 Active governance policies with conditions
├── state/                     # Runtime pipeline state (Stage 1→4 JSON files)
│   ├── gaas_state.json        # 🆕 Runtime governance state
│   └── audit_log.jsonl        # 🆕 Append-only immutable decision trail
└── demo_input/              # File drop trigger for new alerts
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
│   ├── CreatorScreen.kt            # 3-tier cascading dropdown alert builder with auto-fill from JSON
│   ├── GhostPaths.kt               # Centralized hard-coded paths to model, data, state, input
│   ├── DQDataClasses.kt            # @Serializable data models: Alert, Entity, Report, Catalog, AnalysisState
│   ├── DQPipelineManager.kt        # Orchestrates Stage 1→4a→4b→4c with 10s thermal cooldowns + GaaS hooks
│   ├── Stage1Triage.kt             # Severity + downstream report impact → FULL_ANALYSIS or MINIMAL
│   ├── Stage2ContextBuilder.kt     # Entity lookup, column defs, source system → condensed context string
│   ├── Stage3PatternDetector.kt    # Owner workload correlation, group health → pattern detection
│   ├── Stage4UpstreamResearcher.kt # Technical Data Architect: root cause hypothesis + investigation path
│   ├── Stage4DownstreamResearcher.kt# Business Impact Analyst: cascade + stakeholder notification priority
│   ├── Stage4Synthesizer.kt        # Senior Data Steward: executive narrative for CDO briefing
│   ├── BugLogger.kt                # File-based timestamped logger (logs/bug_log.txt)
│   ├── GaaSDataClasses.kt          # 🆕 Governance data models: TrustScore, PolicyRule, AuditRecord, etc.
│   ├── GaaSController.kt           # 🆕 Central governance orchestrator with pre/post stage hooks
│   ├── TrustScoreManager.kt        # 🆕 Persistent trust scores, autonomy levels, manual overrides
│   ├── PolicyEngine.kt             # 🆕 5 default policies + dynamic creation/evaluation + gate rendering
│   ├── AgentNegotiator.kt          # 🆕 Conflict detection, structured debate, mediation proposals
│   ├── EscalationRouter.kt         # 🆕 4-level smart escalation with priority matrix
│   ├── AuditLogger.kt              # 🆕 Immutable append-only audit trail with SHA-256 hashing
│   ├── GovernanceScreen.kt         # 🆕 Navigation host for Governance tab
│   ├── GovernanceDashboard.kt      # 🆕 Trust dashboard with agent cards and stats overview
│   ├── AgentDetail.kt              # 🆕 Deep-dive into single agent performance history
│   ├── PolicyEditor.kt             # 🆕 Visual policy creation and management (includes gate label assignment)
│   ├── DecisionQueue.kt            # 🆕 Pending approvals interface with priority badges
│   ├── AuditPlayback.kt            # 🆕 Timeline replay and compliance export
│   ├── MetroMap.kt                 # 🆕 Interactive pipeline visualization: stations, gates, train, pan/zoom, tooltips
│   ├── MetroStation.kt             # 🆕 Stage station composable with trust score pill
│   ├── MetroGate.kt                # 🆕 Governance gate composable (shield diamond)
│   ├── MetroTrain.kt               # 🆕 Moving alert card showing current analysis progress
│   ├── MetroMapViewModel.kt        # 🆕 Animation state for train position and policy results
│   ├── AgentThoughtPanel.kt        # 🆕 Expandable reasoning panel for active agent
│   ├── PolicyEvaluationPanel.kt    # 🆕 Live policy violation display
│   └── TrustThermometer.kt         # 🆕 Visual trust score indicator
├── build.gradle                    # Root project plugins (Android, Kotlin, Compose, Serialization)
├── settings.gradle                 # Project name + repository config (Google, Maven Central)
├── gradle.properties               # AndroidX, compile SDK override, JVM heap settings
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 9.1.0 (Java 25 compatible)
├── demo_alert.json                 # Golden demo alert (LINK_ORDER_CUSTOMER, Critical, Adaptability)
└── README.md                       # This file
```

### What Each Component Does

| File | Responsibility |
|------|----------------|
| `MainActivity.kt` | Entry point with BottomNavigationBar (Create + Analyze + **Governance** tabs). Initializes LiteRT-LM engine, GaaS layer, and starts 5-second file poll loop. |
| `CreatorScreen.kt` | 3-tier cascading dropdown form (Type → Source → Dataset). Auto-fills check name, severity, dimension, and owner email from JSON lookups. Writes alert JSON to `demo_input/new_alert.json`. |
| `GhostPaths.kt` | Single source of truth for all absolute file paths on device. Validates model availability (`>1GB`) and DQ data directory presence. Includes paths for GaaS governance files. |
| `DQDataClasses.kt` | Kotlinx Serialization data classes consumed by all stages. `AnalysisState` is the accumulator passed from Stage 1→4c. |
| `DQPipelineManager.kt` | CoroutineScope-driven orchestrator. Handles stage progression through 1→2→3→4a→4b→4c with 10s `delay()` cooldowns. Early-exit for `MINIMAL` alerts. **GaaS hooks** wrap each stage boundary for policy validation. |
| `Stage1Triage.kt` | Loads `reports.json`, checks if the alert dataset feeds any executive (`Class 2`) reports. Decides `FULL_ANALYSIS` vs `MINIMAL`. Writes `stage1.json`. |
| `Stage2ContextBuilder.kt` | Loads `entities.json`, `catalog_columns.json`, `entity_groups.json`. Builds a condensed business context string (<1,200 tokens). Writes `stage2.json`. |
| `Stage3PatternDetector.kt` | Loads `dq_alerts.json` to count owner failures and compute entity group health score by functional group (not datasource). Detects `owner_overload`, `group_collapse`, or `isolated_incident`. Writes `stage3.json`. |
| `Stage4UpstreamResearcher.kt` | Technical Data Architect sub-agent. Analyzes source system architecture, builds root cause hypothesis with confidence, and defines investigation path. Writes `stage4a.json`. |
| `Stage4DownstreamResearcher.kt` | Business Impact Analyst sub-agent. Assesses cascade chains, stakeholder notification priority by report class, and time sensitivity. Writes `stage4b.json`. |
| `Stage4Synthesizer.kt` | Senior Data Steward sub-agent. Synthesizes upstream + downstream research into a 350-400 word executive narrative for CDO briefing. Writes `stage4c.json`. |
| `BugLogger.kt` | Thread-safe file logger. Logs every stage transition, file I/O error, and LLM exception to app-private storage. Accessible via "Logs" button in UI. |
| `GaaSController.kt` | Central governance orchestrator. Loads policies, coordinates interceptions at stage boundaries, manages pipeline blocking/resume, and handles violation modals. |
| `TrustScoreManager.kt` | Persists agent trust scores to `trust_scores.json`. Tracks decision accuracy, score history, and autonomy levels. Supports manual overrides with audit logging. |
| `PolicyEngine.kt` | Evaluates agent outputs against active policies at runtime. Includes 5 default bank policies + dynamic creation with condition builders. Gates are rendered dynamically from policies with `gateOrder >= 0`. |
| `AgentNegotiator.kt` | Detects conflicts between Stage 4a and 4b outputs. Activates structured negotiation with visual debate interface and hybrid mediation proposals. |
| `EscalationRouter.kt` | Routes decisions to appropriate channels based on Trust Score + Policy Requirements + Alert Severity. Maintains pending approval queue with priority badges. |
| `AuditLogger.kt` | Writes immutable append-only decision records to `audit_log.jsonl`. Includes cryptographic integrity (SHA-256), decision path playback, and compliance export. |

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
6. **Switch to Analyze:** Tap "Analyze" tab. Watch the **Metro Map visualization** — a train moves through 6 stations (Stage 1→4c) with 5 governance gates (TRUST, PII, EXEC, AUDIT, SCHEMA) between them. Tap any station or gate for a tooltip explaining what that step does. The train pauses and pulses when a policy violation blocks the pipeline.
7. **The Reveal:** Executive Stewardship Report appears with technical briefing, impact assessment, and actionable recommendations

### Governance Cockpit Demo (GaaS Branch Exclusive)

1. **Trust Dashboard:** Tap **Governance** tab. See all 7 agents with Trust Score rings, trend arrows, and autonomy badges (Training → Supervised → Autonomous → Expert).
2. **Policy Violation:** Trigger a Critical alert. Watch the Analyze tab show a 🔴 Blocked chip and a **Policy Violation modal** explaining why. Tap "Approve & Continue" to override.
3. **Agent Negotiation:** Create an alert that triggers conflicting 4a/4b recommendations. See the **⚖ Agent Negotiation Active** card with each agent's position, confidence, and evidence. Tap "Accept Mediation" or examine the hybrid proposal.
4. **Decision Queue:** If escalation fires, open Governance → tap **Queue**. See swipeable approval cards with priority badges. Approve or reject with notes.
5. **Audit Playback:** In Governance, tap **Audit**. Search by agent, policy, or date. Tap **Export** to generate compliance documentation.

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

The generated APK is named **DQ Agent - GaaS** (via `versionName`) and uses applicationId suffix `.gaas` so it installs alongside the main branch APK without conflict.

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
- GaaS governance files from `/Download/GhostModels/DQAgent/data/`

---

## UI & Interaction Notes

- **Locked portrait orientation** — prevents accidental screen rotation from resetting pipeline state/presets during analysis.
- **Expandable stage outputs** — Stage 4a, 4b, and 4c cards show truncated previews by default to save vertical space. Tap **Expand** to view the full report inline; tap **Collapse** to return to the preview.
- **Persistent stage visibility** — after the pipeline completes, all stage outputs remain visible in the scrollable feed.
- **Governance status chips** — small colored indicators on each stage card showing GaaS approval state (✓ ⏳ ✕ ⚠ ✎).
- **Violation modals** — when a policy is violated, a modal appears with the policy name, triggered content, remediation action, and Approve/Block buttons.

## Project Status

**Branch:** `gaas-enhancement` — Governance-as-a-Service multi-agent framework

**Current:** MVP complete — 4-stage pipeline functional, GaaS governance layer active, demo-ready

**GaaS Features Delivered:**
- ✅ Trust Score Management (7 agents, persistent scores, autonomy levels)
- ✅ Policy Engine (5 default + dynamic creation, runtime evaluation)
- ✅ Agent Negotiation Mediator (4a/4b conflict detection + mediation)
- ✅ Escalation Router (4 levels, smart prioritization)
- ✅ Audit & Forensics (immutable log, playback, compliance export)
- ✅ Governance Cockpit (new tab with Trust Dashboard, Policy Center, Decision Queue, Audit Playback)
- ✅ Metro Map Visualization (interactive pipeline map with stations, gates, train, pan/zoom, tooltips)
- ✅ Dynamic Gate-to-Policy Coupling (gate tooltips read live from `PolicyEngine` descriptions)

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
