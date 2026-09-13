package dev.entrelumen.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaptureSessionTest {
  @TempDir Path root;

  @Test
  void consecutiveFramesKeepTheirDeltaAndCloseDrains() throws Exception {
    var session = new CaptureSession(root, 128);
    long start = System.nanoTime();
    session.frame(start, true, "valid");
    session.frame(start + 10_000_000, true, "valid");
    session.frame(start + 35_000_000, true, "valid");
    session.close();
    assertTrue(session.awaitClosed(5000));
    var rows = Files.readAllLines(session.directory.resolve("frames.csv"));
    assertEquals(3, rows.size());
    assertEquals(10.0, Double.parseDouble(rows.get(1).split(",")[1]));
    assertEquals(25.0, Double.parseDouble(rows.get(2).split(",")[1]));
    session.frame(start + 50_000_000, true, "valid");
    assertEquals(rows, Files.readAllLines(session.directory.resolve("frames.csv")));
    assertNull(session.failure());
  }

  @Test
  void invalidStateBreaksPairAndCannotBecomeAcceptance() throws Exception {
    var session = new CaptureSession(root, 128);
    long start = System.nanoTime();
    session.frame(start, true, "valid");
    session.frame(start + 10_000_000, false, "unfocused");
    session.frame(start + 20_000_000, true, "valid");
    session.frame(start + 30_000_000, true, "valid");
    session.tick(start, 1, 12_000_000, true);
    session.tick(start + 50_000_000, 3, 13_000_000, true);
    session.close();
    assertTrue(session.awaitClosed(5000));
    assertEquals(2, Files.readAllLines(session.directory.resolve("frames.csv")).size());
    String integrity = Files.readString(session.directory.resolve("capture-integrity.json"));
    assertTrue(integrity.contains("invalid_frame_state"));
    assertTrue(integrity.contains("tick_counter_gap"));
    assertTrue(integrity.contains("\"acceptance\": false"));
  }

  @Test
  void boundedBufferStopsInsteadOfSilentlyLosingRows() throws Exception {
    var session = new CaptureSession(root, 1);
    long start = System.nanoTime();
    for (int i = 0; i < 100000 && !session.isClosed(); i++)
      session.frame(start + i * 1000000L, true, "valid");
    assertTrue(session.isClosed());
    assertTrue(session.awaitClosed(5000));
    assertTrue(
        Files.readString(session.directory.resolve("capture-integrity.json"))
            .contains("buffer_overflow"));
  }

  @Test
  void completionWaitsForArtifactsAndConcurrentCloseIsIdempotent() throws Exception {
    var session = new CaptureSession(root, 128);
    var future = session.completion().toCompletableFuture();
    assertFalse(future.isDone());
    long now = System.nanoTime();
    session.frame(now, true, "valid");
    session.frame(now + 10000000, true, "valid");
    session.close();
    var callers =
        java.util.stream.IntStream.range(0, 8)
            .mapToObj(i -> new Thread(() -> session.close("disconnected")))
            .toList();
    callers.forEach(Thread::start);
    for (var caller : callers) caller.join();
    var result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
    assertTrue(result.successful());
    assertEquals(2, Files.readAllLines(session.directory.resolve("frames.csv")).size());
    String integrity = Files.readString(session.directory.resolve("capture-integrity.json"));
    assertTrue(integrity.contains("closed_needs_review"));
    assertFalse(integrity.contains("disconnected"));
  }

  @Test
  void failedIntegrityWriteCompletesWithErrorNotSuccess() throws Exception {
    var session = new CaptureSession(root, 128);
    Path integrity = session.directory.resolve("capture-integrity.json");
    Files.move(integrity, session.directory.resolve("initial-integrity.json"));
    Files.createDirectory(integrity);
    session.close();
    var result =
        session.completion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
    assertFalse(result.successful());
    assertFalse(result.error().isBlank());
    assertNotNull(session.failure());
  }
}
