# DYCONET: HOTCO-CT v4.3 online service

This service implements the 19-node HOTCO-CT v4.3 reference dynamics:

- 11 need states, 4 action states, and 4 valence states;
- participant-specific signed need-action topology scaled as `R / 11`;
- net-first excitation and inhibition;
- bounded Grossberg shunting dynamics;
- pairwise lateral action inhibition (`lambda = 2.0`);
- stage-projected RK4 (`dt = 0.02`, `T = 40`, `tau = 0.8`);
- terminal state read at `T = 40`, independently of settling status.

## Input policy

The online service accepts the strict `hotco_ct_input_2.1` contract and only
explicit current-user questionnaire responses. It performs no missing-value
replacement, population borrowing, synthetic completion, behavioral anchoring,
or neutral substitution. A request is rejected with HTTP 422 if any of the
following is absent or invalid:

- 11 need ratings (1-7);
- 44 need-action belief ratings (1-7: every need for every mode);
- 4 action valences (-3 to +3);
- 4 explicit binary availability responses, with at least one available mode.

Mode-use frequency is deliberately not part of schema 2.1 and is rejected if
sent inside `responses`. It cannot enter the state equation, topology, solver,
readout, or availability logic. The optional top-three need ranking remains a
non-dynamic user-reported reference.

### Availability resolution

Phase 1 resolves availability only from the current user's explicit access
declarations. `availability_resolver.py` converts those four booleans into the
binary HOTCO action-gate vector `q` and records per-mode provenance. It does not
infer access from frequency, preference, HOTCO output, weather, routing, or
population data.

The resolver contains a provider protocol as an extension seam for a later
version. No routing/API provider is called in Phase 1. External information can
therefore be integrated later without modifying the HOTCO solver or the
colleague-owned routing repository.

## Run locally

### Windows / PowerShell

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
$env:PORT = "8077"
python main.py
```

Open `http://127.0.0.1:8077/health` before starting the Android app.

### Docker

```bash
docker build -t dyconet-hotco-ct-v4-3 services/dyconet
docker run --rm -p 8077:5000 dyconet-hotco-ct-v4-3
```

From the repository root:

```bash
curl -sS -X POST \
  -H 'Content-Type: application/json' \
  --data @services/dyconet/example_request.json \
  http://127.0.0.1:8077/api/dyconet
```

## Test

```bash
cd services/dyconet
python -m unittest discover -s tests -v
```

The output remains wrapped as `{"cognitive_passport": {...}}`. Cognitive
Passport schema 2.0 is retained as the stable downstream contract. It records
`hotco_ct_input_2.1` as its source schema, includes availability-resolution
provenance, and carries versioned deterministic `xai_diagnostics` alongside
four deprecated Android aliases: `final_choice`, `probabilities`, `confidence`,
and `convergence_achieved`.
