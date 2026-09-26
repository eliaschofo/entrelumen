package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

  /** The warp-requirement condition the pack's Waystones config uses for the Ark's Traveller. */
  static final ResourceLocation TRAVELLER = ResourceLocation.fromNamespaceAndPath("entrelumen", "ark_traveller");

  /**
   * The Exploration module makes warps free of experience, through two public Waystones 21.1.41
   * hooks read in its JAR on 25 September 2026:
   *
   * <ul>
   *   <li>{@code WaystonesAPI.registerConditionPredicate(ConditionResolver)}: the condition
   *       {@code entrelumen:ark_traveller}, true for a player with the Traveller's speed bonus (a synced
   *       attribute, so the client's warp list agrees). {@code pack/config/waystones-common.toml}
   *       ends the default {@code warpRequirements} with {@code [entrelumen:ark_traveller]
   *       multiply_xp_cost(0)}, so the list shows no cost and does not grey out the destinations.
   *   <li>The Balm event {@code WaystoneTeleportEvent.Pre}: on the server, a traveller's experience
   *       requirements ({@code ExperienceLevelRequirement.setLevels}, {@code
   *       ExperiencePointsRequirement.setPoints}, inside {@code CombinedRequirement}) drop to zero even
   *       if a local config lacks that line. Cooldowns and item costs stay.
   * </ul>
   *
   * Any failure leaves Waystones as it is.
   */
  static void registerTraveller() {
    if (!available()) return;
    try {
      Class<?> resolverType = Class.forName(API + "requirement.ConditionResolver");
      Class<?> contextType = Class.forName(API + "WaystoneTeleportContext");
      Class<?> noParameter = Class.forName("net.blay09.mods.waystones.requirement.RequirementRegistry$NoParameter");
      Method entity = contextType.getMethod("getEntity");
      Object resolver = Proxy.newProxyInstance(resolverType.getClassLoader(), new Class<?>[] {resolverType},
          (proxy, method, args) -> switch (method.getName()) {
            case "getId" -> TRAVELLER;
            case "getParameterType" -> noParameter;
            case "matches" -> entity.invoke(args[0]) instanceof Player player && ArkEffects.traveller(player);
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> TRAVELLER.toString();
            default -> throw new UnsupportedOperationException(method.getName());
          });
      Class.forName(API + "WaystonesAPI").getMethod("registerConditionPredicate", resolverType).invoke(null, resolver);

      Class<?> pre = Class.forName(API + "event.WaystoneTeleportEvent$Pre");
      Method context = pre.getMethod("getContext");
      Method requirements = pre.getMethod("getRequirements");
      Free free = free();
      Consumer<Object> handler = event -> {
        try {
          if (entity.invoke(context.invoke(event)) instanceof ServerPlayer player
              && ArkState.status(player).active(ArkRules.Module.EXPLORATION))
            free.apply(requirements.invoke(event));
        } catch (ReflectiveOperationException | RuntimeException failure) {
          LOGGER.debug("Could not waive a traveller's warp cost", failure);
        }
      };
      Object events = Class.forName("net.blay09.mods.balm.api.Balm").getMethod("getEvents").invoke(null);
      Class.forName("net.blay09.mods.balm.api.event.BalmEvents").getMethod("onEvent", Class.class, Consumer.class)
          .invoke(events, pre, handler);
      LOGGER.info("Waystones: the Ark's Traveller warps without experience");
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.warn("Waystones is loaded but its warp requirements could not be hooked; warps keep their cost", failure);
    }
  }

  private static Free free() throws ClassNotFoundException {
    return new Free(Class.forName("net.blay09.mods.waystones.requirement.CombinedRequirement"),
        Class.forName("net.blay09.mods.waystones.requirement.ExperienceLevelRequirement"),
        Class.forName("net.blay09.mods.waystones.requirement.ExperiencePointsRequirement"));
  }

  /** What the Pre hook does to a traveller's requirements (full-pack QA). */
  static void waive(Object requirement) throws ReflectiveOperationException {
    free().apply(requirement);
  }

  /** Sets every experience requirement inside a requirement to zero. */
  private record Free(Class<?> combined, Class<?> levels, Class<?> points) {
    void apply(Object requirement) throws ReflectiveOperationException {
      if (requirement == null) return;
      if (combined.isInstance(requirement)) {
        for (Object inner : (Collection<?>) combined.getMethod("getRequirements").invoke(requirement)) apply(inner);
      } else if (levels.isInstance(requirement)) {
        levels.getMethod("setLevels", int.class).invoke(requirement, 0);
      } else if (points.isInstance(requirement)) {
        points.getMethod("setPoints", int.class).invoke(requirement, 0);
      }
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
