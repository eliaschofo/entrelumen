"""Prepare and run isolated NeoForge test servers without reading launcher credentials."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import urllib.request
import uuid
from contextlib import contextmanager

FLAGS = getattr(subprocess, "CREATE_NO_WINDOW", 0)
NEOFORGE = "21.1.249"


def owned_destination(value):
    destination = Path(value).resolve()
    marker = destination / ".entrelumen-test-server.json"
    if not marker.is_file() or json.loads(marker.read_text())["owner"] != "entrelumen":
        raise ValueError("Not an ENTRELUMEN-owned test server")
    return destination


def write_json(path, value):
    temporary = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


@contextmanager
def file_lock(path):
    """OS lock, released on runner crash; keep the file to avoid inode races."""
    with path.open("a+b") as handle:
        if handle.tell() == 0:
            handle.write(b"0")
            handle.flush()
        handle.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise ValueError("Another owner is operating this destination or request") from error
        try:
            yield
        finally:
            handle.seek(0)
            if os.name == "nt":
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle, fcntl.LOCK_UN)


def process_alive(pid):
    """Conservative liveness only: never signal/terminate a PID from a receipt."""
    if not isinstance(pid, int) or pid <= 0:
        raise ValueError("Invalid owned-process PID")
    if os.name == "nt":
        import ctypes
        from ctypes import wintypes
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.GetExitCodeProcess.argtypes = (wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD))
        kernel.CloseHandle.argtypes = (wintypes.HANDLE,)
        handle = kernel.OpenProcess(0x1000, False, pid)
        if not handle:
            return ctypes.get_last_error() != 87  # Access denied is not evidence of death.
        try:
            code = wintypes.DWORD()
            return not kernel.GetExitCodeProcess(handle, ctypes.byref(code)) or code.value == 259
        finally:
            kernel.CloseHandle(handle)
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def check_previous_run(destination):
    receipt = destination / "owned-process.json"
    if receipt.exists():
        state = json.loads(receipt.read_text(encoding="utf-8"))
        if state.get("owner") != "entrelumen" or Path(state.get("cwd", "")).resolve() != destination:
            raise ValueError("Process receipt ownership does not match destination")
        if "ended" not in state and process_alive(state.get("pid")):
            raise ValueError("Previous server PID is still live or unverifiable; inspect it before launching")


def java_path(value: str) -> Path:
    path = Path(value).resolve()
    if not path.is_file():
        raise ValueError(f"Java executable does not exist: {path}")
    return path


def prepare(args):
    destination = Path(args.destination).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    marker = destination / ".entrelumen-test-server.json"
    if any(destination.iterdir()) and not marker.exists():
        raise ValueError("Refusing to alter a non-empty unowned server directory")
    if marker.exists():
        owned_destination(destination)
        check_previous_run(destination)
    marker.write_text(json.dumps({"owner": "entrelumen", "neoforge": NEOFORGE}, indent=2) + "\n")
    installer = destination / f"neoforge-{NEOFORGE}-installer.jar"
    uri = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEOFORGE}/{installer.name}"
    if not installer.exists():
        urllib.request.urlretrieve(uri, installer)
    with urllib.request.urlopen(uri + ".sha256") as response:
        expected = response.read().decode().strip().split()[0]
    if hashlib.sha256(installer.read_bytes()).hexdigest() != expected:
        raise ValueError("NeoForge installer checksum does not match official metadata")
    with (destination / "installer.log").open("w", encoding="utf-8") as log:
        completed = subprocess.run(
            [str(java_path(args.java)), "-jar", str(installer), "--installServer", str(destination)],
            cwd=destination, stdout=log, stderr=subprocess.STDOUT, creationflags=FLAGS,
        )
    if completed.returncode:
        raise RuntimeError(f"Installer failed ({completed.returncode}); inspect {destination / 'installer.log'}")
    (destination / "eula.txt").write_text(
        "# https://aka.ms/MinecraftEULA\n" + ("eula=true\n" if args.accept_eula else "eula=false\n")
    )
    properties = {
        "server-ip": "127.0.0.1", "server-port": str(args.port), "online-mode": "true",
        "enable-rcon": "false", "max-players": "6", "view-distance": "10",
        "simulation-distance": "6", "level-seed": "71942026", "level-name": "entrelumen-test",
        "difficulty": "normal", "gamemode": "survival", "allow-flight": "true",
        "spawn-protection": "0", "motd": "ENTRELUMEN local verification",
        "enable-status": "true", "sync-chunk-writes": "true",
    }
    target = destination / "server.properties"
    if not target.exists():
        target.write_text("".join(f"{key}={value}\n" for key, value in properties.items()))
    print(json.dumps({"prepared": str(destination), "neoforge": NEOFORGE, "port": args.port,
                      "eulaAccepted": args.accept_eula}))


def run(args):
    destination = owned_destination(args.destination)
    with file_lock(destination / ".runtime.lock"):
        check_previous_run(destination)
        return run_locked(args, destination)


def run_locked(args, destination):
    if not re.fullmatch(r"[1-8]G", args.heap):
        raise ValueError("Heap must be 1G..8G for the approved memory budget")
    argfile = destination / "libraries/net/neoforged/neoforge" / NEOFORGE / (
        "win_args.txt" if os.name == "nt" else "unix_args.txt"
    )
    if not argfile.is_file():
        raise ValueError("Server installation is incomplete")
    # The installer and runner never share a console with the player's client.
    # A run-specific file queue works even when the host closes command stdin.
    run_id = str(uuid.uuid4())
    control = destination / "control" / run_id
    inbox, results = control / "requests", control / "results"
    inbox.mkdir(parents=True)
    results.mkdir()
    command = [str(java_path(args.java)), f"-Xmx{args.heap}", "-Xms1G", f"@{argfile}", "nogui"]
    process = subprocess.Popen(command, cwd=destination, stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding="utf-8", errors="replace",
                               creationflags=FLAGS, bufsize=1)
    receipt = destination / "owned-process.json"
    try:
        write_json(receipt, {"pid": process.pid, "owner": "entrelumen", "runId": run_id,
                                      "cwd": str(destination), "java": str(java_path(args.java)),
                                      "started": time.time(), "command": command})
    except BaseException:
        # Do not orphan the freshly spawned child if its ownership receipt fails.
        process.terminate()
        process.wait(timeout=15)
        process.stdin.close()
        process.stdout.close()
        raise
    console_lock = threading.Lock()

    def receive_commands():
        while process.poll() is None:
            for request in sorted(inbox.glob("*.json")):
                result = results / request.name
                if result.exists():
                    continue
                claimed = request.with_suffix(".claimed")
                try:
                    request.rename(claimed)
                    payload = json.loads(claimed.read_text(encoding="utf-8"))
                    line = payload["command"]
                    if payload["runId"] != run_id or not valid_command(line):
                        raise ValueError("Invalid command or obsolete server run")
                    with console_lock:
                        process.stdin.write(line + "\n")
                        process.stdin.flush()
                    write_json(result, {"requestId": claimed.stem, "status": "delivered",
                                        "deliveredAt": time.time(), "runId": run_id,
                                        "commandHash": hashlib.sha256(line.encode()).hexdigest(),
                                        "note": "Written to server console; inspect logs for execution result"})
                except (ValueError, KeyError, TypeError) as error:
                    write_json(result, {"requestId": claimed.stem, "status": "rejected",
                                        "reason": str(error), "runId": run_id})
                except OSError as error:
                    # A failed flush/result write may follow a successful server read.
                    write_json(result, {"requestId": claimed.stem, "status": "unconfirmed",
                                        "reason": str(error), "runId": run_id,
                                        "note": "Delivery may have occurred; inspect logs, never replay automatically"})
            time.sleep(0.1)

    receiver = threading.Thread(target=receive_commands, daemon=True)
    receiver.start()
    try:
        with (destination / "verification-console.log").open("a", encoding="utf-8") as log:
            for line in process.stdout:
                log.write(line)
                log.flush()
                print(line, end="", flush=True)
        return process.wait()
    finally:
        if process.poll() is None:
            try:
                with console_lock:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                process.wait(timeout=30)
            except (BrokenPipeError, OSError, subprocess.TimeoutExpired):
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()  # Only this Popen-owned transient child.
                    process.wait(timeout=15)
        receiver.join(timeout=2)
        process.stdin.close()
        process.stdout.close()
        state = json.loads(receipt.read_text())
        state.update({"exitCode": process.returncode, "ended": time.time()})
        write_json(receipt, state)


def valid_command(command):
    return isinstance(command, str) and bool(command.strip()) and len(command) <= 2048 and not any(ord(c) < 32 or ord(c) == 127 for c in command)


def send_command(args):
    destination = owned_destination(args.destination)
    receipt = json.loads((destination / "owned-process.json").read_text())
    if receipt.get("owner") != "entrelumen" or Path(receipt.get("cwd", "")).resolve() != destination:
        raise ValueError("Process receipt ownership does not match destination")
    if "ended" in receipt or "runId" not in receipt:
        raise ValueError("This server run is terminal or does not support the command queue")
    run_id = str(uuid.UUID(receipt["runId"]))
    if not valid_command(args.text):
        raise ValueError("Expected one console command without control characters")
    request_id = str(uuid.UUID(args.request_id)) if args.request_id else str(uuid.uuid4())
    control = destination / "control" / run_id
    request = control / "requests" / (request_id + ".json")
    result = control / "results" / request.name
    claimed = request.with_suffix(".claimed")
    with file_lock(control / (request_id + ".lock")):
        payload = None
        # The consumer can rename .json to .claimed between existence and read.
        for candidate in (request, claimed, request, claimed):
            try:
                payload = json.loads(candidate.read_text(encoding="utf-8"))
                break
            except FileNotFoundError:
                pass
        if payload is not None:
            if payload.get("runId") != run_id or payload.get("command") != args.text:
                raise ValueError("Request ID already belongs to different command content")
        elif result.exists():
            previous = json.loads(result.read_text(encoding="utf-8"))
            if previous.get("commandHash") != hashlib.sha256(args.text.encode()).hexdigest():
                raise ValueError("Cannot verify request content; inspect original receipt")
        else:
            if not process_alive(receipt.get("pid")):
                raise ValueError("Owned server process is not live; no command was queued")
            write_json(request, {"runId": run_id, "command": args.text})
    until = time.monotonic() + 10
    while time.monotonic() < until:
        if result.exists():
            value = json.loads(result.read_text())
            print(json.dumps(value))
            return {"delivered": 0, "rejected": 1, "unconfirmed": 2}.get(value["status"], 2)
        time.sleep(0.1)
    print(json.dumps({"requestId": request_id, "status": "unconfirmed", "runId": receipt["runId"],
                      "note": "Do not resubmit with a new ID; inspect this run and request"}))
    return 2


def main():
    # Mod names and server chat may contain glyphs outside Windows' code page.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    create = sub.add_parser("prepare-server")
    create.add_argument("--destination", required=True)
    create.add_argument("--java", required=True)
    create.add_argument("--accept-eula", action="store_true")
    create.add_argument("--port", type=int, default=25576)
    create.set_defaults(function=prepare)
    launch = sub.add_parser("run-server")
    launch.add_argument("--destination", required=True)
    launch.add_argument("--java", required=True)
    launch.add_argument("--heap", default="8G")
    launch.set_defaults(function=run)
    console = sub.add_parser("command")
    console.add_argument("--destination", required=True)
    console.add_argument("--text", required=True)
    console.add_argument("--request-id", help="Reuse only to inspect/retry this logical command")
    console.set_defaults(function=send_command)
    args = parser.parse_args()
    return args.function(args) or 0


if __name__ == "__main__":
    raise SystemExit(main())
