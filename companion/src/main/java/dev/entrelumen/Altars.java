package dev.entrelumen;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Renewal and Terraform altars: blocks, items, block entities and their capabilities. */
public final class Altars {
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "entrelumen");

  public static final DeferredBlock<RenewalAltarBlock> RENEWAL_ALTAR = BLOCKS.register("renewal_altar",
      () -> new RenewalAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(2.5f)
          .sound(SoundType.STONE).lightLevel(state -> 7).noOcclusion()));
  public static final DeferredBlock<TerraformAltarBlock> TERRAFORM_ALTAR = BLOCKS.register("terraform_altar",
      () -> new TerraformAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(3f)
          .sound(SoundType.STONE).noOcclusion()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RenewalAltarEntity>> RENEWAL_ENTITY =
      BLOCK_ENTITIES.register("renewal_altar",
          () -> BlockEntityType.Builder.of(RenewalAltarEntity::new, RENEWAL_ALTAR.get()).build(null));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerraformAltarEntity>> TERRAFORM_ENTITY =
      BLOCK_ENTITIES.register("terraform_altar",
          () -> BlockEntityType.Builder.of(TerraformAltarEntity::new, TERRAFORM_ALTAR.get()).build(null));

  static {
    ITEMS.register("renewal_altar", () -> new AltarItem(RENEWAL_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.renewal.tooltip", "entrelumen.altar.renewal.tooltip_use"));
    ITEMS.register("terraform_altar", () -> new AltarItem(TERRAFORM_ALTAR.get(), new Item.Properties(),
        "entrelumen.altar.terraform.tooltip", "entrelumen.altar.terraform.tooltip_use"));
  }

  /** Plinth [1,0,1]-[15,3,15] and column [3,3,3]-[13,11,13], shared by both altars (pixels). */
  private static final VoxelShape BASE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 3, 13, 11, 13));
  /** Renewal: cap [1,11,1]-[15,14,15]; the crystal above it stays outside the shape. */
  static final VoxelShape RENEWAL_SHAPE = Shapes.or(BASE, Block.box(1, 11, 1, 15, 14, 15));
  /** Terraform: cap [1,11,1]-[15,13,15]; the instrument above it stays outside the shape. */
  static final VoxelShape TERRAFORM_SHAPE = Shapes.or(BASE, Block.box(1, 11, 1, 15, 13, 15));

  private Altars() {}

  static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    BLOCK_ENTITIES.register(bus);
    bus.addListener(Altars::capabilities);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Altars::allowCrouchedUse);
  }

  private static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RENEWAL_ENTITY.get(),
        (altar, side) -> altar.fuelHandler());
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TERRAFORM_ENTITY.get(),
        (altar, side) -> altar.itemHandler());
  }

  /** Crouching with an empty main hand reaches the altar even when the offhand holds something. */
  static void allowCrouchedUse(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE || event.getHand() != InteractionHand.MAIN_HAND
        || !event.getEntity().isSecondaryUseActive() || !event.getEntity().getMainHandItem().isEmpty()) return;
    if (event.getLevel().hasChunkAt(event.getPos())) {
      Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
      if (block instanceof RenewalAltarBlock || block instanceof TerraformAltarBlock) event.setUseBlock(TriState.TRUE);
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
