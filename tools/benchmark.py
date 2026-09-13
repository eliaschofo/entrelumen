"""Validate supplied ENTRELUMEN measurements; never collect or invent samples."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import sys

SCENARIOS = {"early_base", "mid_base", "late_base", "exploration"}
MIN_ROUTE_MS = 300_000
MIN_SOAK_MS = 7_200_000


def percentile(values, p):
    """Nearest rank: sorted[ceil(p*n)-1]; no interpolation."""
    if not values:
        raise ValueError("empty sample set")
    return sorted(values)[max(0, math.ceil(p * len(values)) - 1)]


def read_csv(path, columns):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not set(columns).issubset(reader.fieldnames or []):
            raise ValueError(f"{path.name}: required columns {columns}")
        rows = []
        for index, row in enumerate(reader, 2):
            parsed = {}
            for column in columns:
                try:
                    value = float(row[column])
                except (ValueError, TypeError):
                    raise ValueError(f"{path.name}:{index}: invalid {column}") from None
                if not math.isfinite(value) or value < 0:
                    raise ValueError(f"{path.name}:{index}: nonfinite/negative {column}")
                parsed[column] = value
            if rows and parsed["elapsed_ms"] <= rows[-1]["elapsed_ms"]:
                raise ValueError(f"{path.name}:{index}: elapsed_ms must strictly increase")
            rows.append(parsed)
    if len(rows) < 2:
        raise ValueError(f"{path.name}: at least two samples required")
    return rows


def fingerprint(path):
    return {"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


COLLECTOR = "entrelumen-integrated-v1"


def validate_capture(config, paths):
    """Check original collector artifacts before computing any acceptance metrics."""
    supplied = [Path(path) for path in paths if path is not None]
    roots = {path.resolve().parent for path in supplied}
    declared = str(config.get("collector", "")).startswith("entrelumen-integrated")
    detected = any((root / "session.json").exists() or
                   (root / "capture-integrity.json").exists() or
                   root.name.startswith("capture-") for root in roots)
    if not declared and not detected:
        return []
    if len(roots) != 1:
        raise ValueError("collector capture requires files from one original run directory")
    root = roots.pop()
    artifacts = []
    documents = {}
    for name in ("session.json", "capture-integrity.json"):
        path = root / name
        documents[name] = json.loads(path.read_text(encoding="utf-8-sig"))
        if not isinstance(documents[name], dict):
            raise ValueError(f"{name}: object required")
        artifacts.append(fingerprint(path))
    session, integrity = documents["session.json"], documents["capture-integrity.json"]
    if config.get("collector") != COLLECTOR or session.get("collector") != COLLECTOR:
        raise ValueError("collector metadata must match supported original session")
    if session.get("clock") != "shared_process_nanoTime" or config.get("mode") not in {"singleplayer", "integrated_lan"}:
        raise ValueError("collector requires a shared integrated-server clock")
    if (integrity.get("status") != "closed_needs_review" or integrity.get("reasons") != [] or
            integrity.get("pending_rows") != 0 or isinstance(integrity.get("pending_rows"), bool) or
            integrity.get("clock_origin_verified") is not True or integrity.get("acceptance") is not False or
            integrity.get("tick_scope") != "vanilla_tickServer"):
        raise ValueError("capture integrity is incomplete or invalid: " + str(integrity.get("reasons", integrity.get("status"))))
    for name, column in (("frames", "frame_ms"), ("ticks", "tick_ms"), ("memory", "heap_used_mb")):
        path = root / (name + ".csv")
        read_csv(path, ("elapsed_ms", column))
        artifacts.append(fingerprint(path))
    for supplied_path, expected in zip(paths, ("frames.csv", "ticks.csv", "memory.csv")):
        if supplied_path is not None and Path(supplied_path).resolve() != root / expected:
            raise ValueError("collector input must use original " + expected)
    # Inspect raw flags even when someone accidentally retained a clean sidecar.
    for name in ("frames", "ticks", "events", "gc", "post_gc"):
        path = root / (name + ".csv")
        with path.open(encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if not {"elapsed_ms", "detail"}.issubset(reader.fieldnames or []):
                raise ValueError(f"{name}.csv: missing raw columns")
            previous = -1.0
            tick = None
            for row in reader:
                elapsed = float(row["elapsed_ms"])
                if not math.isfinite(elapsed) or elapsed < 0 or elapsed <= previous:
                    raise ValueError(f"{name}.csv: raw timestamps must strictly increase")
                previous = elapsed
                if name == "events" or (name == "frames" and row["detail"] != "valid"):
                    raise ValueError(f"{name}.csv: invalid pause/focus/loading frame state")
                if name == "ticks":
                    counter = int(row["detail"])
                    if tick is not None and counter != tick + 1:
                        raise ValueError("ticks.csv: missing or duplicated tick")
                    tick = counter
                if name in {"gc", "post_gc"}:
                    value = float(row["value"])
                    if not math.isfinite(value) or value < (-1 if name == "gc" else 0):
                        raise ValueError(f"{name}.csv: invalid raw value")
            if name == "gc" and previous < 0:
                raise ValueError("gc.csv: no GC observations")
        artifacts.append(fingerprint(path))
    return artifacts


def validate_config(config):
    problems = []
    for key in ("hardware", "preset", "world"):
        if not isinstance(config.get(key), dict):
            return [f"{key} must be an object"]
    for key in ("run_id", "captured_at", "pack_revision", "collector", "hardware", "preset", "world", "scenario", "mode", "evidence_kind"):
        if not config.get(key):
            problems.append(f"missing metadata: {key}")
    if config.get("evidence_kind") != "real":
        problems.append("synthetic or unclassified input is not acceptance evidence")
    if config.get("scenario") not in SCENARIOS:
        problems.append("scenario must be early_base, mid_base, late_base or exploration")
    if config.get("mode") not in {"singleplayer", "integrated_lan", "dedicated"}:
        problems.append("unknown execution mode")
    hardware = config.get("hardware", {})
    for key in ("cpu", "gpu", "ram_gb", "os", "java"):
        if not hardware.get(key):
            problems.append(f"missing hardware.{key}")
    if hardware.get("cpu") != "i7-8750H" or hardware.get("gpu") != "GTX 1070" or hardware.get("ram_gb") != 16:
        problems.append("not the agreed reference hardware; report is diagnostic only")
    preset = config.get("preset", {})
    for key, expected in {"width": 1920, "height": 1080, "render_chunks": 10, "simulation_chunks": 6, "shaders": False}.items():
        if preset.get(key) != expected:
            problems.append(f"preset.{key} must be {expected}")
    heap = preset.get("heap_gb")
    if not isinstance(heap, (float, int)) or isinstance(heap, bool) or not 0 < heap <= 8:
        problems.append("preset.heap_gb must be >0 and <=8 (combined for integrated LAN)")
    world = config.get("world", {})
    for key in ("seed", "save_id", "route_id", "dimension"):
        if key not in world or world[key] == "":
            problems.append(f"missing world.{key}")
    if config.get("scenario") != "exploration" and world.get("preloaded") is not True:
        problems.append("base routes require preloaded terrain")
    if config.get("scenario") == "exploration" and world.get("preloaded") is not False:
        problems.append("exploration must separately record new terrain generation")
    players = config.get("players")
    if not isinstance(players, int) or not 1 <= players <= 6:
        problems.append("players must be 1..6")
    if config.get("mode") != "singleplayer" and players != 6:
        problems.append("co-op acceptance requires six players; fewer is diagnostic")
    if config.get("mode") != "singleplayer" and config.get("external_clients") is not True:
        problems.append("co-op load must use external clients, not six local instances")
    if config.get("mode") == "dedicated" and not config.get("server_hardware"):
        problems.append("dedicated mode requires separately recorded server_hardware")
    return problems


def route_metrics(frames, ticks):
    frame_values = [row["frame_ms"] for row in frames]
    tick_values = [row["tick_ms"] for row in ticks]
    if min(frame_values) <= 0:
        raise ValueError("frame_ms must be positive")
    duration = math.fsum(frame_values)
    frame_span = frames[-1]["elapsed_ms"] - frames[0]["elapsed_ms"]
    tick_span = ticks[-1]["elapsed_ms"] - ticks[0]["elapsed_ms"]
    problems = []
    if duration < MIN_ROUTE_MS or frame_span < MIN_ROUTE_MS or tick_span < MIN_ROUTE_MS:
        problems.append("route requires at least 300 seconds in both frame and tick streams")
    # Catch sampled/aggregated FPS streams: these must be individual consecutive frames.
    for before, after in zip(frames, frames[1:]):
        interval = after["elapsed_ms"] - before["elapsed_ms"]
        if abs(interval - after["frame_ms"]) > max(1.0, after["frame_ms"] * 0.02):
            problems.append("frame timestamps do not match individual consecutive frame times")
            break
    if abs(frame_span - tick_span) > max(1000, frame_span * 0.01):
        problems.append("frame/tick capture spans differ by more than 1% or one second")
    if abs(frames[0]["elapsed_ms"] - ticks[0]["elapsed_ms"]) > 1000:
        problems.append("capture streams must share a clock origin and start within one second")
    fps = len(frames) * 1000 / duration
    p99 = percentile(frame_values, 0.99)
    p95 = percentile(tick_values, 0.95)
    tps = (len(ticks) - 1) * 1000 / tick_span
    metrics = {"frame_samples": len(frames), "tick_samples": len(ticks),
               "frame_duration_ms": duration, "frame_span_ms": frame_span, "tick_span_ms": tick_span,
               "fps_mean": fps, "frame_p99_ms": p99, "fps_1pct_low": 1000 / p99,
               "tick_p95_ms": p95, "observed_tps": tps,
               "frame_max_ms": max(frame_values), "tick_max_ms": max(tick_values),
               "frames_over_50ms": sum(v > 50 for v in frame_values),
               "frames_over_100ms": sum(v > 100 for v in frame_values)}
    checks = {"fps_mean_gte_60": fps >= 60, "fps_1pct_low_gte_40": 1000 / p99 >= 40,
              "tick_p95_lt_50": p95 < 50}
    return metrics, checks, problems


def memory_metrics(rows, config, base):
    span = rows[-1]["elapsed_ms"] - rows[0]["elapsed_ms"]
    max_gap = max(b["elapsed_ms"]-a["elapsed_ms"] for a,b in zip(rows, rows[1:]))
    values = [r["heap_used_mb"] for r in rows]
    problems = []
    if span < MIN_SOAK_MS or len(rows) < 121 or max_gap > 65_000:
        problems.append("memory requires >=2h, >=121 samples, and gaps <=65s")
    review = config.get("memory_review", {})
    if not isinstance(review, dict):
        review = {}
    conclusion = review.get("conclusion", "unreviewed")
    evidence = base / review.get("evidence_file", "")
    if conclusion not in {"stable", "sustained_growth"} or not evidence.is_file() or evidence.stat().st_size == 0:
        problems.append("memory needs a separate GC-aware written review; RAM percentage is insufficient")
    return {"samples": len(rows), "span_ms": span, "max_gap_ms": max_gap,
            "heap_min_mb": min(values), "heap_max_mb": max(values),
            "heap_start_mb": values[0], "heap_end_mb": values[-1],
            "review_conclusion": conclusion, "review_evidence": fingerprint(evidence) if evidence.is_file() else None,
            "status": "incomplete" if problems else "fail" if conclusion == "sustained_growth" else "pass",
            "reasons": problems, "note": "pass incorporates supplied human review; no automated leak inference"}


def report(config, frames_path=None, ticks_path=None, memory_path=None, base=Path('.')):
    problems = validate_config(config)
    result = {"schema": 1, "status": "incomplete", "scope": "one scenario; not campaign or full performance acceptance",
              "config": config, "sources": [], "reasons": problems,
              "definitions": {"fps_mean": "1000 * frame_count / sum(frame_ms)",
                              "fps_1pct_low": "1000 / nearest-rank p99(frame_ms)",
                              "tick_p95_ms": "nearest-rank p95(tick_ms)",
                              "observed_tps": "1000 * (tick_count - 1) / timestamp_span_ms; diagnostic, not sustained-TPS proof"}}
    try:
        result["sources"].extend(validate_capture(config, (frames_path, ticks_path, memory_path)))
        if frames_path and ticks_path:
            frames = read_csv(frames_path, ("elapsed_ms", "frame_ms"))
            ticks = read_csv(ticks_path, ("elapsed_ms", "tick_ms"))
            result["sources"] += [fingerprint(frames_path), fingerprint(ticks_path)]
            metrics, checks, errors = route_metrics(frames, ticks)
            result.update(metrics=metrics, checks=checks)
            problems.extend(errors)
            # High average TPS alone cannot establish sustained load; avoid a false acceptance claim.
            if metrics["observed_tps"] < 19.8 or metrics["observed_tps"] > 20.2:
                problems.append("tick stream cadence is outside expected 20TPS; investigate stalled or missing samples")
            if config.get("scenario") == "exploration":
                result["checks"] = {"tick_p95_lt_50": checks["tick_p95_lt_50"]}
                result["exploration_note"] = "FPS reported separately; preloaded-base FPS thresholds are not applied"
        elif not memory_path:
            problems.append("both frame and tick CSVs required for a route")
        elif frames_path or ticks_path:
            problems.append("partial route input: frame and tick CSVs must be supplied together")
        if memory_path:
            rows = read_csv(memory_path, ("elapsed_ms", "heap_used_mb"))
            result["sources"].append(fingerprint(memory_path))
            result["memory"] = memory_metrics(rows, config, base)
            if result["memory"]["status"] == "incomplete":
                problems.extend(result["memory"]["reasons"])
        if not problems:
            failed = any(not value for value in result.get("checks", {}).values()) or result.get("memory", {}).get("status") == "fail"
            result["status"] = "fail" if failed else "pass"
    except (ValueError, OSError, KeyError, TypeError, ZeroDivisionError) as error:
        problems.append(str(error))
    return result


def self_test():
    # Explicitly synthetic calculations. These are never exported as pack evidence.
    assert percentile(list(range(1, 101)), .99) == 99
    assert percentile(list(range(1, 101)), .95) == 95
    frames = [{"elapsed_ms": i*10., "frame_ms": 10.} for i in range(30001)]
    ticks = [{"elapsed_ms": i*50., "tick_ms": 49.} for i in range(6001)]
    metrics, checks, errors = route_metrics(frames, ticks)
    assert metrics["fps_mean"] == 100 and metrics["fps_1pct_low"] == 100 and all(checks.values()) and not errors
    for row in ticks: row["tick_ms"] = 50.
    assert route_metrics(frames, ticks)[1]["tick_p95_lt_50"] is False
    for row in frames: row["frame_ms"] = 25.
    assert route_metrics(frames, ticks)[1]["fps_1pct_low_gte_40"] is True
    assert route_metrics(frames, ticks)[1]["fps_mean_gte_60"] is False
    assert route_metrics(frames[:10], ticks[:10])[2]
    assert validate_config({"evidence_kind": "synthetic"})
    import tempfile
    with tempfile.TemporaryDirectory(prefix='entrelumen-synthetic-parser-test-') as temporary:
        path=Path(temporary)/'synthetic.csv'
        for body in ['elapsed_ms,frame_ms\n0,NaN\n10,10\n', 'elapsed_ms,frame_ms\n0,10\n0,10\n', 'elapsed_ms,other\n0,10\n10,10\n']:
            path.write_text(body,encoding='utf-8')
            try:read_csv(path,('elapsed_ms','frame_ms'))
            except ValueError:pass
            else:raise AssertionError('malformed CSV accepted')
    print('PASS synthetic unit checks only: nearest-rank percentiles, FPS, strict 50ms/40FPS boundaries, short input and malformed CSV rejection.')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--config',type=Path);p.add_argument('--frames',type=Path);p.add_argument('--ticks',type=Path)
    p.add_argument('--memory',type=Path);p.add_argument('--output',type=Path);p.add_argument('--self-test',action='store_true')
    args=p.parse_args()
    if args.self_test:
        self_test();return
    if not args.config:p.error('--config is required')
    try:
        config=json.loads(args.config.read_text(encoding='utf-8'))
        if not isinstance(config,dict):raise ValueError('configuration must be an object')
        result=report(config,args.frames,args.ticks,args.memory,args.config.parent)
        result['sources'].insert(0,fingerprint(args.config))
    except (OSError,ValueError,TypeError) as error:
        result={'schema':1,'status':'incomplete','reasons':[str(error)]}
    rendered=json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False)+'\n'
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(rendered,encoding='utf-8')
    else:sys.stdout.write(rendered)
    sys.exit({'pass':0,'fail':1,'incomplete':2}[result['status']])


if __name__=='__main__':main()
