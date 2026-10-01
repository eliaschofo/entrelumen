package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.Event;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Keeps FTB Chunks claims off the shared ENTRELUMEN sites. A claim by any team cancels every
 * right-click of the others inside it, so one player could lock everybody out of the start ruin's
 * pedestal and Sealed Stair, an act ruin, the Envés or the Solsticio portal. A claim whose chunk
 * touches a {@link StructureProtection} region (the start ruin, the act ruins, the Envés dimension,
 * Solsticio and its Overworld rift), the Sealed Stair's heart, seals, gate or antechamber, or the
 * Overworld Solsticio portal is refused with {@link #REFUSED}.
 *
 * <p>FTB Chunks is optional and not on the compile classpath: the listener is bound to its
 * {@code ClaimedChunkEvent.BEFORE_CLAIM} (ftbchunks 2101.1.21) by reflection, only when it is loaded.
 */
public final class FtbChunksClaimGuard {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final String MOD_ID = "ftbchunks";
  /** Translation key FTB Chunks shows for the refused claim. */
  public static final String REFUSED = "entrelumen.claim.protected";
  static final String EVENTS = "dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent";
  static final String BEFORE = "dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent$Before";
  static final String CLAIMED_CHUNK = "dev.ftb.mods.ftbchunks.api.ClaimedChunk";
  static final String CLAIM_RESULT = "dev.ftb.mods.ftbchunks.api.ClaimResult";

  /** The registered {@code ClaimedChunkEvent.Before} proxy, or null when FTB Chunks is absent. */
  @Nullable static volatile Object listener;

  private FtbChunksClaimGuard() {}

  static void register() {
    if (!ModList.get().isLoaded(MOD_ID)) return;
    try {
      Class<?> before = Class.forName(BEFORE);
      Method getPos = Class.forName(CLAIMED_CHUNK).getMethod("getPos");
      Object refusal = Class.forName(CLAIM_RESULT).getMethod("customProblem", String.class).invoke(null, REFUSED);
      InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
        case "before" -> {
          var source = (CommandSourceStack) args[0];
          var pos = (ChunkDimPos) getPos.invoke(args[1]);
          yield refuses(source.getServer(), pos.dimension(), pos.x(), pos.z())
              ? CompoundEventResult.interruptFalse(refusal) : CompoundEventResult.pass();
        }
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "ENTRELUMEN claim guard";
        default -> throw new UnsupportedOperationException(method.getName());
      };
      Object guard = Proxy.newProxyInstance(before.getClassLoader(), new Class<?>[] {before}, handler);
      beforeClaim().register(guard);
      listener = guard;
      LOGGER.info("FTB Chunks claims are refused over the ENTRELUMEN shared sites");
    } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
      LOGGER.error("FTB Chunks is loaded but its claim event was not found; claims over the shared sites"
          + " are not refused", e);
    }
  }

  @SuppressWarnings("unchecked")
  static Event<Object> beforeClaim() throws ReflectiveOperationException {
    return (Event<Object>) Class.forName(EVENTS).getField("BEFORE_CLAIM").get(null);
  }

  /** Whether a claim of chunk ({@code cx}, {@code cz}) in {@code dimension} must be refused. */
  public static boolean refuses(MinecraftServer server, ResourceKey<Level> dimension, int cx, int cz) {
    ServerLevel level = server.getLevel(dimension);
    if (level == null) return false;
    List<ProtectionRules.Box> boxes = new ArrayList<>();
    for (var region : StructureProtection.regions(level)) boxes.add(region.box());
    return refuses(boxes, sites(server, dimension), cx, cz);
  }

  /** The single-block sites of {@code dimension} no claim may cover. */
  static List<BlockPos> sites(MinecraftServer server, ResourceKey<Level> dimension) {
    List<BlockPos> sites = new ArrayList<>();
    if (!dimension.equals(Level.OVERWORLD)) return sites;
    var entrance = EnvesData.get(server).entrance;
    if (entrance.heart != null) sites.add(entrance.heart);
    sites.addAll(entrance.sealCells);
    sites.addAll(entrance.gate);
    if (entrance.antechamber != null) sites.add(entrance.antechamber);
    var portal = SolsticioData.get(server).overworldPortal;
    if (portal != null) sites.add(portal);
    return sites;
  }

  /** Whether the chunk's column meets any box or holds any site. */
  static boolean refuses(Collection<ProtectionRules.Box> boxes, Collection<BlockPos> sites, int cx, int cz) {
    var column = new ProtectionRules.Box(cx << 4, Integer.MIN_VALUE, cz << 4, (cx << 4) + 15, Integer.MAX_VALUE,
        (cz << 4) + 15);
    for (var box : boxes) if (box.intersects(column)) return true;
    for (BlockPos site : sites) if (site.getX() >> 4 == cx && site.getZ() >> 4 == cz) return true;
    return false;
  }
}
