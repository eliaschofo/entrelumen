package dev.entrelumen.client;

import com.mojang.logging.LogUtils;
import dev.entrelumen.ArkMultiblock;
import dev.entrelumen.AtlasNetwork;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * The Atlas's Ark guide: the Ark projected as a 3D ghost where it is (the team's controller) or where
 * it will be (the block the player looks at, facing the player). A toggle.
 *
 * <p>With Patchouli (pinned 1.21.1-93) the ghost is Patchouli's own multiblock view, reached by
 * reflection through its public API ({@code PatchouliAPI.get()}: {@code makeSparseMultiblock},
 * {@code predicateMatcher}, {@code showMultiblock}, {@code clearMultiblock}), so the companion neither
 * compiles against nor ships it. It shows every required block in one colour and counts what is
 * placed. Without Patchouli, dust particles mark each required block still missing.
 */
@EventBusSubscriber(modid = "entrelumen", value = Dist.CLIENT)
public final class ArkGuideClient {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("entrelumen", "ark");
  private static final DustParticleOptions CORE = new DustParticleOptions(new Vector3f(0.95f, 0.78f, 0.35f), 1.2f);
  private static final DustParticleOptions SLOT = new DustParticleOptions(new Vector3f(0.45f, 0.85f, 1.0f), 1.6f);
  private static ArkMultiblock.Anchor anchor;
  private static ResourceKey<Level> dimension;
  private static boolean patchouliShown;

  private ArkGuideClient() {}

  public static boolean shown() {
    return anchor != null;
  }

  /** Shows the guide, or hides it when it is shown. Returns whether it is now shown. */
  public static boolean toggle(AtlasNetwork.ArkView ark) {
    var minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null) return false;
    if (shown()) {
      hide();
      minecraft.player.displayClientMessage(Component.translatable("entrelumen.ark.guide.hidden"), true);
      return false;
    }
    var definition = ArkMultiblock.builtin();
    ArkMultiblock.Anchor at = null;
    if (ark.registered() && ark.dimension().equals(minecraft.level.dimension().location().toString()))
      at = new ArkMultiblock.Anchor(ark.origin(), ark.rotation());
    else if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
      at = new ArkMultiblock.Anchor(hit.getBlockPos().above(1 - definition.minY()), facing(minecraft.player.getDirection()));
    if (at == null) {
      minecraft.player.displayClientMessage(Component.translatable("entrelumen.ark.guide.look"), true);
      return false;
    }
    anchor = at;
    dimension = minecraft.level.dimension();
    patchouliShown = showPatchouli(definition, at);
    minecraft.player.displayClientMessage(Component.translatable(ark.registered()
        ? "entrelumen.ark.guide.shown_ark" : "entrelumen.ark.guide.shown_here"), true);
    return true;
  }

  /** The facing a player looking this way builds: the drawings face north. */
  static int facing(Direction direction) {
    return switch (direction) {
      case EAST -> 1;
      case SOUTH -> 2;
      case WEST -> 3;
      default -> 0;
    };
  }

  static void hide() {
    if (patchouliShown) clearPatchouli();
    patchouliShown = false;
    anchor = null;
    dimension = null;
  }

  // ---- Patchouli ---------------------------------------------------------------------------------

  private static Object api() throws ReflectiveOperationException {
    return Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
  }

  private static Class<?> apiType() throws ClassNotFoundException {
    return Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");
  }

  private static boolean showPatchouli(ArkMultiblock.Definition definition, ArkMultiblock.Anchor at) {
    if (!ModList.get().isLoaded("patchouli")) return false;
    try {
      Object api = api();
      Class<?> type = apiType();
      Method matcher = type.getMethod("predicateMatcher", BlockState.class, Predicate.class);
      Map<BlockPos, Object> parts = new LinkedHashMap<>();
      for (ArkMultiblock.Part part : definition.required()) {
        BlockState display = display(part);
        if (display == null) continue;
        Predicate<BlockState> accepts = state -> matches(part, state, at.rotation());
        parts.put(part.offset(), matcher.invoke(api, display, accepts));
      }
      Object multiblock = type.getMethod("makeSparseMultiblock", Map.class).invoke(api, parts);
      Class<?> multiblockType = Class.forName("vazkii.patchouli.api.IMultiblock");
      multiblockType.getMethod("setId", ResourceLocation.class).invoke(multiblock, ID);
      // Patchouli draws a view one block above its anchor; this puts the controller's part on it.
      multiblockType.getMethod("offsetView", int.class, int.class, int.class).invoke(multiblock, 0, 1, 0);
      type.getMethod("showMultiblock", multiblockType, Component.class, BlockPos.class,
          net.minecraft.world.level.block.Rotation.class).invoke(api, multiblock,
          Component.translatable("entrelumen.ark.guide.title"), at.origin(), ArkMultiblock.rotation(at.rotation()));
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.warn("Patchouli's multiblock view is unavailable; the Ark guide uses particles", failure);
      return false;
    }
  }

  private static void clearPatchouli() {
    try {
      Object api = api();
      Object current = apiType().getMethod("getCurrentMultiblock").invoke(api);
      if (current != null && ID.equals(Class.forName("vazkii.patchouli.api.IMultiblock").getMethod("getID").invoke(current)))
        apiType().getMethod("clearMultiblock").invoke(api);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.debug("Could not clear Patchouli's multiblock view", failure);
    }
  }

  /** The state a part is drawn with; a block from an absent mod is not drawn. */
  static BlockState display(ArkMultiblock.Part part) {
    if (!BuiltInRegistries.BLOCK.containsKey(part.block())) return null;
    try {
      return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), part.state(), false).blockState();
    } catch (Exception invalid) {
      return BuiltInRegistries.BLOCK.get(part.block()).defaultBlockState();
    }
  }

  /** The server's rule, applied to a block state: the family, then the kept properties. */
  static boolean matches(ArkMultiblock.Part part, BlockState state, int rotation) {
    var matcher = ArkMultiblock.matcher(pos -> BuiltInRegistries.BLOCK.getKey(state.getBlock()),
        (pos, name) -> {
          var property = state.getBlock().getStateDefinition().getProperty(name);
          return property == null ? null : value(state, property);
        }, dev.entrelumen.ArkState::normalise);
    return Boolean.TRUE.equals(matcher.matches(BlockPos.ZERO, part, rotation));
  }

  private static <T extends Comparable<T>> String value(BlockState state,
      net.minecraft.world.level.block.state.properties.Property<T> property) {
    return property.getName(state.getValue(property));
  }

  // ---- particles ---------------------------------------------------------------------------------

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (anchor == null) return;
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    if (level == null || minecraft.player == null || !level.dimension().equals(dimension)) {
      hide();
      return;
    }
    if (patchouliShown || level.getGameTime() % 10 != 0) return;
    var definition = ArkMultiblock.builtin();
    for (ArkMultiblock.Part part : definition.required()) {
      BlockPos pos = ArkMultiblock.world(anchor, part.offset());
      if (pos.distSqr(minecraft.player.blockPosition()) > 48 * 48 || !level.isLoaded(pos)) continue;
      if (matches(part, level.getBlockState(pos), anchor.rotation())) continue;
      level.addParticle(part.isSlot() ? SLOT : CORE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
    }
  }
}
