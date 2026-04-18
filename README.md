# DQ Agent — AI-Powered Data Quality Intelligence

> **Autonomous analysis of data quality failures using on-device LLM inference.**  
> Built for financial data operations. Runs entirely on Android with local AI.

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
│   └── dq_knowledge.json      # Severity rules, dimension definitions, KPI thresholds
├── state/                     # Runtime pipeline state (Stage 1→4 JSON files)
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
│   ├── MainActivity.kt             # Entry point: BottomNav + Create/Analyze screens, engine init, file polling
│   ├── CreatorScreen.kt            # 3-tier cascading dropdown alert builder with auto-fill from JSON
│   ├── GhostPaths.kt               # Centralized hard-coded paths to model, data, state, input
│   ├── DQDataClasses.kt            # @Serializable data models: Alert, Entity, Report, Catalog, AnalysisState
│   ├── DQPipelineManager.kt        # Orchestrates Stage 1→4a→4b→4c with 10s thermal cooldowns
│   ├── Stage1Triage.kt             # Severity + downstream report impact → FULL_ANALYSIS or MINIMAL
│   ├── Stage2ContextBuilder.kt     # Entity lookup, column defs, source system → condensed context string
│   ├── Stage3PatternDetector.kt    # Owner workload correlation, group health → pattern detection
│   ├── Stage4UpstreamResearcher.kt # Technical Data Architect: root cause hypothesis + investigation path
│   ├── Stage4DownstreamResearcher.kt# Business Impact Analyst: cascade + stakeholder notification priority
│   ├── Stage4Synthesizer.kt        # Senior Data Steward: executive narrative for CDO briefing
│   ├── EntityData.kt               # Legacy entity info data class (pre-DQ pipeline)
│   └── BugLogger.kt                # File-based timestamped logger (logs/bug_log.txt)
├── build.gradle                    # Root project plugins (Android, Kotlin, Compose, Serialization)
├── settings.gradle                 # Project name + repository config (Google, Maven Central)
├── gradle.properties               # AndroidX, compile SDK override, JVM heap settings
├── demo_alert.json                 # Golden demo alert (LINK_ORDER_CUSTOMER, Critical, Adaptability)
└── README.md                       # This file
```

### What Each Component Does

| File | Responsibility |
|------|----------------|
| `MainActivity.kt` | Entry point with BottomNavigationBar (Create + Analyze tabs). Initializes LiteRT-LM engine and starts 5-second file poll loop on `demo_input/new_alert.json`. |
| `CreatorScreen.kt` | 3-tier cascading dropdown form (Type → Source → Dataset). Auto-fills check name, severity, dimension, and owner email from JSON lookups. Writes alert JSON to `demo_input/new_alert.json`. |
| `GhostPaths.kt` | Single source of truth for all absolute file paths on device. Validates model availability (`>1GB`) and DQ data directory presence. |
| `DQDataClasses.kt` | Kotlinx Serialization data classes consumed by all stages. `AnalysisState` is the accumulator passed from Stage 1→4c. |
| `DQPipelineManager.kt` | CoroutineScope-driven orchestrator. Handles stage progression through 1→2→3→4a→4b→4c with 10s `delay()` cooldowns. Early-exit for `MINIMAL` alerts. |
| `Stage1Triage.kt` | Loads `reports.json`, checks if the alert dataset feeds any executive (`Class 2`) reports. Decides `FULL_ANALYSIS` vs `MINIMAL`. Writes `stage1.json`. |
| `Stage2ContextBuilder.kt` | Loads `entities.json`, `catalog_columns.json`, `entity_groups.json`. Builds a condensed business context string (<1,200 tokens). Writes `stage2.json`. |
| `Stage3PatternDetector.kt` | Loads `dq_alerts.json` to count owner failures and compute entity group health score by functional group (not datasource). Detects `owner_overload`, `group_collapse`, or `isolated_incident`. Writes `stage3.json`. |
| `Stage4UpstreamResearcher.kt` | Technical Data Architect sub-agent. Analyzes source system architecture, builds root cause hypothesis with confidence, and defines investigation path. Writes `stage4a.json`. |
| `Stage4DownstreamResearcher.kt` | Business Impact Analyst sub-agent. Assesses cascade chains, stakeholder notification priority by report class, and time sensitivity. Writes `stage4b.json`. |
| `Stage4Synthesizer.kt` | Senior Data Steward sub-agent. Synthesizes upstream + downstream research into a 350-400 word executive narrative for CDO briefing. Writes `stage4c.json`. |
| `BugLogger.kt` | Thread-safe file logger. Logs every stage transition, file I/O error, and LLM exception to app-private storage. Accessible via "Logs" button in UI. |

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

**Setup for stakeholder presentation:**

1. **Screen Mirroring:** Use scrcpy, Samsung Link to Windows, or AirDroid to mirror S25+ to laptop
2. **Data Preparation:** Pre-load `dq_alerts.json` with 1 "completed" analysis for credibility
3. **Open the App:** Tap "Create" tab in the Bottom Navigation Bar
4. **Build the Alert:** Select `postgres` → `beta_hub` → `LINK_ORDER_CUSTOMER` → choose from 5–7 check names. Watch auto-fill populate severity, dimension, and owner email for that specific check.
5. **Send:** Tap "Send DQ Alert" → Toast confirms delivery
6. **Switch to Analyze:** Tap "Analyze" tab. Watch 6-stage progression (Triage → Context → Pattern → Upstream → Downstream → Synthesis)
7. **The Reveal:** Executive Stewardship Report appears with technical briefing, impact assessment, and actionable recommendations

**Total demo time:** 2 minutes from creation to insight.

**Alternative (file drop still works):** Drag `demo_alert.json` to `DQAgent/demo_input/` for scripted presentations.

---

## Build & Deploy

### Prerequisites

- JDK 21 (GitHub Actions handles this)
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

---

## Project Status

**Current:** MVP complete — 4-stage pipeline functional, demo-ready

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
