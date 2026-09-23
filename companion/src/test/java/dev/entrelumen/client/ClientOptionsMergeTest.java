package dev.entrelumen.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientOptionsMergeTest {
  @TempDir Path folder;

  private Path write(String name, String content) throws Exception {
    Path path = folder.resolve(name);
    Files.writeString(path, content, StandardCharsets.UTF_8);
    return path;
  }

  private static String curseForgeLike473Bytes() {
    String prefix = "version:1519\r\nrenderDistance:8\r\nguiScale:2\r\nfullscreen:false\r\n"
        + "soundCategory_music:0.5\r\nkey_key.sneak:key.keyboard.left.shift\r\n"
        + "key_key.sprint:key.keyboard.left.control\r\nlang:es_es\r\n"
        + "resourcePacks:[\"vanilla\",\"file/personal\"]\r\n";
    String key = "launcherOption:";
    int fill = 473 - prefix.getBytes(StandardCharsets.UTF_8).length - key.length();
    assertTrue(fill > 0);
    return prefix + key + "x".repeat(fill); // no terminal newline
  }

  @Test
  void addsOnlyMissingOptionsAndPreservesCurseForgePrefixByteForByte() throws Exception {
    byte[] original = curseForgeLike473Bytes().getBytes(StandardCharsets.UTF_8);
    assertEquals(473, original.length);
    Path options = folder.resolve("options.txt");
    Files.write(options, original);
    Path defaults = write("defaults.txt", "version:3955\nrenderDistance:10\nguiScale:3\n"
        + "fullscreen:false\nresourcePacks:[\"vanilla\",\"file/entrelumen\"]\n"
        + "simulationDistance:6\nmaxFps:120\n");

    assertEquals(2, ClientOptionsMerge.appendMissing(options, defaults));
    byte[] merged = Files.readAllBytes(options);
    assertArrayEquals(original, Arrays.copyOf(merged, original.length));
    String text = new String(merged, StandardCharsets.UTF_8);
    assertTrue(text.endsWith("\r\nsimulationDistance:6\r\nmaxFps:120\r\n"));
    assertTrue(text.contains("renderDistance:8\r\n"));
    assertTrue(text.contains("guiScale:2\r\n"));
    assertTrue(text.contains("lang:es_es\r\n"));
    assertTrue(text.contains("resourcePacks:[\"vanilla\",\"file/personal\"]\r\n"));
    assertTrue(text.contains("key_key.sneak:key.keyboard.left.shift\r\n"));
    assertEquals(0, ClientOptionsMerge.appendMissing(options, defaults));
    assertArrayEquals(merged, Files.readAllBytes(options));
  }

  @Test
  void preservesCrLfWhenExistingFileEndsWithNewline() throws Exception {
    Path options = write("options.txt", "version:1519\r\nrenderDistance:8\r\n");
    Path defaults = write("defaults.txt", "renderDistance:10\nsimulationDistance:6\n");
    assertEquals(1, ClientOptionsMerge.appendMissing(options, defaults));
    assertEquals("version:1519\r\nrenderDistance:8\r\nsimulationDistance:6\r\n",
        Files.readString(options));
  }

  @Test
  void leavesMissingOptionsFileForNativeDefaultOptionsHandler() throws Exception {
    Path options = folder.resolve("options.txt");
    Path defaults = write("defaults.txt", "renderDistance:10\n");
    assertEquals(0, ClientOptionsMerge.appendMissing(options, defaults));
    assertFalse(Files.exists(options));
  }

  @Test
  void rejectsMalformedAndDuplicateFragmentsBeforeWriting() throws Exception {
    Path options = write("options.txt", "renderDistance:8\n");
    byte[] original = Files.readAllBytes(options);
    Path defaults = write("defaults.txt", "simulationDistance:6\nbad line\n");
    assertThrows(IllegalArgumentException.class,
        () -> ClientOptionsMerge.appendMissing(options, defaults));
    assertArrayEquals(original, Files.readAllBytes(options));
    Files.writeString(defaults, "simulationDistance:6\nsimulationDistance:7\n");
    assertThrows(IllegalArgumentException.class,
        () -> ClientOptionsMerge.appendMissing(options, defaults));
    assertArrayEquals(original, Files.readAllBytes(options));
    Files.writeString(defaults, "key_key.sneak:key.keyboard.v\n");
    assertThrows(IllegalArgumentException.class,
        () -> ClientOptionsMerge.appendMissing(options, defaults));
    assertArrayEquals(original, Files.readAllBytes(options));
  }
}
