package dev.entrelumen;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Ark altars: blocks, items, block entities, the Repose coffer's menu and their capabilities. */
public final class Altars {
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "entrelumen");
  static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "entrelumen");

  public static final DeferredBlock<RenewalAltarBlock> RENEWAL_ALTAR = BLOCKS.register("renewal_altar",
      () -> new RenewalAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(2.5f)
          .sound(SoundType.STONE).lightLevel(state -> 7).noOcclusion()));
  public static final DeferredBlock<TerraformAltarBlock> TERRAFORM_ALTAR = BLOCKS.register("terraform_altar",
      () -> new TerraformAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(3f)
          .sound(SoundType.STONE).noOcclusion()));
  public static final DeferredBlock<AreaAltarBlock> PEACE_ALTAR = BLOCKS.register("peace_altar",
      () -> new AreaAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(2.5f)
          .sound(SoundType.STONE).lightLevel(state -> 10).noOcclusion(), () -> Altars.PEACE_ENTITY.get(),
          PeaceAltarEntity::new));
  public static final DeferredBlock<AreaAltarBlock> GROWTH_ALTAR = BLOCKS.register("growth_altar",
      () -> new AreaAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(2.5f)
          .sound(SoundType.STONE).lightLevel(state -> 7).noOcclusion(), () -> Altars.GROWTH_ENTITY.get(),
          GrowthAltarEntity::new));
  public static final DeferredBlock<AreaAltarBlock> TIME_ALTAR = BLOCKS.register("time_altar",
      () -> new AreaAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.5f)
          .sound(SoundType.STONE).lightLevel(state -> 5).noOcclusion(), () -> Altars.TIME_ENTITY.get(),
          TimeAltarEntity::new));
  public static final DeferredBlock<ReposeAltarBlock> REPOSE_ALTAR = BLOCKS.register("repose_altar",
      () -> new ReposeAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f)
          .sound(SoundType.STONE).noOcclusion()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RenewalAltarEntity>> RENEWAL_ENTITY =
      BLOCK_ENTITIES.register("renewal_altar",
          () -> BlockEntityType.Builder.of(RenewalAltarEntity::new, RENEWAL_ALTAR.get()).build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerraformAltarEntity>> TERRAFORM_ENTITY =
      BLOCK_ENTITIES.register("terraform_altar",
          () -> BlockEntityType.Builder.of(TerraformAltarEntity::new, TERRAFORM_ALTAR.get()).build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PeaceAltarEntity>> PEACE_ENTITY =
      BLOCK_ENTITIES.register("peace_altar",
          () -> BlockEntityType.Builder.of(PeaceAltarEntity::new, PEACE_ALTAR.get()).build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrowthAltarEntity>> GROWTH_ENTITY =
      BLOCK_ENTITIES.register("growth_altar",
          () -> BlockEntityType.Builder.of(GrowthAltarEntity::new, GROWTH_ALTAR.get()).build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TimeAltarEntity>> TIME_ENTITY =
      BLOCK_ENTITIES.register("time_altar",
          () -> BlockEntityType.Builder.of(TimeAltarEntity::new, TIME_ALTAR.get()).build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReposeAltarEntity>> REPOSE_ENTITY =
      BLOCK_ENTITIES.register("repose_altar",
          () -> BlockEntityType.Builder.of(ReposeAltarEntity::new, REPOSE_ALTAR.get()).build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<ReposeAltarMenu>> REPOSE_MENU =
      MENUS.register("repose_altar", () -> IMenuTypeExtension.create(
          (id, inventory, buffer) -> new ReposeAltarMenu(id, inventory)));

  static {
    ITEMS.register("renewal_altar", () -> new AltarItem(RENEWAL_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.renewal.tooltip", "entrelumen.altar.renewal.tooltip_use"));
    ITEMS.register("terraform_altar", () -> new AltarItem(TERRAFORM_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.terraform.tooltip", "entrelumen.altar.terraform.tooltip_use"));
    ITEMS.register("peace_altar", () -> new AltarItem(PEACE_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.peace.tooltip", "entrelumen.altar.peace.tooltip_use"));
    ITEMS.register("growth_altar", () -> new AltarItem(GROWTH_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.growth.tooltip", "entrelumen.altar.growth.tooltip_use"));
    ITEMS.register("time_altar", () -> new AltarItem(TIME_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.time.tooltip", "entrelumen.altar.time.tooltip_use"));
    ITEMS.register("repose_altar", () -> new AltarItem(REPOSE_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.repose.tooltip", "entrelumen.altar.repose.tooltip_use"));
  }

  /** Plinth [1,0,1]-[15,3,15] and column [3,3,3]-[13,11,13], shared by every altar (pixels). */
  private static final VoxelShape BASE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 3, 13, 11, 13));
  /**
   * The pedestal with its cap [1,11,1]-[15,14,15]: the Renewal, Peace, Growth, Time and Repose
   * altars. The ornament each carries above the cap stays outside the shape.
   */
  static final VoxelShape PEDESTAL_SHAPE = Shapes.or(BASE, Block.box(1, 11, 1, 15, 14, 15));
  /** Renewal: the pedestal; the crystal above its cap stays outside the shape. */
  static final VoxelShape RENEWAL_SHAPE = PEDESTAL_SHAPE;
  /** Terraform: cap [1,11,1]-[15,13,15]; the instrument above it stays outside the shape. */
  static final VoxelShape TERRAFORM_SHAPE = Shapes.or(BASE, Block.box(1, 11, 1, 15, 13, 15));

  private Altars() {}

  static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    BLOCK_ENTITIES.register(bus);
    MENUS.register(bus);
    AltarEffects.register(bus);
    bus.addListener(Altars::capabilities);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Altars::allowCrouchedUse);
  }

  private static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RENEWAL_ENTITY.get(),
        (altar, side) -> altar.fuelHandler());
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TERRAFORM_ENTITY.get(),
        (altar, side) -> altar.fuelHandler());
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PEACE_ENTITY.get(), (altar, side) -> altar.fuelHandler());
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GROWTH_ENTITY.get(), (altar, side) -> altar.fuelHandler());
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TIME_ENTITY.get(), (altar, side) -> altar.fuelHandler());
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REPOSE_ENTITY.get(), ReposeAltarEntity::itemHandler);
  }

  /** Crouching with an empty main hand reaches the altar even when the offhand holds something. */
  static void allowCrouchedUse(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE || event.getHand() != InteractionHand.MAIN_HAND
        || !event.getEntity().isSecondaryUseActive() || !event.getEntity().getMainHandItem().isEmpty()) return;
    if (event.getLevel().hasChunkAt(event.getPos())) {
      Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
      if (block instanceof AltarBlock) event.setUseBlock(TriState.TRUE);
    }
  }

  /** The altar's block item, with its gestures in the tooltip. */
  public static final class AltarItem extends BlockItem {
    private final List<String> lines;

    AltarItem(Block block, Item.Properties properties, String... lines) {
      super(block, properties);
      this.lines = List.of(lines);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
      for (String line : lines) tooltip.add(Component.translatable(line));
    }
  }
}
