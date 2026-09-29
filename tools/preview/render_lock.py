"""At most two preview renders at a time on this PC (ENTRELUMEN_RENDER_SLOTS), and none while RAM is short.

The PC has 16 GB for five or six working sessions, so previews queue: a render takes the lock folder
E:/Elias/Codex/Entrelumen-ssd/render.lock (ENTRELUMEN_RENDER_LOCK overrides it), or the next free slot folder
render.lock.2 and so on, with os.mkdir, which either creates it or fails, atomically. A Pillow render peaks near
200 MB, so two slots fit (29/9: one slot left writers queuing half an hour). Inside goes owner.json: process id and start time, host, working folder,
command and since when. The lock is released on exit: the context manager, atexit, and SIGINT, SIGTERM and
SIGBREAK turned into a normal exit. A process killed outright cannot release, so a lock whose owner process is
gone (or is another process with the same id) is stale, and the next render clears it: its owner.json and the
empty folder, nothing else. A lock folder that holds anything else, or is a link, is left alone and reported.

Before each chapter, a render also waits while free memory is under 0.8 GB: MemFree of /proc/meminfo where it
exists (Linux, Git Bash), else Windows' available physical memory (what Git Bash's /proc/meminfo calls MemFree).
The renderer is Pillow only: it never starts a browser.
"""
import atexit
import ctypes
import json
import os
import signal
import socket
import sys
import time
from pathlib import Path

LOCK = Path(os.environ.get("ENTRELUMEN_RENDER_LOCK", "E:/Elias/Codex/Entrelumen-ssd/render.lock"))
OWNER = "owner.json"
MIN_FREE = int(0.8 * 2 ** 30)
POLL = 5.0
MAX_WAIT = 60 * 60
SLOTS = max(1, int(os.environ.get("ENTRELUMEN_RENDER_SLOTS", "2")))
YOUNG = 60.0   # a lock folder without owner.json may be a render writing it: wait this long before calling it stale


# --------------------------------------------------------------------------------------------------
# Memory

def mem_free():
    """Free memory in bytes (see the module docstring), or None when it cannot be read."""
    try:
        with open("/proc/meminfo", encoding="ascii") as f:
            for line in f:
                if line.startswith("MemFree:"):
                    return int(line.split()[1]) * 1024
    except OSError:
        pass
    if sys.platform == "win32":
        class Status(ctypes.Structure):
            _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong),
                        ("ullTotalPhys", ctypes.c_ulonglong), ("ullAvailPhys", ctypes.c_ulonglong),
                        ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                        ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong),
                        ("ullAvailExtendedVirtual", ctypes.c_ulonglong)]
        status = Status()
        status.dwLength = ctypes.sizeof(Status)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
            return int(status.ullAvailPhys)
    return None


def wait_for_memory(minimum=MIN_FREE, poll=POLL, max_wait=MAX_WAIT, read=mem_free, sleep=time.sleep,
                    clock=time.monotonic, log=print):
    """Return once free memory reaches the minimum (or cannot be read); TimeoutError after max_wait seconds."""
    start = clock()
    told = None
    while True:
        free = read()
        if free is None or free >= minimum:
            if told is not None:
                log(f"render: memory back to {free / 2 ** 30:.2f} GB free, going on")
            return free
        waited = clock() - start
        if waited >= max_wait:
            raise TimeoutError(f"render: free memory stayed under {minimum / 2 ** 30:.1f} GB for {waited / 60:.0f} min "
                               f"({free / 2 ** 30:.2f} GB now); try again later")
        if told is None or waited - told >= 60:
            log(f"render: {free / 2 ** 30:.2f} GB free, under {minimum / 2 ** 30:.1f} GB: waiting")
            told = waited
        sleep(poll)


# --------------------------------------------------------------------------------------------------
# Processes

def process_start(pid):
    """An opaque start stamp of a live process (to tell it from a later one with the same id), or None if there
    is no such process. On an unknown platform, 0 for a live process."""
    if sys.platform == "win32":
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.OpenProcess.restype = ctypes.c_void_p
        kernel.OpenProcess.argtypes = [ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
        kernel.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong)]
        kernel.GetProcessTimes.argtypes = [ctypes.c_void_p] + [ctypes.POINTER(ctypes.c_ulonglong)] * 4
        kernel.CloseHandle.argtypes = [ctypes.c_void_p]
        handle = kernel.OpenProcess(0x1000, False, int(pid))   # PROCESS_QUERY_LIMITED_INFORMATION
        if not handle:
            return 0 if ctypes.get_last_error() == 5 else None   # access denied: it exists, owned by someone else
        try:
            code = ctypes.c_ulong()
            if kernel.GetExitCodeProcess(handle, ctypes.byref(code)) and code.value != 259:   # STILL_ACTIVE
                return None
            times = [ctypes.c_ulonglong() for _ in range(4)]
            if kernel.GetProcessTimes(handle, *(ctypes.byref(t) for t in times)):
                return int(times[0].value)
            return 0
        finally:
            kernel.CloseHandle(handle)
    try:
        with open(f"/proc/{int(pid)}/stat", encoding="ascii") as f:
            return int(f.read().rsplit(")", 1)[1].split()[19])
    except OSError:
        pass
    try:
        os.kill(int(pid), 0)
        return 0
    except ProcessLookupError:
        return None
    except (PermissionError, OSError):
        return 0


def owner_alive(owner):
    started = process_start(owner.get("pid", -1))
    if started is None:
        return False
    recorded = owner.get("started")
    return not recorded or not started or recorded == started


# --------------------------------------------------------------------------------------------------
# The lock

class LockError(RuntimeError):
    pass


class RenderLock:
    def __init__(self, path=LOCK, max_wait=MAX_WAIT, poll=POLL, log=print, sleep=time.sleep, clock=time.monotonic,
                 slots=SLOTS):
        base = Path(path)
        self.slots = [base] + [base.with_name(f"{base.name}.{i}") for i in range(2, max(1, slots) + 1)]
        self.path = base
        self.max_wait, self.poll, self.log, self.sleep, self.clock = max_wait, poll, log, sleep, clock
        self.held = False
        self.owner = None

    # --- reading and clearing
    def read_owner(self):
        try:
            return json.loads((self.path / OWNER).read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return None

    def _guard(self):
        is_junction = getattr(self.path, "is_junction", lambda: False)
        if self.path.is_symlink() or is_junction():
            raise LockError(f"{self.path} is a link, not a lock folder: stop and ask")

    def stale(self):
        """Whether the lock on disk belongs to nobody alive (a folder without owner.json counts once it is old)."""
        owner = self.read_owner()
        if owner is None:
            try:
                age = time.time() - self.path.stat().st_mtime
            except OSError:
                return False   # gone meanwhile
            return age > YOUNG
        return not owner_alive(owner)

    def clear_stale(self, seen):
        """Remove a stale lock: its owner.json (only if it is still the one judged stale) and the empty folder."""
        self._guard()
        try:
            names = [p.name for p in self.path.iterdir()]
        except FileNotFoundError:
            return
        if set(names) - {OWNER}:
            raise LockError(f"{self.path} holds {', '.join(sorted(set(names) - {OWNER}))}: not a render lock; stop and ask")
        if OWNER in names:
            if self.read_owner() != seen:
                return   # someone else took it in between
            try:
                (self.path / OWNER).unlink()
            except FileNotFoundError:
                pass
        try:
            self.path.rmdir()
        except (FileNotFoundError, OSError):
            pass
        self.log(f"render: cleared a stale lock ({describe(seen)})")

    # --- taking and giving back
    def acquire(self):
        start = self.clock()
        told = False
        while True:
            for slot in self.slots:
                self.path = slot
                try:
                    os.mkdir(slot)
                except FileExistsError:
                    self._guard()
                    seen = self.read_owner()
                    if self.stale():
                        self.clear_stale(seen)
                        try:
                            os.mkdir(slot)
                        except FileExistsError:
                            continue
                    else:
                        continue
                return self._take()
            self.path = self.slots[0]
            seen = self.read_owner()
            waited = self.clock() - start
            if waited >= self.max_wait:
                raise TimeoutError(f"render: {len(self.slots)} render slot(s) still held after {waited / 60:.0f} min, "
                                   f"the first by {describe(seen)}")
            if not told:
                self.log(f"render: all {len(self.slots)} slot(s) busy, the first with {describe(seen)}: waiting")
                told = True
            self.sleep(self.poll)

    def _take(self):
        self.owner = {"pid": os.getpid(), "started": process_start(os.getpid()), "host": socket.gethostname(),
                      "cwd": os.getcwd(), "command": " ".join(sys.argv)[:300],
                      "since": time.strftime("%Y-%m-%d %H:%M:%S")}
        tmp = self.path / f"{OWNER}.tmp"
        tmp.write_text(json.dumps(self.owner), encoding="utf-8")
        os.replace(tmp, self.path / OWNER)
        self.held = True
        atexit.register(self.release)
        return self

    def release(self):
        if not self.held:
            return
        self.held = False
        owner = self.read_owner()
        if owner and owner.get("pid") == os.getpid():
            try:
                (self.path / OWNER).unlink()
                self.path.rmdir()
            except OSError:
                pass

    def __enter__(self):
        return self.acquire()

    def __exit__(self, *exc):
        self.release()
        return False


def describe(owner):
    if not owner:
        return "an unknown render"
    return f"pid {owner.get('pid')} in {owner.get('cwd', '?')} since {owner.get('since', '?')}"


def exit_on_signals():
    """SIGINT, SIGTERM and SIGBREAK become a normal exit, so context managers and atexit release the lock."""
    def stop(signum, _frame):
        raise SystemExit(128 + signum)
    for name in ("SIGINT", "SIGTERM", "SIGBREAK"):
        if hasattr(signal, name):
            try:
                signal.signal(getattr(signal, name), stop)
            except (ValueError, OSError):
                pass
