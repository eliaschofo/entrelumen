package dev.entrelumen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesBalance.Rarity;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * The Envés's loot vocabulary for its loot tables ({@code data/entrelumen/loot_table/enves/}):
 * {@code entrelumen:enves_gear} (a piece with Apotheosis affixes whose rarity comes from the attempt's
 * World Tier, the floor and the Fortune blessing, see {@link EnvesBalance.Settings#rarity}),
 * {@code entrelumen:enves_gem} (a gem whose purity follows the same), {@code entrelumen:enves_material}
 * (the rarity's salvage material), the function {@code entrelumen:enves_floor_bonus} (more of a stack
 * the deeper it drops) and the condition {@code entrelumen:enves_floor}. The floor and the tier come
 * from where the loot is rolled (the chest, or the echo that fell), so a Pinnacle player farming a
 * Frontier attempt gets Frontier loot. Apotheosis is reached by reflection; without it the entries
 * give plain enchanted gear, emeralds and vanilla materials, so the tables always load.
 */
public final class EnvesLoot {
  private static final Logger LOGGER = LogUtils.getLogger();

  private EnvesLoot() {}

  /** Where loot drops: the attempt's tier, the floor, and whether the floor's Fortune blessing is taken. */
  public record Where(Tier tier, int depth, boolean fortune) {
    public static final Where OUTSIDE = new Where(Tier.FRONTIER, 1, false);
  }

  public static Where where(LootContext context) {
    Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
    ServerLevel level = context.getLevel();
    if (origin == null || !level.dimension().equals(Enves.LEVEL)) return Where.OUTSIDE;
    BlockPos pos = BlockPos.containing(origin);
    int depth = EnvesGeometry.depthAt(pos.getY());
    var attempt = Enves.attemptAt(level.getServer(), pos);
    if (attempt.isEmpty() || depth < 1) return Where.OUTSIDE;
    boolean fortune = EnvesShrines.fortune(level.getServer(), attempt.get(), depth);
    return new Where(attempt.get().tier, depth, fortune);
  }

  static Random random(LootContext context) {
    return new Random(context.getRandom().nextLong());
  }

  // ---- Apotheosis by reflection -------------------------------------------------------------

  static final class Apoth {
    private static boolean resolved, ok;
    private static Method standalone, createLootItem, rarityValue, material, gemRandom, gemStack, minPurity;
    private static Object rarityRegistry, gemRegistry;
    private static Object[] tiers, purities;

    static synchronized boolean ready() {
      if (resolved) return ok;
      resolved = true;
      if (ModList.get() == null || !ModList.get().isLoaded("apotheosis")) return false;
      try {
        String base = "dev.shadowsoffire.apotheosis.";
        Class<?> genContext = Class.forName(base + "tiers.GenContext");
        Class<?> worldTier = Class.forName(base + "tiers.WorldTier");
        Class<?> rarity = Class.forName(base + "loot.LootRarity");
        Class<?> rarities = Class.forName(base + "loot.RarityRegistry");
        Class<?> gems = Class.forName(base + "socket.gem.GemRegistry");
        Class<?> gem = Class.forName(base + "socket.gem.Gem");
        Class<?> purity = Class.forName(base + "socket.gem.Purity");
        Class<?> dynamic = Class.forName("dev.shadowsoffire.placebo.reload.DynamicRegistry");
        standalone = genContext.getMethod("standalone", net.minecraft.util.RandomSource.class, worldTier, float.class,
            ServerLevel.class, BlockPos.class);
        createLootItem = Class.forName(base + "loot.LootController").getMethod("createRandomLootItem", genContext, rarity);
        rarityValue = dynamic.getMethod("getValue", ResourceLocation.class);
        material = rarity.getMethod("getMaterial");
        rarityRegistry = rarities.getField("INSTANCE").get(null);
        gemRegistry = gems.getField("INSTANCE").get(null);
        gemRandom = gems.getMethod("getRandomItem", genContext);
        gemStack = gems.getMethod("createGemStack", gem, purity);
        minPurity = gem.getMethod("getMinPurity");
        tiers = new Object[Tier.values().length];
        for (Tier tier : Tier.values()) tiers[tier.ordinal()] = worldTier.getField(tier.name()).get(null);
        purities = purity.getEnumConstants();
        ok = purities.length == EnvesBalance.PURITIES.size();
        if (!ok) LOGGER.warn("Apotheosis's purities changed; Envés gems fall back to plain ones");
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
        LOGGER.warn("Apotheosis is loaded but its loot API was not found; Envés loot falls back to plain gear", e);
        ok = false;
      }
      return ok;
    }

    static Object context(LootContext context, Where where) throws ReflectiveOperationException {
      BlockPos pos = BlockPos.containing(Optional.ofNullable(context.getParamOrNull(LootContextParams.ORIGIN)).orElse(Vec3.ZERO));
      return standalone.invoke(null, context.getRandom(), tiers[where.tier().ordinal()], context.getLuck(), context.getLevel(), pos);
    }

    static Object rarity(Rarity rarity) throws ReflectiveOperationException {
      return rarityValue.invoke(rarityRegistry, ResourceLocation.parse(rarity.id()));
    }

    static ItemStack gear(LootContext context, Where where, Rarity rarity) {
      try {
        Object value = rarity(rarity);
        if (value == null) return ItemStack.EMPTY;
        Object stack = createLootItem.invoke(null, context(context, where), value);
        return stack instanceof ItemStack item ? item : ItemStack.EMPTY;
      } catch (ReflectiveOperationException | RuntimeException e) {
        LOGGER.debug("Apotheosis could not make an affix item", e);
        return ItemStack.EMPTY;
      }
    }

    static ItemStack gem(LootContext context, Where where, int purity) {
      try {
        Object gem = gemRandom.invoke(gemRegistry, context(context, where));
        if (gem == null) return ItemStack.EMPTY;
        Object min = minPurity.invoke(gem);
        int index = Math.max(purity, min instanceof Enum<?> e ? e.ordinal() : 0);
        Object stack = gemStack.invoke(null, gem, purities[Math.min(index, purities.length - 1)]);
        return stack instanceof ItemStack item ? item : ItemStack.EMPTY;
      } catch (ReflectiveOperationException | RuntimeException e) {
        LOGGER.debug("Apotheosis could not make a gem", e);
        return ItemStack.EMPTY;
      }
    }

    static ItemStack material(Rarity rarity) {
      try {
        Object value = rarity(rarity);
        if (value == null) return ItemStack.EMPTY;
        return material.invoke(value) instanceof Item item ? new ItemStack(item) : ItemStack.EMPTY;
      } catch (ReflectiveOperationException | RuntimeException e) {
        return ItemStack.EMPTY;
      }
    }
  }

  // ---- Fallbacks (a pack without Apotheosis, and the isolated GameTests) --------------------

  private static final List<Item> PLAIN_IRON = List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.BOW, Items.IRON_HELMET,
      Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
  private static final List<Item> PLAIN_DIAMOND = List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.CROSSBOW,
      Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
  private static final List<Item> PLAIN_NETHERITE = List.of(Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_HELMET,
      Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

  static ItemStack plainGear(LootContext context, Rarity rarity) {
    Random random = random(context);
    List<Item> pool = rarity.ordinal() >= Rarity.MYTHIC.ordinal() ? PLAIN_NETHERITE
        : rarity.ordinal() >= Rarity.RARE.ordinal() ? PLAIN_DIAMOND : PLAIN_IRON;
    ItemStack stack = new ItemStack(pool.get(random.nextInt(pool.size())));
    int level = new int[] {5, 12, 20, 28, 30}[rarity.ordinal()];
    var access = context.getLevel().registryAccess();
    var table = access.registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
        .getTag(net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE);
    return EnchantmentHelper.enchantItem(context.getRandom(), stack, level, access, table);
  }

  static ItemStack plainGem(int purity) {
    return new ItemStack(purity >= 5 ? Items.ECHO_SHARD : purity >= 3 ? Items.DIAMOND : Items.EMERALD);
  }

  static ItemStack plainMaterial(Rarity rarity) {
    return new ItemStack(switch (rarity) {
      case COMMON -> Items.IRON_INGOT;
      case UNCOMMON -> Items.GOLD_INGOT;
      case RARE -> Items.AMETHYST_SHARD;
      case EPIC -> Items.ECHO_SHARD;
      case MYTHIC -> Items.HEART_OF_THE_SEA;
    });
  }

  // ---- The entries --------------------------------------------------------------------------

  private static final Set<LootContextParam<?>> ORIGIN = Set.of(LootContextParams.ORIGIN);

  /** {@code entrelumen:enves_gear}: {@code rarity_bonus} steps, or the tier's {@code top} rarity. */
  public static final class Gear extends LootPoolSingletonContainer {
    public static final MapCodec<Gear> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("rarity_bonus", 0).forGetter(e -> e.bonus),
            Codec.BOOL.optionalFieldOf("top", false).forGetter(e -> e.top))
        .and(singletonFields(i)).apply(i, Gear::new));
    final int bonus;
    final boolean top;

    Gear(int bonus, boolean top, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
      super(weight, quality, conditions, functions);
      this.bonus = bonus;
      this.top = top;
    }

    @Override
    public LootPoolEntryType getType() {
      return EnvesContent.LOOT_GEAR.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> out, LootContext context) {
      Where where = where(context);
      Rarity rarity = EnvesContentConfig.balance().rarity(where.tier(), where.depth(), bonus, top, where.fortune(), random(context));
      ItemStack stack = Apoth.ready() ? Apoth.gear(context, where, rarity) : ItemStack.EMPTY;
      out.accept(stack.isEmpty() ? plainGear(context, rarity) : stack);
    }
  }

  /** {@code entrelumen:enves_gem}: a random gem, purity by tier and floor plus {@code purity_bonus}. */
  public static final class Gem extends LootPoolSingletonContainer {
    public static final MapCodec<Gem> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("purity_bonus", 0).forGetter(e -> e.bonus))
        .and(singletonFields(i)).apply(i, Gem::new));
    final int bonus;

    Gem(int bonus, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
      super(weight, quality, conditions, functions);
      this.bonus = bonus;
    }

    @Override
    public LootPoolEntryType getType() {
      return EnvesContent.LOOT_GEM.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> out, LootContext context) {
      Where where = where(context);
      int purity = EnvesContentConfig.balance().purity(where.tier(), where.depth(), bonus);
      ItemStack stack = Apoth.ready() ? Apoth.gem(context, where, purity) : ItemStack.EMPTY;
      out.accept(stack.isEmpty() ? plainGem(purity) : stack);
    }
  }

  /** {@code entrelumen:enves_material}: the salvage material of a rarity rolled like gear. */
  public static final class Material extends LootPoolSingletonContainer {
    public static final MapCodec<Material> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("rarity_bonus", 0).forGetter(e -> e.bonus))
        .and(singletonFields(i)).apply(i, Material::new));
    final int bonus;

    Material(int bonus, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
      super(weight, quality, conditions, functions);
      this.bonus = bonus;
    }

    @Override
    public LootPoolEntryType getType() {
      return EnvesContent.LOOT_MATERIAL.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> out, LootContext context) {
      Where where = where(context);
      Rarity rarity = EnvesContentConfig.balance().rarity(where.tier(), where.depth(), bonus, false, where.fortune(), random(context));
      ItemStack stack = Apoth.ready() ? Apoth.material(rarity) : ItemStack.EMPTY;
      out.accept(stack.isEmpty() ? plainMaterial(rarity) : stack);
    }
  }

  /** {@code entrelumen:enves_floor_bonus}: adds {@code per_floor} × (floor − 1) to the count, the fraction by chance. */
  public static final class FloorBonus extends LootItemConditionalFunction {
    public static final MapCodec<FloorBonus> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
        .and(Codec.FLOAT.fieldOf("per_floor").forGetter(f -> f.perFloor)).apply(i, FloorBonus::new));
    final float perFloor;

    FloorBonus(List<LootItemCondition> conditions, float perFloor) {
      super(conditions);
      this.perFloor = perFloor;
    }

    @Override
    public LootItemFunctionType<FloorBonus> getType() {
      return EnvesContent.LOOT_FLOOR_BONUS.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
      return ORIGIN;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
      stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() + bonus(perFloor, where(context).depth(), context.getRandom().nextFloat())));
      return stack;
    }

    public static int bonus(float perFloor, int depth, float roll) {
      return EnvesBalance.floorBonus(perFloor, depth, roll);
    }
  }

  /** {@code entrelumen:enves_floor}: true on floors {@code min}..{@code max} (outside the Envés, floor I). */
  public record FloorCondition(int min, int max) implements LootItemCondition {
    public static final MapCodec<FloorCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.INT.optionalFieldOf("min", 1).forGetter(FloorCondition::min),
        Codec.INT.optionalFieldOf("max", EnvesLayout.FLOORS).forGetter(FloorCondition::max)).apply(i, FloorCondition::new));

    @Override
    public LootItemConditionType getType() {
      return EnvesContent.LOOT_FLOOR.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
      return ORIGIN;
    }

    @Override
    public boolean test(LootContext context) {
      int depth = where(context).depth();
      return depth >= min && depth <= max;
    }
  }

  /** Whether Apotheosis answers (for the tests and the logs). */
  public static boolean apotheosis() {
    return Apoth.ready();
  }

  static Item shard() {
    return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("entrelumen", "sour_light_shard"));
  }
}
