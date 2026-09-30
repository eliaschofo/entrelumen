package dev.entrelumen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Terra's hydroponic garden (docs/design/terra-garden.md), a little engine with a pot at its heart: the
 * plan that projects it as a ghost, the grow lamp that wakes a built one, the core (the pot) and the
 * outlets the harvest leaves by, the core's block
 * entity and capability, the excluded-seeds tags and the two statistics the guide's quests count.
 */
public final class TerraGarden {
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "entrelumen");
  static final DeferredRegister.DataComponents COMPONENTS =
      DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "entrelumen");
  static final DeferredRegister<ResourceLocation> STATS = DeferredRegister.create(Registries.CUSTOM_STAT, "entrelumen");

  /** Seeds the garden refuses: boss drops and act VI content (the design doc lists why). */
  public static final TagKey<Item> EXCLUDED_SEEDS = ItemTags.create(id("terra_garden_excluded"));
  /** Items the garden never makes, whatever the crop's harvest says (Nether stars, dragon eggs). */
  public static final TagKey<Item> FORBIDDEN_DROPS = ItemTags.create(id("terra_garden_forbidden_drops"));

  /** Gardens a player woke with the grow lamp. */
  public static final DeferredHolder<ResourceLocation, ResourceLocation> ACTIVATIONS =
      STATS.register("terra_garden_activations", () -> id("terra_garden_activations"));
  /** Harvests the gardens a player woke have made while the player was online. */
  public static final DeferredHolder<ResourceLocation, ResourceLocation> HARVESTS =
      STATS.register("terra_garden_harvests", () -> id("terra_garden_harvests"));

  /** A core's seed and store, kept on the item when the core is broken, so nothing spills or is lost. */
  public record Contents(ItemStack seed, List<StoredStack> store) {
    public static final Contents EMPTY = new Contents(ItemStack.EMPTY, List.of());
    public static final Codec<Contents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("seed", ItemStack.EMPTY).forGetter(Contents::seed),
        StoredStack.CODEC.listOf().optionalFieldOf("store", List.of()).forGetter(Contents::store))
        .apply(instance, Contents::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Contents> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public Contents {
      seed = seed.copy();
      store = List.copyOf(store);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Contents that && ItemStack.matches(seed, that.seed) && store.equals(that.store);
    }

    @Override
    public int hashCode() {
      return 31 * ItemStack.hashItemAndComponents(seed) + store.hashCode();
    }
  }

  /** One kind of item in the store and how many. */
  public record StoredStack(ItemStack item, long count) {
    public static final Codec<StoredStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(StoredStack::item),
        Codec.LONG.fieldOf("count").forGetter(StoredStack::count))
        .apply(instance, StoredStack::new));

    public StoredStack {
      item = item.copyWithCount(1);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof StoredStack that && count == that.count && ItemStack.isSameItemSameComponents(item, that.item);
    }

    @Override
    public int hashCode() {
      return 31 * ItemStack.hashItemAndComponents(item) + Long.hashCode(count);
    }
  }

  public static final DeferredHolder<DataComponentType<?>, DataComponentType<Contents>> CONTENTS =
      COMPONENTS.registerComponentType("terra_garden_contents",
          builder -> builder.persistent(Contents.CODEC).networkSynchronized(Contents.STREAM_CODEC));

  public static final DeferredBlock<TerraGardenCoreBlock> CORE = BLOCKS.register("terra_garden_core",
      () -> new TerraGardenCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_STEM).strength(3.5f, 6f)
          .sound(SoundType.COPPER).noOcclusion().requiresCorrectToolForDrops()
          .lightLevel(state -> switch (state.getValue(TerraGardenCoreBlock.GARDEN)) {
            case GROWING -> 13;
            case BUILT -> 7;
            case UNBUILT -> 0;
          })));
  /** Where the harvest leaves the engine: it pushes into what touches it and lets pipes pull the core's store. */
  public static final DeferredBlock<TerraEngineMemberBlock> OUTLET = BLOCKS.register("terra_garden_outlet",
      () -> new TerraEngineMemberBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3f, 6f)
          .sound(SoundType.COPPER).requiresCorrectToolForDrops()));
  /** The engine's plain member: riveted copper; formed, it draws nothing and the core draws the engine. */
  public static final DeferredBlock<TerraEngineMemberBlock> CASING = BLOCKS.register("terra_engine_casing",
      () -> new TerraEngineMemberBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3f, 6f)
          .sound(SoundType.COPPER).requiresCorrectToolForDrops()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerraGardenCoreEntity>> CORE_ENTITY =
      BLOCK_ENTITIES.register("terra_garden_core",
          () -> BlockEntityType.Builder.of(TerraGardenCoreEntity::new, CORE.get()).build(null));

  public static final DeferredItem<PlanItem> PLAN = ITEMS.register("terra_garden_plan",
      () -> new PlanItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
  /** The grow lamp placed as a block: the top of a Terralight crystal's column (Terralight). */
  public static final DeferredBlock<Block> LAMP_BLOCK = BLOCKS.register("terra_grow_lamp",
      () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(1f)
          .sound(SoundType.LANTERN).noOcclusion().lightLevel(state -> 12)) {
        private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(4, 8, 4, 12, 13, 12), Block.box(7, 13, 7, 9, 16, 9), Block.box(5, 3, 5, 11, 8, 11));

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
          return SHAPE;
        }
      });
  public static final DeferredItem<GrowLampItem> GROW_LAMP = ITEMS.register("terra_grow_lamp",
      () -> new GrowLampItem(LAMP_BLOCK.get(), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));
  public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.register("terra_garden_core",
      () -> new CoreItem(CORE.get(), new Item.Properties().rarity(Rarity.RARE)));
  public static final DeferredItem<BlockItem> OUTLET_ITEM = ITEMS.registerSimpleBlockItem("terra_garden_outlet", OUTLET);
  public static final DeferredItem<BlockItem> CASING_ITEM = ITEMS.registerSimpleBlockItem("terra_engine_casing", CASING);

  /**
   * What the plan does on the client, set by the client setup ({@code client.TerraGardenClient}); a
   * dedicated server leaves it empty. The item never touches client classes itself.
   */
  public static volatile Consumer<UseOnContext> planClientUse = context -> {};

  private TerraGarden() {}

  static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("entrelumen", path);
  }

  static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    BLOCK_ENTITIES.register(bus);
    COMPONENTS.register(bus);
    STATS.register(bus);
    bus.addListener(TerraGarden::capabilities);
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TerraGardenCoreEntity::onBlockBroken);
    bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
      Stats.CUSTOM.get(ACTIVATIONS.get(), StatFormatter.DEFAULT);
      Stats.CUSTOM.get(HARVESTS.get(), StatFormatter.DEFAULT);
    }));
  }

  private static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CORE_ENTITY.get(), (core, side) -> core.itemHandler());
    // An outlet shows the store of the core whose standing engine it belongs to, and nothing otherwise.
    event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, entity, side) -> {
      TerraGardenCoreEntity core = TerraGardenCoreEntity.coreOfOutlet(level, pos);
      return core == null ? null : core.itemHandler();
    }, OUTLET.get());
  }

  /**
   * A seed the garden grows: an item that places a crop block (wheat seeds, carrots, Mystical
   * Agriculture's seeds and the like) and is not excluded. Returns the crop, or null.
   */
  public static CropBlock crop(ItemStack stack) {
    if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return null;
    return item.getBlock() instanceof CropBlock crop ? crop : null;
  }

  public static boolean excluded(ItemStack stack) {
    return stack.is(EXCLUDED_SEEDS);
  }

  /** Terra's plan: right-click a block to project the garden there as a ghost; crouch to take it away. */
  public static final class PlanItem extends Item {
    public PlanItem(Properties properties) {
      super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
      if (context.getLevel().isClientSide) planClientUse.accept(context);
      return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.entrelumen.terra_garden_plan.tooltip").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.entrelumen.terra_garden_plan.use").withStyle(ChatFormatting.DARK_GREEN));
    }
  }

  /**
   * Terra's grow lamp. On a built engine's pot it wakes the engine and goes into it (one lamp per engine;
   * breaking any block of the engine gives it back). Anywhere else it is placed as a block, the top of a
   * Terralight crystal's column.
   */
  public static final class GrowLampItem extends BlockItem {
    public GrowLampItem(Block block, Properties properties) {
      super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
      var level = context.getLevel();
      BlockPos pos = context.getClickedPos();
      if (!(level.getBlockEntity(pos) instanceof TerraGardenCoreEntity core)) return super.useOn(context);
      if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) core.activate(player, context.getItemInHand());
      return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.entrelumen.terra_grow_lamp.tooltip").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.entrelumen.terra_grow_lamp.use").withStyle(ChatFormatting.DARK_GREEN));
    }
  }

  /** The core as an item: says what it carries when it was broken with a seed or a full store. */
  public static final class CoreItem extends BlockItem {
    public CoreItem(Block block, Properties properties) {
      super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("block.entrelumen.terra_garden_core.tooltip").withStyle(ChatFormatting.GRAY));
      Contents contents = stack.get(CONTENTS.get());
      if (contents == null) return;
      if (!contents.seed().isEmpty())
        tooltip.add(Component.translatable("entrelumen.terra_garden.item_seed", contents.seed().getHoverName())
            .withStyle(ChatFormatting.DARK_GREEN));
      long stored = contents.store().stream().mapToLong(StoredStack::count).sum();
      if (stored > 0)
        tooltip.add(Component.translatable("entrelumen.terra_garden.item_store", stored).withStyle(ChatFormatting.DARK_GREEN));
    }
  }
}
