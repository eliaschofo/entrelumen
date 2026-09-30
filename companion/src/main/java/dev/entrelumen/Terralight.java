package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Terralight crystal, the only renewable source of Terra's grow lamps (docs/design/terra-garden.md, «La
 * economía de las lámparas»). A grow lamp placed as a block over an air block over grass, and beside that
 * column a copper grounding rod on dirt with two «cables» above it (any block with an energy capability, of
 * any mod; nothing needs to flow): the crystal grows in the air block. Four stages, very slow, faster only in
 * the rain; the full cluster gives one Terralight shard, and a shard makes a lamp.
 */
public final class Terralight {
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "entrelumen");
  static final DeferredRegister<ResourceLocation> STATS = DeferredRegister.create(
      net.minecraft.core.registries.Registries.CUSTOM_STAT, "entrelumen");

  // ---- config ------------------------------------------------------------------------------------
  public static final ModConfigSpec SPEC;
  static final ModConfigSpec.LongValue FULL_GROWTH_TICKS, AFTER_RAIN_TICKS;
  static final ModConfigSpec.IntValue RAIN_MULTIPLIER;

  static {
    var builder = new ModConfigSpec.Builder();
    builder.push("terralight");
    FULL_GROWTH_TICKS = builder.comment("Game ticks of dry growth from nothing to a full Terralight crystal (288000 = 4 hours).")
        .translation("entrelumen.config.terralight.full_growth_ticks")
        .defineInRange("fullGrowthTicks", TerralightRules.FULL_GROWTH_TICKS, 20L, Long.MAX_VALUE / 64);
    RAIN_MULTIPLIER = builder.comment("How much faster it grows while it rains there, and for a while after.")
        .translation("entrelumen.config.terralight.rain_multiplier")
        .defineInRange("rainMultiplier", TerralightRules.RAIN_MULTIPLIER, 1, 64);
    AFTER_RAIN_TICKS = builder.comment("Game ticks after the rain stops during which it still grows faster (6000 = 5 minutes).")
        .translation("entrelumen.config.terralight.after_rain_ticks")
        .defineInRange("afterRainTicks", TerralightRules.AFTER_RAIN_TICKS, 0L, 1_000_000L);
    builder.pop();
    SPEC = builder.build();
  }

  static long fullGrowthTicks() {
    return SPEC.isLoaded() ? FULL_GROWTH_TICKS.get() : TerralightRules.FULL_GROWTH_TICKS;
  }

  static int rainMultiplier() {
    return SPEC.isLoaded() ? RAIN_MULTIPLIER.get() : TerralightRules.RAIN_MULTIPLIER;
  }

  static long afterRainTicks() {
    return SPEC.isLoaded() ? AFTER_RAIN_TICKS.get() : TerralightRules.AFTER_RAIN_TICKS;
  }

  // ---- content -----------------------------------------------------------------------------------
  public static final DeferredBlock<GroundingRodBlock> ROD = BLOCKS.register("terralight_grounding_rod",
      () -> new GroundingRodBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2f, 6f)
          .sound(SoundType.COPPER).noOcclusion()));
  public static final DeferredBlock<CrystalBlock> CRYSTAL = BLOCKS.register("terralight_crystal",
      () -> new CrystalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(1.5f)
          .sound(SoundType.AMETHYST_CLUSTER).noOcclusion().pushReaction(PushReaction.DESTROY)
          .lightLevel(state -> 3 + 2 * state.getValue(CrystalBlock.STAGE))));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundingRodEntity>> ROD_ENTITY =
      BLOCK_ENTITIES.register("terralight_grounding_rod",
          () -> BlockEntityType.Builder.of(GroundingRodEntity::new, ROD.get()).build(null));
  public static final DeferredItem<BlockItem> ROD_ITEM = ITEMS.registerSimpleBlockItem("terralight_grounding_rod", ROD);
  public static final DeferredItem<ShardItem> SHARD = ITEMS.register("terralight_shard",
      () -> new ShardItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
  /** Grow lamps a player crafted from shards: the guide's lamp-recipe quest counts it. */
  public static final DeferredHolder<ResourceLocation, ResourceLocation> LAMPS_CRAFTED =
      STATS.register("terra_grow_lamps_crafted", () -> TerraGarden.id("terra_grow_lamps_crafted"));

  private Terralight() {}

  static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    BLOCK_ENTITIES.register(bus);
    STATS.register(bus);
    bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
        () -> Stats.CUSTOM.get(LAMPS_CRAFTED.get(), StatFormatter.DEFAULT)));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent event) -> {
      if (!event.getEntity().level().isClientSide && event.getCrafting().is(TerraGarden.GROW_LAMP.get()))
        event.getEntity().awardStat(LAMPS_CRAFTED.get(), event.getCrafting().getCount());
    });
  }

  /** A «cable»: any block that offers an energy capability, on any side or none. Nothing has to flow. */
  static boolean cable(Level level, BlockPos pos) {
    if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null) return true;
    for (Direction side : Direction.values())
      if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null) return true;
    return false;
  }

  static boolean soil(BlockState state) {
    return state.is(BlockTags.DIRT);
  }

  /** The grounding rod: a thin copper stick, the setup's controller. */
  public static final class GroundingRodBlock extends Block implements EntityBlock {
    public static final MapCodec<GroundingRodBlock> CODEC = simpleCodec(GroundingRodBlock::new);
    private static final VoxelShape SHAPE = Block.box(7, 0, 7, 9, 16, 9);

    public GroundingRodBlock(Properties properties) {
      super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
      return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new GroundingRodEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      if (level.isClientSide || type != ROD_ENTITY.get()) return null;
      return (world, pos, blockState, entity) -> ((GroundingRodEntity) entity).serverTick((ServerLevel) world);
    }
  }

  /**
   * The crystal: four stages (small, medium and large bud, then the cluster), placed and grown only by its
   * grounding rod. It never takes random ticks and is not bone-mealable. Mined full it gives one shard;
   * mined earlier, nothing.
   */
  public static final class CrystalBlock extends Block {
    public static final MapCodec<CrystalBlock> CODEC = simpleCodec(CrystalBlock::new);
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    private static final VoxelShape[] SHAPES = {
        Block.box(5, 0, 5, 11, 4, 11), Block.box(5, 0, 5, 11, 7, 11), Block.box(4, 0, 4, 12, 10, 12), Block.box(3, 0, 3, 13, 14, 13)};

    public CrystalBlock(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
      return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPES[state.getValue(STAGE)];
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
      return false;
    }
  }

  /** The shard: shimmers like the luminosities, a tooltip line and nothing else. */
  public static final class ShardItem extends Item {
    public ShardItem(Properties properties) {
      super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.entrelumen.terralight_shard.tooltip").withStyle(ChatFormatting.GRAY));
    }
  }
}
