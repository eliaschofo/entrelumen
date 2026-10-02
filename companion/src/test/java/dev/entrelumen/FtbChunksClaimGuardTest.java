package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Claims over the shared sites (finding F5) and the block tags that keep them usable: FTB Chunks'
 * interaction whitelist and the dedicated-server spawn protection exemption (finding F4).
 */
class FtbChunksClaimGuardTest {
  /** The start ruin's registered box, from chunk (0, 0) to the middle of chunk (2, 1). */
  private static final ProtectionRules.Box RUIN = new ProtectionRules.Box(3, -64, 5, 40, 90, 20);

  @Test
  void claimsTouchingARegionOrASiteAreRefused() {
    var boxes = List.of(RUIN);
    var sites = List.of(new BlockPos(-17, 70, 300), new BlockPos(1000, 64, -1));
    assertTrue(FtbChunksClaimGuard.refuses(boxes, List.of(), 0, 0), "the ruin's first chunk");
    assertTrue(FtbChunksClaimGuard.refuses(boxes, List.of(), 2, 1), "the ruin's last chunk");
    assertFalse(FtbChunksClaimGuard.refuses(boxes, List.of(), 3, 1), "the chunk east of the ruin");
    assertFalse(FtbChunksClaimGuard.refuses(boxes, List.of(), -1, 0), "the chunk west of the ruin");
    assertFalse(FtbChunksClaimGuard.refuses(boxes, List.of(), 0, 2), "the chunk south of the ruin");
    // Any height counts: a claim is a whole column.
    var sky = List.of(new ProtectionRules.Box(100, 300, 100, 101, 310, 101));
    assertTrue(FtbChunksClaimGuard.refuses(sky, List.of(), 6, 6), "a box high above the ground");
    // Single-block sites (the heart, the gate, the Solsticio portal), negative coordinates included.
    assertTrue(FtbChunksClaimGuard.refuses(List.of(), sites, -2, 18), "the heart's chunk");
    assertTrue(FtbChunksClaimGuard.refuses(List.of(), sites, 62, -1), "the portal's chunk");
    assertFalse(FtbChunksClaimGuard.refuses(List.of(), sites, -1, 18), "beside the heart");
    assertFalse(FtbChunksClaimGuard.refuses(List.of(), sites, 62, 0), "beside the portal");
    assertFalse(FtbChunksClaimGuard.refuses(List.of(), List.of(), 0, 0), "nothing to protect");
  }

  @Test
  void theRefusalNamesATranslatedMessage() throws Exception {
    for (String lang : List.of("en_us", "es_es"))
      assertTrue(lang(lang).has(FtbChunksClaimGuard.REFUSED), lang + " lacks " + FtbChunksClaimGuard.REFUSED);
  }

  @Test
  void theFtbChunksWhitelistOnlyAddsSharedEntrelumenBlocks() throws Exception {
    var values = tag("/data/ftbchunks/tags/block/interact_whitelist.json");
    assertEquals(List.of("entrelumen:heliodor_pedestal", "entrelumen:enves_gate", "entrelumen:enves_seal",
        "entrelumen:solsticio_portal"), values);
    assertRegistered(values);
  }

  @Test
  void spawnProtectionExemptsOnlyRegisteredEntrelumenBlocks() throws Exception {
    var values = tag("/data/entrelumen/tags/block/spawn_protection_exempt.json");
    assertTrue(values.containsAll(List.of("entrelumen:heliodor_pedestal", "entrelumen:enves_gate",
        "entrelumen:enves_seal", "entrelumen:solsticio_portal")), values.toString());
    assertRegistered(values);
    var mixins = JsonParser.parseString(read("/entrelumen.common.mixins.json")).getAsJsonObject();
    assertTrue(mixins.getAsJsonArray("server").toString().contains("DedicatedServerSpawnProtectionMixin"),
        "the spawn protection mixin is not registered on the server side");
  }

  /** The tag's values, after checking it adds to the tag (never replaces it) and names no vanilla block. */
  private static List<String> tag(String resource) throws Exception {
    JsonObject tag = JsonParser.parseString(read(resource)).getAsJsonObject();
    assertFalse(tag.get("replace").getAsBoolean(), resource + " replaces the tag");
    List<String> values = new ArrayList<>();
    tag.getAsJsonArray("values").forEach(value -> values.add(value.getAsString()));
    for (String value : values) assertTrue(value.startsWith("entrelumen:"), resource + " lists " + value);
    return values;
  }

  /** Every id is registered by a {@code BLOCKS.register("…")} call in the companion's sources. */
  private static void assertRegistered(List<String> ids) throws Exception {
    var registered = new java.util.HashSet<String>();
    var call = Pattern.compile("BLOCKS\\.register\\(\\s*\"([a-z0-9_]+)\"");
    try (Stream<Path> sources = Files.list(Path.of("src", "main", "java", "dev", "entrelumen"))) {
      for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
        var matcher = call.matcher(Files.readString(source, StandardCharsets.UTF_8));
        while (matcher.find()) registered.add("entrelumen:" + matcher.group(1));
      }
    }
    for (String id : ids) assertTrue(registered.contains(id), id + " is not a registered block");
  }

  private static JsonObject lang(String code) throws Exception {
    return JsonParser.parseString(read("/assets/entrelumen/lang/" + code + ".json")).getAsJsonObject();
  }

  private static String read(String resource) throws Exception {
    var stream = FtbChunksClaimGuardTest.class.getResourceAsStream(resource);
    assertNotNull(stream, resource);
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      var out = new StringBuilder();
      char[] buffer = new char[8192];
      for (int n; (n = reader.read(buffer)) > 0; ) out.append(buffer, 0, n);
      return out.toString();
    }
  }
}
