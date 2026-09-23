# IMIQ Mobility App — Adaptive HOTCO-CT UC5

Android client for the IMIQ mobility assistant in Magdeburg. The current UC5 architecture combines an **Adaptive Cognitive Passport**, **HOTCO-CT v4.3**, an external route-ranking service, route geometry, live contextual evidence, deterministic XAI, and optional evidence-bounded Digital Companion narration.

This README describes the **current implementation in the adaptive HOTCO-CT integration branch**, not the older 44-question onboarding pipeline.

> **Scientific boundary:** HOTCO-CT models a person's mobility deliberation. It does **not** generate routes and it does **not** own the routing rank. Candidate generation and ordering remain the responsibility of the external routing service.

---

## Current implementation status

| Component | Current state |
|---|---|
| Adaptive Cognitive Passport onboarding | Implemented |
| 11 mobility needs + 4 action valences | Implemented |
| Explicit availability for car/bike/PT/walk | Implemented |
| Four fixed TRAIN-derived belief calibration questions | Implemented |
| Estimation of the remaining belief cells with explicit provenance | Implemented |
| HOTCO-CT v4.3 initial bootstrap | Implemented |
| Five environmental tolerance ratings | Implemented |
| Cognitive Passport persistence on device | Implemented |
| Longitudinal XAI-driven micro-questions | Implemented |
| Explicit-confirmation profile revisions | Implemented |
| External route ranking | Implemented in UI, but adaptive-passport compatibility still needs one adapter fix |
| GraphHopper geometry reconstruction | Implemented after a routing result exists |
| Route-conditioned contextual HOTCO | Implemented in backend, but adaptive-passport reconstruction still needs compatibility work |
| Deterministic contextual XAI | Implemented |
| Optional server-side Digital Companion narration | Implemented |
| Production/local Android flavors | Implemented |
| Production-debug CI build and unit tests | Implemented |

### Important current integration note

The adaptive onboarding and HOTCO bootstrap are functional, but the current frontend `PassportAdapter.toEnginePassport()` still contains the legacy strict guard that requires **44 observed belief ratings**, `imputation_used == false`, and no population-trained estimator. An Adaptive Cognitive Passport truthfully contains initially **4 observed + 40 estimated** belief cells, so this guard currently prevents the adaptive passport from reaching route ranking.

There is a second downstream compatibility point in contextual deliberation: the current backend route-context path reconstructs the strict questionnaire from normalized Cognitive Passport values. Estimator-derived adaptive belief values are not guaranteed to map exactly back to the original integer 1–7 response scale, so contextual deliberation must use an adaptive-aware reconstruction path before the adaptive route-context pipeline is considered end-to-end complete.

These are integration issues, not HOTCO solver changes. The intended ownership boundaries described below remain unchanged.

---

# 1. Architecture at a glance

```text
                         ┌───────────────────────────────┐
                         │          Android app          │
                         └──────────────┬────────────────┘
                                        │
                          production: access-code login
                          local*: direct profile setup
                                        │
                                        ▼
                    ┌──────────────────────────────────┐
                    │ Adaptive Cognitive Passport setup│
                    │                                  │
                    │ 11 needs                         │
                    │ 4 availability declarations      │
                    │ 4 mode valences                  │
                    │ 4 fixed belief questions         │
                    │ 5 context-tolerance ratings      │
                    └────────────────┬─────────────────┘
                                     │
                                     ▼
                  /adaptive-passport/start + complete
                                     │
                                     ▼
                  statistical completion of 4×11 beliefs
                    observed and estimated kept separate
                                     │
                                     ▼
                  /adaptive-passport/bootstrap
                                     │
                                     ▼
                         HOTCO-CT v4.3 baseline
                                     │
                                     ▼
                         Cognitive Passport v2
                    + adaptive provenance + lineage
                    + deterministic XAI diagnostics
                                     │
                                     ▼
                           persisted on device
                                     │
                    ┌────────────────┴───────────────┐
                    │                                │
                    ▼                                ▼
           profile refinement                 trip planning
           micro-question                          │
                    │                              ▼
                    │                     origin + destination
                    │                              │
                    │                              ▼
                    │                    external routing engine
                    │                     owns candidate ranking
                    │                              │
                    │                              ▼
                    │                 GraphHopper geometry reconstruction
                    │                   car / bike / walk when available
                    │                              │
                    │                              ▼
                    │                    DYCONET contextual layer
                    │                 baseline + per-route HOTCO run
                    │                              │
                    │                              ▼
                    │                    deterministic contextual XAI
                    │                              │
                    │                              ▼
                    │                  optional Digital Companion voice
                    │                    from minimized validated evidence
                    │                              │
                    └──────────────────────────────┤
                                                   ▼
                                      route cards / map / directions
```

`localEmulator` and `localUsb` make the Adaptive Passport → HOTCO core local. Routing, geocoding, GraphHopper and the other institutional services remain separate external dependencies.

---

# 2. App entry and authentication

The app starts with a splash screen and then chooses the initial screen from local state:

- if a Cognitive Passport already exists, open the home screen;
- in `localEmulator` or `localUsb`, open profile setup directly;
- in `production`, a logged-in user without a completed profile enters setup;
- otherwise, production opens the access-code login screen.

Production login sends the access code to:

```text
POST https://imiq-public.et.uni-magdeburg.de/api/dayplanner/login
```

The local build flavors deliberately bypass that remote login when no passport exists. This is a build-time development behavior and is not enabled in the production flavor.

---

# 3. Adaptive Cognitive Passport onboarding

The current onboarding no longer asks the participant to rate all 44 need–mode belief cells.

After the welcome / participant-ID screen, the five data steps are:

1. **11 mobility needs**, each explicitly rated from 1 to 7.
2. **Structural availability** for all four canonical modes: `car`, `bike`, `pt`, and `walk`.
3. **Four mode valences**, shown in the Android UI on a 1–7 scale and converted to the HOTCO raw valence scale −3…+3 before submission.
4. **Four fixed belief calibration questions**, returned by the backend.
5. **Five environmental tolerance ratings**: `rain`, `crowding`, `darkness`, `traffic`, and `temperature`.

At least one transport mode must be declared available.

The adaptive onboarding does **not** currently use the legacy top-three need ranking or mode-use frequency as HOTCO inputs.

## 3.1 Initial calibration questions

The initial four belief questions are **fixed TRAIN-derived calibration items**. They are not selected adaptively during the initial onboarding.

The backend returns exactly four question targets. The Android client then requires an explicit 1–7 response for each returned cell.

## 3.2 Belief completion and provenance

The canonical HOTCO topology still contains:

```text
4 modes × 11 needs = 44 need–mode belief cells
```

Initially:

```text
4  = directly observed belief ratings
40 = estimator-derived belief values
```

The estimator uses the frozen Adaptive Cognitive Passport v1 artifact. The backend keeps measurement provenance explicit:

- observed beliefs remain observed;
- estimated beliefs remain estimated;
- estimated beliefs may enter the normalized HOTCO belief matrix;
- estimated values are **not** fabricated into raw 1–7 questionnaire answers.

The adaptive serializer reports the use of a population-trained estimator truthfully. This is different from the strict legacy 44-observed-input contract.

---

# 4. Availability is separate from preference

Availability is a distinct structural input. The current phase uses only the participant's explicit declarations for `car`, `bike`, `pt`, and `walk`.

Availability is **not inferred** from mode-use frequency, HOTCO activation, valence, preference, weather, routing output, or population values.

The availability vector gates the corresponding HOTCO actions.

The architecture intentionally leaves a provider boundary for a future version in which new mobility services or external accessibility information could be introduced without changing the core HOTCO solver. This repository does not modify the external routing service to achieve that.

---

# 5. Initial HOTCO-CT bootstrap

Once the four belief questions are answered, the Android app performs:

```text
POST /api/dyconet/adaptive-passport/complete
POST /api/dyconet/adaptive-passport/bootstrap
```

The bootstrap payload contains the completed adaptive profile, explicit four-mode availability, and environmental tolerances.

The backend then builds the full normalized HOTCO input and runs HOTCO-CT v4.3.

The resulting Cognitive Passport contains normalized needs, the full normalized 4×11 belief matrix, valences, explicit availability, environmental tolerances, HOTCO terminal state and comparative readout, deterministic XAI diagnostics, measurement provenance, adaptive-passport metadata, and passport lineage/revision metadata.

The initial adaptive passport is revision 1.

---

# 6. Cognitive Passport persistence

The current Cognitive Passport is persisted on the Android device through `PassportStore`.

This does **not** mean that the passport never leaves the phone. When required for a live operation, the app transmits the relevant passport representation to the institutional services used for route ranking or contextual deliberation.

The adaptive contract distinguishes observed measurements, model-estimated beliefs, explicit availability, environmental tolerances, and lineage/revision information.

Client-carried passport validation is currently based on schema, provenance, and internal consistency. It should not be described as cryptographically tamper-proof authentication.

---

# 7. Longitudinal profile refinement

The Adaptive Cognitive Passport can evolve after onboarding.

The backend may expose one profile-scoped micro-question candidate derived from deterministic XAI/profile diagnostics. The Android app never invents the target.

A profile revision requires the active backend-generated question, a new explicit 1–7 user response, and `explicit_user_confirmation = true`.

For the adaptive contract, the request is sent to:

```text
POST /api/dyconet/adaptive-passport/profile-update
```

The previous passport is retained as a separate lineage snapshot and a new passport revision is generated. An estimated belief may therefore become directly observed over time.

The current trigger logic is relevance/provenance based. It should **not** be described as a calibrated statistical uncertainty estimator.

---

# 8. Origin and destination

The route screen uses a manually selected origin if one exists; otherwise it uses the device location.

Destination and optional manual-origin search use the institutional geocoder:

```text
https://imiq-public.et.uni-magdeburg.de/api/geocode/
```

If the primary provider fails or produces no usable result, the Android client may use Photon as a fallback:

```text
https://photon.komoot.io/api/
```

Photon is not queried in parallel with every request. The fallback receives the search query, not the Cognitive Passport or HOTCO state.

The app explicitly checks whether origin and destination are inside the current routing coverage area. A place that exists but lies outside supported coverage is treated differently from a geocoding failure.

---

# 9. Route ranking: external service owns order

Route ranking belongs to the separate routing service:

```text
POST https://imiq-app.et.uni-magdeburg.de/api/routing/ranked-routes
```

The intended request contains a routing-oriented view of the Cognitive Passport, start coordinates, destination coordinates, and reported mode availability.

The routing service returns the ordered candidate list, including route rank, mode, availability, summary and value-fit information.

## Non-negotiable ownership rule

The Android client preserves routing order.

HOTCO-CT and the Digital Companion must not claim that a HOTCO-preferred mode is automatically the fastest route, shortest route, optimal route, or routing rank 1.

A selected route and a HOTCO contextual tendency are separate concepts.

## Current adaptive integration blocker

The present `PassportAdapter.toEnginePassport()` still validates the legacy strict-passport provenance:

```text
44 observed beliefs
imputation_used == false
population_or_synthetic_values_used == false
```

That contract is incompatible with the truthful Adaptive Cognitive Passport provenance:

```text
4 observed beliefs
40 estimated beliefs initially
belief estimation used
population-trained estimator used
```

This adapter must be made adaptive-aware before the new onboarding can reach the routing service end to end.

---

# 10. GraphHopper geometry

The route-ranking service does not provide map geometry. After ranking, the Android client reconstructs geometry through GraphHopper:

```text
GET https://imiq-app.et.uni-magdeburg.de/api/graphhopper/route
```

Supported GraphHopper mappings are:

| Routing mode | GraphHopper profile |
|---|---|
| `car` | `car` |
| `bike` | `bike` |
| `walk` / `foot` | `foot` |
| public transport / unsupported composite modes | no GraphHopper reconstruction |

GraphHopper provides polyline geometry, reconstructed distance/time, and turn-by-turn instructions where available.

It does **not** replace the routing service's ranking or summary. The UI keeps the external routing rank authoritative and treats GraphHopper as supplemental geometry/navigation evidence.

Geometry failures do not silently replace route-ranking results with demo data.

---

# 11. Route-conditioned contextual deliberation

After routing and geometry are available, Android can request:

```text
POST /api/dyconet/contextual-deliberation
```

The request includes the stored Cognitive Passport, environmental tolerance profile, ordered routing candidates, route/segment geometry where available, and a contextual query configuration.

The backend runs one baseline HOTCO-CT simulation, contextual enrichment for each candidate route, one route-conditioned HOTCO-CT simulation per candidate, and a difference-from-baseline calculation. Candidate order is preserved.

## 11.1 Current live context request

The Android contract currently requests **weather** from the Orion context provider. The backend has broader provider infrastructure, but additional families should not be described as active Android inputs until their route semantics and UI handling are deliberately enabled.

Missing or unusable context remains `UNKNOWN`, `EMPTY`, `UNAVAILABLE`, or otherwise explicitly represented. It is not silently replaced with neutral context.

## 11.2 Current contextual forcing model

The default contextual model is:

```text
F3_NEED_ONLY_V1
```

with default:

```text
rho = 0.25
```

F3 converts eligible contextual stressors and user tolerances into **need-only forcing**. Action and valence contextual forcing are zero in this model.

The backend marks this contextual model:

```text
EXPERIMENTAL_UNCALIBRATED
```

It should therefore be interpreted as an experimental contextual extension, not a calibrated behavioral effect model.

## Current adaptive compatibility blocker

The contextual-deliberation backend currently reconstructs a strict questionnaire from Cognitive Passport values before running the baseline and route-conditioned simulations.

That legacy reconstruction assumes every normalized belief can be mapped exactly back to an integer 1–7 raw response. Adaptive estimator-derived belief values do not have such fabricated raw answers and are not guaranteed to sit on that discrete grid.

The contextual path therefore needs an adaptive-aware participant reconstruction route before Adaptive Passport → Contextual HOTCO is fully end-to-end compatible.

---

# 12. Deterministic contextual XAI

For a selected route with a contextual result, Android requests:

```text
POST /api/dyconet/contextual-explanation
```

This endpoint does **not** rerun HOTCO. It explains the existing deterministic contextual result.

The explanation contract contains route-bound information such as baseline tendency, contextual tendency, whether the leader changed, ambiguity state, contextual drivers, supporting and opposing constraints, data-quality information, warnings, minimized evidence, and an evidence hash/version.

The explanation is tied to a `route_id` and to the deterministic evidence version. A stale explanation is not reused after route or context changes.

---

# 13. Digital Companion narration

Natural-language narration is optional and server-side:

```text
POST /api/dyconet/contextual-explanation/narrate
```

The LLM does **not** receive the raw full app state as decision authority. Narration is generated from minimized deterministic evidence.

The server controls provider configuration through environment variables:

```text
IMIQ_XAI_LLM_PROVIDER
IMIQ_XAI_LLM_API_KEY
IMIQ_XAI_LLM_BASE_URL
IMIQ_XAI_LLM_MODEL
```

The current backend supports an AcademicCloud-compatible provider and a mock development provider. The Android app should not hard-code a specific LLM model name as part of the scientific architecture.

## Narration safety boundary

Recommendation authority remains deterministic. The language model supplies voice, not a new decision.

Narration is validated against route identity, evidence version, supported modes, supported stressors, deterministic recommendation/ambiguity state, numeric evidence, and context freshness/fidelity.

Provider failure or invalid generated content must not create a new mobility recommendation. The deterministic explanation/fallback remains the authoritative source.

---

# 14. Result-screen behavior

The route-results UI is progressive:

```text
routing
  ↓
routing rank available
  ↓
geometry reconstruction
  ↓
contextual analysis
  ↓
deterministic XAI
  ↓
optional narration
```

The app does not need to hide already valid routing results while an optional downstream layer is still loading or unavailable.

The route cards preserve external routing order. Selecting a route controls map presentation, route detail, contextual explanation, narration, and navigation action. Selection does not rerank candidates.

---

# 15. Navigation

For supported routable modes, GraphHopper instructions can be shown in-app.

Public transport or candidates without usable internal instructions may hand off to an external navigation application if a compatible handler is available.

The UI disables navigation when neither internal instructions nor an external handler are available.

---

# 16. Build variants

The Android project defines one `environment` flavor dimension:

| Variant | DYCONET base URL | Local core mode | Intended use |
|---|---|---:|---|
| `localEmulatorDebug` | `http://10.0.2.2:8077` | `true` | Android emulator |
| `localUsbDebug` | `http://127.0.0.1:8077` | `true` | Physical device with `adb reverse` |
| `productionDebug` | `https://imiq-app.et.uni-magdeburg.de` | `false` | Institutional test/deployment build |
| `productionRelease` | `https://imiq-app.et.uni-magdeburg.de` | `false` | Release variant |

Important: the flavor switches the **DYCONET** endpoint. The current routing, GraphHopper, geocoding and other external-service clients retain their own service URLs.

---

# 17. Local development

## Requirements

- JDK 17
- Android SDK / platform compatible with `compileSdk = 34`
- Android SDK path available through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or a local non-versioned `local.properties`

Example PowerShell session:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

Do not commit `local.properties`.

A template is provided:

```text
local.properties.example
```

## Local DYCONET backend

Run the current DYCONET backend on port `8077`.

### Emulator

```powershell
.\gradlew.bat :app:testLocalEmulatorDebugUnitTest :app:assembleLocalEmulatorDebug
```

APK:

```text
app/build/outputs/apk/localEmulator/debug/app-localEmulator-debug.apk
```

### Physical USB device

Expose the local backend to the phone:

```powershell
adb reverse tcp:8077 tcp:8077
```

Build:

```powershell
.\gradlew.bat :app:testLocalUsbDebugUnitTest :app:assembleLocalUsbDebug
```

APK:

```text
app/build/outputs/apk/localUsb/debug/app-localUsb-debug.apk
```

The helper script `BUILD_LOCAL_APKS_WINDOWS.bat` can build the local variants after `local.properties` is configured.

---

# 18. Production-debug validation

The integration branch has been validated locally with:

```powershell
.\gradlew.bat :app:testProductionDebugUnitTest :app:assembleProductionDebug --stacktrace
```

Expected APK:

```text
app/build/outputs/apk/production/debug/app-production-debug.apk
```

A successful Gradle build confirms that the Android project compiles and that the unit-test suite passes. It does **not** by itself prove that every remote service contract is end-to-end compatible; the adaptive routing/context compatibility items documented above still require integration fixes/tests.

---

# 19. GitHub Actions

`.github/workflows/android-ci.yml` runs for pushes and pull requests targeting `master` or `main`.

The workflow:

1. checks out the repository;
2. installs JDK 17;
3. validates the Gradle wrapper;
4. runs `./gradlew :app:testProductionDebugUnitTest --stacktrace`;
5. runs `./gradlew :app:assembleProductionDebug --stacktrace`;
6. uploads `app/build/outputs/apk/production/debug/*.apk`.

Unit tests are not configured with `continue-on-error`; a failing test fails CI.

---

# 20. Service map

| Service | Endpoint / location | Responsibility |
|---|---|---|
| Login | `imiq-public.et.uni-magdeburg.de/api/dayplanner/login` | access code → token |
| Adaptive start | `DYCONET_BASE_URL/api/dyconet/adaptive-passport/start` | validate needs/valences and return 4 fixed calibration questions |
| Adaptive complete | `DYCONET_BASE_URL/api/dyconet/adaptive-passport/complete` | complete 4×11 belief profile with provenance |
| Adaptive bootstrap | `DYCONET_BASE_URL/api/dyconet/adaptive-passport/bootstrap` | run initial HOTCO-CT and create Cognitive Passport |
| Adaptive profile update | `DYCONET_BASE_URL/api/dyconet/adaptive-passport/profile-update` | explicit longitudinal passport revision |
| Routing | `imiq-app.et.uni-magdeburg.de/api/routing/ranked-routes` | candidate generation/ranking |
| GraphHopper | `imiq-app.et.uni-magdeburg.de/api/graphhopper/route` | supplemental geometry/instructions |
| Primary geocoder | `imiq-public.et.uni-magdeburg.de/api/geocode/` | place query → coordinates |
| Photon | `photon.komoot.io/api/` | fallback geocoding only |
| Contextual deliberation | `DYCONET_BASE_URL/api/dyconet/contextual-deliberation` | baseline + route-conditioned HOTCO |
| Contextual XAI | `DYCONET_BASE_URL/api/dyconet/contextual-explanation` | deterministic explanation |
| Digital Companion narration | `DYCONET_BASE_URL/api/dyconet/contextual-explanation/narrate` | optional bounded narration |
| Orion | queried server-side by DYCONET | structured live context |
| LLM provider | configured server-side | narration only; no routing/HOTCO authority |

---

# 21. Scientific and architectural invariants

The current implementation is intended to preserve the following rules:

1. **Observed and estimated beliefs are not conflated.**
2. **Estimated beliefs never become fabricated raw user answers.**
3. **Availability remains separate from cognitive preference.**
4. **Trip context does not automatically rewrite the stable Cognitive Passport.**
5. **Longitudinal profile changes require explicit user confirmation.**
6. **Routing owns candidate generation and rank.**
7. **GraphHopper supplies supplemental geometry, not rank.**
8. **Context can modify HOTCO deliberation without modifying routing rank.**
9. **F3 contextual forcing is experimental and currently need-only.**
10. **Deterministic XAI precedes optional LLM narration.**
11. **The LLM cannot invent a recommendation that contradicts deterministic evidence.**
12. **Unknown or unavailable external data remain explicitly unknown/unavailable.**
13. **No demo route, sample passport, fixed confidence value, or neutral replacement is silently substituted for a failed live dependency.**

---

# 22. Adaptive model status

The Adaptive Cognitive Passport v1 is currently an **experimental, internally validated personalization layer**.

The initial four-item calibration policy is frozen and TRAIN-derived. The remaining belief cells are statistically estimated, and later explicit micro-question answers can progressively replace estimated cells with observed measurements.

This should not be described as independent confirmatory validation, calibrated uncertainty estimation, or proof that more-than-four personalization is already externally validated.

---

# 23. Remaining work before end-to-end adaptive production readiness

The highest-priority integration tasks are:

1. **Make `PassportAdapter` adaptive-aware.** Accept valid Adaptive Cognitive Passport provenance instead of requiring 44 observed beliefs, while continuing to expose provenance honestly.
2. **Add an adaptive passport → routing contract test.** A freshly bootstrapped 4-observed/40-estimated passport must successfully produce the routing request expected by the external service.
3. **Make contextual-deliberation reconstruction adaptive-aware.** Reuse the normalized adaptive HOTCO profile directly or use a dedicated adaptive reconstruction path; do not invert estimated beliefs into invented integer raw responses.
4. **Add an adaptive route → context → XAI integration test.** Verify route identity, candidate order, geometry association, context propagation, deterministic evidence and narration fallback.
5. **Deploy the matching DYCONET backend before relying on the production flavor.** Android and backend schema versions must move together.
6. **Run a physical-device production-style smoke test after deployment.**

---

# 24. Main technologies

- Kotlin
- Jetpack Compose / Material 3
- Retrofit / OkHttp
- Gson and kotlinx-serialization
- Kotlin coroutines
- Google Play Services Location
- MapLibre GL
- Python / Flask DYCONET backend
- HOTCO-CT v4.3
- GraphHopper
- FIWARE/Orion context provider
- optional server-side LLM narration provider

---

## Repository responsibility

This repository contains the **Android frontend**.

The HOTCO-CT / Adaptive Cognitive Passport service lives in the IMIQ backend DYCONET service.

The external route-ranking service remains a separate component and is not owned by HOTCO-CT.
