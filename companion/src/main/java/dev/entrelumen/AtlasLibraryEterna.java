package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Eterna around a block, read exactly as an enchanting table at that position would.
 *
 * <p>With Apothic Enchanting loaded, its public {@code EnchantmentTableStats.gatherStats} gives
 * the table's Eterna (shelf stats, per-shelf maxima, transmitter checks). It is resolved once by
 * reflection so the companion never links against Apothic classes. Without it, or if the call
 * fails, the vanilla/NeoForge rule applies: two Eterna per point of
 * {@code getEnchantPowerBonus}, at most 30 (Apothic's own fallback for unknown blocks).
 */
public final class AtlasLibraryEterna {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final float VANILLA_MAX = 30f;
  private static final MethodHandle GATHER, TABLE_ETERNA;
  private static boolean warned;

  static {
    MethodHandle gather = null, eterna = null;
    if (ModList.get() != null && ModList.get().isLoaded("apothic_enchanting")) {
      try {
        Class<?> stats = Class.forName("dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats");
        var lookup = MethodHandles.publicLookup();
        gather = lookup.findStatic(stats, "gatherStats",
            MethodType.methodType(stats, LevelReader.class, BlockPos.class));
        eterna = lookup.findVirtual(stats, "tableEterna", MethodType.methodType(float.class));
      } catch (ReflectiveOperationException | LinkageError error) {
        LOGGER.warn("Atlas Library could not read Apothic Enchanting stats; using the vanilla shelf rule", error);
        gather = null;
        eterna = null;
      }
    }
    GATHER = gather;
    TABLE_ETERNA = eterna;
  }

  private AtlasLibraryEterna() {}

  public static boolean usesApothic() {
    return GATHER != null;
  }

  /** Recomputed on every call; the library never caches it. */
  public static float around(Level level, BlockPos pos) {
    if (GATHER != null) {
      try {
        Object stats = GATHER.invoke((LevelReader) level, pos);
        return Math.max(0f, (float) TABLE_ETERNA.invoke(stats));
      } catch (Throwable error) {
        if (!warned) {
          warned = true;
          LOGGER.warn("Apothic Enchanting stat lookup failed; using the vanilla shelf rule", error);
        }
      }
    }
    return vanilla(level, pos);
  }

  public static float vanilla(Level level, BlockPos pos) {
    float power = 0;
    for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
      if (!level.hasChunkAt(pos.offset(offset))) continue;
      if (EnchantingTableBlock.isValidBookShelf(level, pos, offset))
        power += level.getBlockState(pos.offset(offset)).getEnchantPowerBonus(level, pos.offset(offset));
    }
    return Math.min(VANILLA_MAX, power * 2f);
  }
}
