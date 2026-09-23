package dev.entrelumen.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds absent general options before Minecraft parses options.txt; never rewrites existing lines. */
final class ClientOptionsMerge {
  private ClientOptionsMerge() {}

  static int appendMissing(Path existing, Path defaults) throws IOException {
    if (!Files.isRegularFile(existing) || Files.isSymbolicLink(existing)
        || !Files.isRegularFile(defaults)) return 0;

    // Parse the entire packaged fragment first; malformed or duplicate defaults never touch a profile.
    Map<String, String> packaged = new LinkedHashMap<>();
    for (String line : decode(Files.readAllBytes(defaults)).split("\\R", -1)) {
      if (line.isEmpty()) continue;
      int colon = line.indexOf(':');
      if (colon < 1) throw new IllegalArgumentException("Malformed packaged option");
      String key = line.substring(0, colon);
      if (!key.matches("[A-Za-z0-9_.-]+") || key.equals("lang") || key.startsWith("key_"))
        throw new IllegalArgumentException("Invalid packaged option key");
      if (packaged.putIfAbsent(key, line) != null)
        throw new IllegalArgumentException("Duplicate packaged option key");
    }
    if (packaged.isEmpty()) throw new IllegalArgumentException("Empty packaged options fragment");

    byte[] original = Files.readAllBytes(existing);
    String originalText = decode(original);
    for (String line : originalText.split("\\R", -1)) {
      int colon = line.indexOf(':');
      if (colon > 0) packaged.remove(line.substring(0, colon));
    }
    if (packaged.isEmpty()) return 0;

    String newline = originalText.contains("\r\n") ? "\r\n" : "\n";
    ByteArrayOutputStream merged = new ByteArrayOutputStream();
    merged.write(original);
    if (original.length > 0 && original[original.length - 1] != '\n'
        && original[original.length - 1] != '\r') merged.write(newline.getBytes(StandardCharsets.UTF_8));
    for (String line : packaged.values()) {
      merged.write(line.getBytes(StandardCharsets.UTF_8));
      merged.write(newline.getBytes(StandardCharsets.UTF_8));
    }

    Path temporary = Files.createTempFile(existing.getParent(), ".entrelumen-options-", ".tmp");
    try {
      Files.write(temporary, merged.toByteArray());
      // Another writer must not be overwritten between our read and the atomic replacement.
      if (!java.util.Arrays.equals(original, Files.readAllBytes(existing)))
        throw new IOException("Options changed during merge");
      Files.move(temporary, existing, StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return packaged.size();
  }

  private static String decode(byte[] bytes) throws IOException {
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes)).toString();
    } catch (java.nio.charset.CharacterCodingException error) {
      throw new IOException("Options are not valid UTF-8", error);
    }
  }
}
