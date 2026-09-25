package dev.entrelumen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.fml.ModList;

/**
 * Reads FTB Ultimine 2101.1.15 without linking against it. Ultimine's own limit for a player is
 * {@code max(0, max_blocks + round(ftbultimine:max_blocks_modifier))}, where {@code max_blocks} comes from
 * {@code ftbultimine-server.snbt} (or an FTB Ranks node when FTB Ranks is installed, which the pack does
 * not ship). A limit of zero selects no blocks, so Ultimine does nothing. The attribute is found in the
 * registry; the configured value and the effective limit are read through two public members,
 * {@code FTBUltimineServerConfig.MAX_BLOCKS} and {@code FTBUltimineServerConfig.getMaxBlocks(ServerPlayer)}.
 */
public final class UltimineCompat {
  public static final String MOD_ID = "ftbultimine";
  public static final ResourceLocation MAX_BLOCKS_MODIFIER =
      ResourceLocation.parse(VeinResonatorRules.ULTIMINE_ATTRIBUTE_ID);

  private UltimineCompat() {}

  private record Handles(Object maxBlocksValue, MethodHandle valueGet, MethodHandle maxBlocks) {
    static final Handles INSTANCE = resolve();

    private static Handles resolve() {
      if (!loaded()) return null;
      try {
        Class<?> config = Class.forName("dev.ftb.mods.ftbultimine.config.FTBUltimineServerConfig");
        Object value = config.getField("MAX_BLOCKS").get(null);
        var lookup = MethodHandles.publicLookup();
        MethodHandle get = lookup.findVirtual(value.getClass(), "get", MethodType.methodType(Object.class));
        MethodHandle limit = lookup.findStatic(config, "getMaxBlocks",
            MethodType.methodType(int.class, ServerPlayer.class));
        return new Handles(value, get, limit);
      } catch (Throwable unavailable) {
        com.mojang.logging.LogUtils.getLogger().warn("ENTRELUMEN FTB Ultimine API unavailable: {}", unavailable.toString());
        return null;
      }
    }
  }

  public static boolean loaded() {
    var mods = ModList.get();
    return mods != null && mods.isLoaded(MOD_ID);
  }

  /** FTB Ultimine's per-player block limit modifier, when Ultimine registered it. */
  public static Holder<Attribute> maxBlocksAttribute() {
    return BuiltInRegistries.ATTRIBUTE.getHolder(MAX_BLOCKS_MODIFIER).<Holder<Attribute>>map(h -> h).orElse(null);
  }

  /**
   * The configured {@code max_blocks}. The pack ships 0; a server that raises it locally is corrected by
   * the resonator's modifier, which always aims at the resonator's own reach. 0 without Ultimine.
   */
  public static int configuredMaxBlocks() {
    var handles = Handles.INSTANCE;
    if (handles == null) return 0;
    try {
      return ((Number) handles.valueGet.invoke(handles.maxBlocksValue)).intValue();
    } catch (Throwable error) {
      return 0;
    }
  }

  /** Ultimine's effective limit for this player, as its block selection reads it; -1 without Ultimine. */
  public static int effectiveMaxBlocks(ServerPlayer player) {
    var handles = Handles.INSTANCE;
    if (handles == null) return -1;
    try {
      return (int) handles.maxBlocks.invoke(player);
    } catch (Throwable error) {
      return -1;
    }
  }
}
