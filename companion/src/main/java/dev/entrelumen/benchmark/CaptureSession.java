package dev.entrelumen.benchmark;

import com.google.gson.GsonBuilder;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Local opt-in session. No Minecraft classes: producer and persistence behavior is testable. */
public final class CaptureSession implements AutoCloseable {
  private record Sample(String stream, double elapsed, double value, String detail) {}

  public record Completion(boolean successful, String error) {}

  private final CompletableFuture<Completion> completion = new CompletableFuture<>();
  private final ArrayBlockingQueue<Sample> queue;
  private final long origin;
  private final Thread writer;
  private final ScheduledExecutorService memory;
  private final Set<String> reasons = ConcurrentHashMap.newKeySet();
  private volatile boolean closed;
  private volatile IOException failure;
  private long previousFrame = -1;
  private int previousTick = -1;
  public final Path directory;

  public CaptureSession(Path root, int capacity) throws IOException {
    directory = Files.createDirectory(root.resolve("capture-" + UUID.randomUUID()));
    origin = System.nanoTime();
    queue = new ArrayBlockingQueue<>(capacity);
    Files.writeString(
        directory.resolve("capture-integrity.json"),
        "{\"status\":\"recording_or_interrupted\",\"acceptance\":false}");
    Files.writeString(
        directory.resolve("session.json"),
        new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(
                Map.of(
                    "started_at",
                    Instant.now().toString(),
                    "collector",
                    "entrelumen-integrated-v1",
                    "clock",
                    "shared_process_nanoTime",
                    "acceptance",
                    false,
                    "metadata_required",
                    List.of(
                        "hardware", "preset", "world", "scenario", "players", "pack_revision"))));
    writer = new Thread(this::write, "entrelumen-capture-writer");
    writer.setDaemon(true);
    memory =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              var t = new Thread(r, "entrelumen-capture-memory");
              t.setDaemon(true);
              return t;
            });
    memory.scheduleAtFixedRate(this::sampleMemory, 0, 30, TimeUnit.SECONDS);
    writer.start();
  }

  public synchronized void frame(long now, boolean valid, String flags) {
    if (closed) return;
    if (!valid) {
      reasons.add("invalid_frame_state");
      previousFrame = -1;
    } else {
      if (previousFrame >= 0)
        offer(new Sample("frames", elapsed(now), (now - previousFrame) / 1e6, flags));
      previousFrame = now;
    }
    if (!valid) offer(new Sample("events", elapsed(now), 0, flags));
  }

  public synchronized void tick(long now, int counter, long duration, boolean valid) {
    if (closed) return;
    if (previousTick >= 0 && counter != previousTick + 1) reasons.add("tick_counter_gap");
    previousTick = counter;
    if (!valid) reasons.add("invalid_tick_state");
    offer(new Sample("ticks", elapsed(now), duration / 1e6, Integer.toString(counter)));
  }

  private double elapsed(long now) {
    return (now - origin) / 1e6;
  }

  private synchronized void offer(Sample sample) {
    if (closed) return;
    if (!queue.offer(sample)) {
      reasons.add("buffer_overflow");
      closed = true;
      memory.shutdownNow();
    }
  }

  private void sampleMemory() {
    try {
      var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
      offer(
          new Sample(
              "memory",
              elapsed(System.nanoTime()),
              heap.getUsed() / 1048576.0,
              heap.getCommitted() + ";" + heap.getMax()));
      for (var gc : ManagementFactory.getGarbageCollectorMXBeans())
        offer(
            new Sample(
                "gc",
                elapsed(System.nanoTime()),
                gc.getCollectionTime(),
                gc.getName() + ";count=" + gc.getCollectionCount()));
      for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
        var usage = pool.getCollectionUsage();
        if (usage != null)
          offer(
              new Sample(
                  "post_gc",
                  elapsed(System.nanoTime()),
                  usage.getUsed() / 1048576.0,
                  pool.getName()));
      }
    } catch (RuntimeException error) {
      invalidate("memory_sampler_failed");
    }
  }

  private void write() {
    Map<String, BufferedWriter> files = new HashMap<>();
    try {
      for (var stream : List.of("frames", "ticks", "memory", "events", "gc", "post_gc")) {
        var file =
            Files.newBufferedWriter(
                directory.resolve(stream + ".csv"), StandardOpenOption.CREATE_NEW);
        files.put(stream, file);
        file.write(
            "elapsed_ms,"
                + switch (stream) {
                  case "frames" -> "frame_ms";
                  case "ticks" -> "tick_ms";
                  case "memory" -> "heap_used_mb";
                  default -> "value";
                }
                + ",detail\n");
      }
      while (!closed || !queue.isEmpty()) {
        var sample = queue.poll(250, TimeUnit.MILLISECONDS);
        if (sample != null)
          files
              .get(sample.stream())
              .write(
                  sample.elapsed()
                      + ","
                      + sample.value()
                      + ",\""
                      + sample.detail().replace("\"", "\"\"")
                      + "\"\n");
      }
    } catch (IOException error) {
      failure = error;
      reasons.add("writer_io_failure");
    } catch (RuntimeException error) {
      failure = new IOException("Capture writer failed", error);
      reasons.add("writer_failure");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      reasons.add("writer_interrupted");
    } finally {
      closed = true;
      memory.shutdownNow();
      for (var file : files.values())
        try {
          file.close();
        } catch (IOException error) {
          failure = error;
          reasons.add("close_io_failure");
        }
      try {
        Files.writeString(
            directory.resolve("capture-integrity.json"),
            new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(
                    Map.of(
                        "status",
                        reasons.isEmpty() ? "closed_needs_review" : "invalid",
                        "acceptance",
                        false,
                        "reasons",
                        new TreeSet<>(reasons),
                        "clock_origin_verified",
                        true,
                        "pending_rows",
                        queue.size(),
                        "tick_scope",
                        "vanilla_tickServer",
                        "heap_unit",
                        "MiB",
                        "post_gc_note",
                        "Last collection usage per pool; not a timestamped GC event")));
      } catch (IOException error) {
        failure = error;
      }
      completion.complete(
          new Completion(
              failure == null && reasons.isEmpty(),
              failure != null ? failure.toString() : String.join(", ", new TreeSet<>(reasons))));
    }
  }

  public synchronized void invalidate(String reason) {
    if (!closed) reasons.add(reason);
  }

  public boolean isClosed() {
    return closed;
  }

  public IOException failure() {
    return failure;
  }

  public synchronized void close() {
    close(null);
  }

  /** First close request wins; lifecycle callbacks cannot change a stopped run afterward. */
  public synchronized void close(String reason) {
    if (closed) return;
    if (reason != null) reasons.add(reason);
    closed = true;
    memory.shutdownNow();
  }

  public CompletionStage<Completion> completion() {
    return completion.minimalCompletionStage();
  }

  public boolean awaitClosed(long milliseconds) throws InterruptedException {
    writer.join(milliseconds);
    return !writer.isAlive();
  }
}
