package dev.entrelumen;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Loads {@code data/entrelumen/ark_multiblock.json} on every reload, so a datapack may replace the
 * Ark's shape. A rejected file keeps the previous definition; the client's guide always draws the one
 * inside the mod.
 */
public final class ArkMultiblockReloadListener extends SimplePreparableReloadListener<ArkMultiblock.Definition> {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath("entrelumen", "ark_multiblock.json");

  @Override
  protected ArkMultiblock.Definition prepare(ResourceManager manager, ProfilerFiller profiler) {
    var resource = manager.getResource(RESOURCE);
    if (resource.isEmpty()) return ArkMultiblock.builtin();
    try (var reader = resource.get().openAsReader()) {
      return ArkMultiblock.parse(JsonParser.parseReader(reader));
    } catch (Exception e) {
      LOGGER.error("Rejected {} from datapack {}; the previous Ark shape is kept. {}", RESOURCE,
          resource.get().sourcePackId(), e.getMessage());
      return ArkMultiblock.definition();
    }
  }

  @Override
  protected void apply(ArkMultiblock.Definition definition, ResourceManager manager, ProfilerFiller profiler) {
    ArkMultiblock.publish(definition);
    LOGGER.info("Ark multiblock: {} parts, {} required, {} beacon places", definition.parts().size(),
        definition.required().size(), definition.beacons().size());
  }
}
