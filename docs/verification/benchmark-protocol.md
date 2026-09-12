# Measurement evidence protocol

`tools/benchmark.py` evaluates supplied measurements. It does not launch Minecraft, collect telemetry, invent baselines, or claim a full campaign/performance pass. Synthetic calculations are only unit tests: `python tools/benchmark.py --self-test`.

## Capture and metadata

For each `early_base`, `mid_base`, `late_base`, and `exploration` scenario, capture at least **300 seconds**, excluding a separately recorded warmup. Use the same world seed, saved base and route across comparisons. Record consecutive individual frames and ticks, including stalls; do not export averaged FPS samples or remove outliers. Both streams use the same elapsed clock and capture window. Begin them within one second. CSVs use decimal points and milliseconds:

```csv
elapsed_ms,frame_ms
```

```csv
elapsed_ms,tick_ms
```

Each frame timestamp denotes the frame's end; each tick timestamp denotes the tick's start. `tick_ms` measures processing, not the scheduled 50ms tick interval. Timestamp cadence detects missing/irregular samples but cannot distinguish dropped observations from server stalls without collector evidence. The parser never reports sustained 20TPS solely from a p95 processing time or a whole-run average.

Create a metadata JSON with these fields. Replace all identifiers and collector details with the actual recording; this schema illustration contains **no measurements**:

```json
{
  "run_id": "actual-capture-id",
  "captured_at": "actual-ISO-8601-date",
  "evidence_kind": "real",
  "pack_revision": "actual-commit-and-lock-hash",
  "collector": "actual-tool-version-and-settings",
  "hardware": {"cpu": "i7-8750H", "gpu": "GTX 1070", "ram_gb": 16, "os": "actual-version", "java": "actual-version"},
  "preset": {"width": 1920, "height": 1080, "render_chunks": 10, "simulation_chunks": 6, "shaders": false, "heap_gb": 8},
  "world": {"seed": "actual-seed", "save_id": "actual-snapshot-hash", "route_id": "actual-route", "dimension": "minecraft:overworld", "preloaded": true},
  "scenario": "early_base",
  "mode": "singleplayer",
  "players": 1
}
```

`mode` is `singleplayer`, `integrated_lan`, or `dedicated`. Co-op acceptance requires `players: 6` and `external_clients: true`. Dedicated captures additionally record `server_hardware` separately. Integrated LAN uses one combined JVM budget, at most 8GB; do not launch six local clients. Other hardware/player counts remain useful diagnostics, but cannot pass the agreed reference acceptance. Exploration uses `preloaded: false` and has its FPS/tirons reported separately from preloaded-base FPS gates.

## Calculation and report

```powershell
python tools/benchmark.py --config capture.json --frames frames.csv --ticks ticks.csv --output report.json
```

- Mean FPS = `1000 × number of frames / sum(frame_ms)`, not mean instantaneous FPS.
- 1% low FPS = `1000 / p99(frame_ms)`. Percentiles use **nearest rank**, index `ceil(p × n) − 1` after sorting. This definition is explicitly different from averaging the slowest 1% of frames.
- p95 MSPT = nearest-rank p95 of tick processing durations.
- Gates: mean FPS **≥60**, 1% low **≥40**, p95 MSPT **<50**. Exactly 50ms fails the tick gate. FPS gates apply to preloaded base scenarios; exploration still reports these metrics.
- Additional diagnostics: maximum frametime/ticktime, counts of frames over 50/100ms, observed whole-run TPS, sample counts and capture spans.
- `pass` means this supplied scenario meets its automated gates with complete metadata. `fail` means complete evidence breaches a numerical gate. `incomplete` takes precedence for missing/invalid data, short windows, metadata mismatches, nonconsecutive frame capture, or unexplained tick cadence outside 19.8–20.2 TPS. That band is a diagnostic tolerance, **not** a relaxed sustained-20TPS acceptance.
- Exit codes: 0 pass, 1 fail, 2 incomplete. JSON records definitions, configuration, counts, reasons and input SHA-256 fingerprints. Preserve raw CSVs alongside reports. Declaring `evidence_kind: real` does not authenticate the measurement; collector provenance and reproducibility remain review requirements.

A full performance acceptance needs all four scenarios, integrated LAN and dedicated multiplayer runs, sustained-TPS review, and the separate soak below. One report never establishes that matrix or confirms 150–200-hour pacing.

## Separate two-hour memory soak

Record `elapsed_ms,heap_used_mb` at intervals of at most 60 seconds, including a starting sample, for at least 7,200,000ms. The parser requires at least 121 samples and rejects gaps above 65 seconds. Retain GC events, post-GC heap, allocation context, workload phases, world/route and any pauses in adjacent collector artifacts; extra CSV columns are permitted.

```powershell
python tools/benchmark.py --config soak.json --memory memory.csv --output soak-report.json
```

RAM percentage, a rising working set, or start/end heap alone cannot establish a leak. The report displays heap range and endpoints without diagnosing one. A complete memory verdict requires metadata `memory_review` with `conclusion` (`stable` or `sustained_growth`) and `evidence_file` pointing to a nonempty written GC-aware review relative to the config. That review must cite collector artifacts, comparable post-GC retained heap and consistent workload windows. Without it, memory remains `incomplete`; `sustained_growth` yields `fail`. A memory `pass` incorporates that supplied review and is **not** an automated proof of no leak. No memory review is generated by this tool.
