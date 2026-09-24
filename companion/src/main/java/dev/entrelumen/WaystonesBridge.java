package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Optional link to the pinned Waystones 21.1.41 public API ({@code net.blay09.mods.waystones.api}),
 * reached by reflection so the companion neither compiles against nor bundles that
 * all-rights-reserved JAR. Used: {@code WaystonesAPI.placeWaystone(Level, BlockPos, WaystoneStyle)},
 * {@code WaystoneStyles.END_STONE} and {@code MutableWaystone.setName/setVisibility(GLOBAL)}.
 * Any failure falls back to Entrelumen's own paired portal.
 */
final class WaystonesBridge {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String API = "net.blay09.mods.waystones.api.";

  private WaystonesBridge() {}

  static boolean available() {
    return ModList.get() != null && ModList.get().isLoaded("waystones");
  }

  /** Places a global, named waystone (two blocks tall) at the spot; true when it exists after. */
  static boolean placeGlobalWaystone(ServerLevel level, BlockPos pos, Component name) {
    try {
      Class<?> api = Class.forName(API + "WaystonesAPI");
      Class<?> styleClass = Class.forName(API + "WaystoneStyle");
      Object style = Class.forName(API + "WaystoneStyles").getField("END_STONE").get(null);
      Method place = api.getMethod("placeWaystone", Level.class, BlockPos.class, styleClass);
      Object result = place.invoke(null, level, pos, style);
      if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return false;
      Object waystone = optional.get();
      Class<?> mutable = Class.forName(API + "MutableWaystone");
      if (mutable.isInstance(waystone)) {
        mutable.getMethod("setName", Component.class).invoke(waystone, name);
        Class<?> visibility = Class.forName(API + "WaystoneVisibility");
        Object global = visibility.getField("GLOBAL").get(null);
        mutable.getMethod("setVisibility", visibility).invoke(waystone, global);
      }
      markDirty(level, waystone);
      LOGGER.info("Registered the Solsticio portal as a global waystone at {}", pos);
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.warn("Waystones is loaded but its API could not register the Solsticio waystone; using the paired portal", failure);
      return false;
    }
  }

  /** Persists name and visibility; internal class, so any failure only costs a later save. */
  private static void markDirty(ServerLevel level, Object waystone) {
    try {
      Class<?> manager = Class.forName("net.blay09.mods.waystones.core.WaystoneManagerImpl");
      Object instance = manager.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, level.getServer());
      manager.getMethod("updateWaystone", Class.forName(API + "Waystone")).invoke(instance, waystone);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.debug("Could not mark the waystone database dirty", failure);
    }
  }
}
