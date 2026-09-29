package dev.entrelumen.client;

import com.mojang.logging.LogUtils;
import dev.entrelumen.TerraGarden;
import dev.entrelumen.TerraGardenLayout;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * The plan's ghost: Terra's garden projected where the player right-clicks, through Patchouli's own
 * multiblock view (the «eye» Botania, Occultism and others use). Right-click a block: the garden appears
 * with its core on the spot a block would go and its front toward the player. Crouch and right-click on
 * the ghost: it goes away. That is all the plan does.
 *
 * <p>Patchouli (pinned 1.21.1-93) is an optional dependency declared in neoforge.mods.toml and reached
 * by reflection through its public API ({@code PatchouliAPI.get()}: {@code makeSparseMultiblock},
 * {@code predicateMatcher}, {@code showMultiblock}, {@code getCurrentMultiblock}, {@code clearMultiblock}),
 * like the Ark guide, so the companion neither compiles against nor ships it. Each ghost block accepts
 * what the core accepts (copper at any stage, a grown vine), so Patchouli's count agrees with the core.
 */
@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TerraGardenClient {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("entrelumen", "terra_garden");
  private static BlockPos core;
  private static int rotation;
  private static ResourceKey<Level> dimension;

  private TerraGardenClient() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    TerraGarden.planClientUse = TerraGardenClient::use;
    NeoForge.EVENT_BUS.addListener(TerraGardenClient::tick);
  }

  static void use(UseOnContext context) {
    var minecraft = Minecraft.getInstance();
    var player = context.getPlayer();
    if (player == null || minecraft.level == null) return;
    BlockPos spot = context.getClickedPos().relative(context.getClickedFace());
    if (player.isSecondaryUseActive()) {
      if (core != null && (spot.equals(core) || context.getClickedPos().equals(core)
          || TerraGardenLayout.contains(TerraGardenLayout.builtin(), core, rotation, context.getClickedPos())
          || TerraGardenLayout.contains(TerraGardenLayout.builtin(), core, rotation, spot))) {
        hide();
        player.displayClientMessage(Component.translatable("entrelumen.terra_garden.ghost_hidden"), true);
      } else if (core != null) {
        player.displayClientMessage(Component.translatable("entrelumen.terra_garden.ghost_elsewhere"), true);
      }
      return;
    }
    Direction toward = player.getDirection().getOpposite();   // the garden's front looks at the player
    int turn = TerraGardenLayout.rotationFacing(toward);
    if (!show(spot, turn)) {
      player.displayClientMessage(Component.translatable("entrelumen.terra_garden.ghost_unavailable"), true);
      return;
    }
    core = spot.immutable();
    rotation = turn;
    dimension = minecraft.level.dimension();
    player.displayClientMessage(Component.translatable("entrelumen.terra_garden.ghost_shown"), true);
  }

  public static boolean shown() {
    return core != null;
  }

  static void hide() {
    clear();
    core = null;
    dimension = null;
  }

  private static void tick(ClientTickEvent.Post event) {
    if (core == null) return;
    var level = Minecraft.getInstance().level;
    if (level == null || !level.dimension().equals(dimension)) hide();
  }

  // ---- Patchouli ---------------------------------------------------------------------------------

  private static Object api() throws ReflectiveOperationException {
    return Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
  }

  private static Class<?> apiType() throws ClassNotFoundException {
    return Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");
  }

  private static boolean show(BlockPos at, int turn) {
    if (!ModList.get().isLoaded("patchouli")) return false;
    try {
      Object api = api();
      Class<?> type = apiType();
      Method matcher = type.getMethod("predicateMatcher", BlockState.class, Predicate.class);
      Map<BlockPos, Object> parts = new LinkedHashMap<>();
      for (TerraGardenLayout.Part part : TerraGardenLayout.builtin().parts()) {
        BlockState display = display(part);
        if (display == null) continue;
        Predicate<BlockState> accepts = state -> accepts(part, state, turn);
        parts.put(part.offset(), matcher.invoke(api, display, accepts));
      }
      Object multiblock = type.getMethod("makeSparseMultiblock", Map.class).invoke(api, parts);
      Class<?> multiblockType = Class.forName("vazkii.patchouli.api.IMultiblock");
      multiblockType.getMethod("setId", ResourceLocation.class).invoke(multiblock, ID);
      // Patchouli draws a view one block above its anchor; this puts the core's part on the anchor.
      multiblockType.getMethod("offsetView", int.class, int.class, int.class).invoke(multiblock, 0, 1, 0);
      type.getMethod("showMultiblock", multiblockType, Component.class, BlockPos.class,
          net.minecraft.world.level.block.Rotation.class).invoke(api, multiblock,
          Component.translatable("entrelumen.terra_garden.ghost_title"), at, TerraGardenLayout.rotation(turn));
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.warn("Patchouli's multiblock view is unavailable for Terra's garden", failure);
      return false;
    }
  }

  private static void clear() {
    try {
      Object api = api();
      Object current = apiType().getMethod("getCurrentMultiblock").invoke(api);
      if (current != null && ID.equals(Class.forName("vazkii.patchouli.api.IMultiblock").getMethod("getID").invoke(current)))
        apiType().getMethod("clearMultiblock").invoke(api);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.debug("Could not clear Patchouli's multiblock view", failure);
    }
  }

  /** The state a part is drawn with (the drawing's own); a block from an absent mod is not drawn. */
  static BlockState display(TerraGardenLayout.Part part) {
    if (!BuiltInRegistries.BLOCK.containsKey(part.block())) return null;
    try {
      return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), part.state(), false).blockState();
    } catch (Exception invalid) {
      return BuiltInRegistries.BLOCK.get(part.block()).defaultBlockState();
    }
  }

  /** The core's rule, applied to a block state. */
  static boolean accepts(TerraGardenLayout.Part part, BlockState state, int turn) {
    return TerraGardenLayout.matches(part, BuiltInRegistries.BLOCK.getKey(state.getBlock()), name -> {
      var property = state.getBlock().getStateDefinition().getProperty(name);
      return property == null ? null : value(state, property);
    }, turn, dev.entrelumen.TerraGardenCoreEntity::normaliseId);
  }

  private static <T extends Comparable<T>> String value(BlockState state,
      net.minecraft.world.level.block.state.properties.Property<T> property) {
    return property.getName(state.getValue(property));
  }
}
