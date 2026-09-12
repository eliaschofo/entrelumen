package dev.entrelumen;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;

public final class ProjectReloadListener
    extends SimplePreparableReloadListener<Map<String, Projects.Project>> {
  public static final ResourceLocation RESOURCE =
      ResourceLocation.fromNamespaceAndPath("entrelumen", "campaign/projects.json");

  @Override
  protected Map<String, Projects.Project> prepare(
      ResourceManager manager, ProfilerFiller profiler) {
    Resource resource =
        manager
            .getResource(RESOURCE)
            .orElseThrow(
                () -> new IllegalStateException("Missing campaign definitions: " + RESOURCE));
    try (var reader = resource.openAsReader()) {
      return Projects.parse(JsonParser.parseReader(reader), BuiltInRegistries.ITEM::containsKey);
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException(
          "Rejected "
              + RESOURCE
              + " from datapack "
              + resource.sourcePackId()
              + "; previous campaign definitions retained. "
              + e.getMessage(),
          e);
    }
  }

  @Override
  protected void apply(
      Map<String, Projects.Project> definitions, ResourceManager manager, ProfilerFiller profiler) {
    Projects.publish(definitions);
    LogUtils.getLogger()
        .info("Loaded {} validated Entrelumen campaign projects", definitions.size());
  }
}
