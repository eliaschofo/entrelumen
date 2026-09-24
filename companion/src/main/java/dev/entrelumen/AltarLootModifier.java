package dev.entrelumen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The harvest and loot bonuses of the Altars of Growth and Time, applied to a block's or a mob's
 * own loot table only (never to equipment a mob picked up, nor to nested tables).
 *
 * <ul>
 *   <li>{@code growth}: a ripe harvest (see {@link GrowthAltarEntity#ripe}) inside an active Altar of
 *       Growth gives twice its drops. Seeds ({@code c:seeds}) and the items and crops tagged
 *       {@code entrelumen:growth_altar_no_bonus} (Mystical Agriculture's essences and crops) keep
 *       their count and rate. An unripe crop gets nothing, so replanting cannot duplicate.</li>
 *   <li>{@code time}: a hostile, non-boss mob a real player kills inside an active Altar of Time
 *       drops three times its loot. Bosses, fake-player kills and items tagged
 *       {@code entrelumen:time_altar_no_bonus} are left alone.</li>
 * </ul>
 * In both, only stackable items without component changes multiply, so no unique item is copied.
 */
public final class AltarLootModifier extends LootModifier {
  public static final MapCodec<AltarLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
      .and(Codec.STRING.fieldOf("altar").forGetter(modifier -> modifier.altar))
      .apply(instance, AltarLootModifier::new));

  private final String altar;

  public AltarLootModifier(LootItemCondition[] conditions, String altar) {
    super(conditions);
    this.altar = altar;
  }

  @Override
  public MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier> codec() {
    return CODEC;
  }

  @Override
  protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
    Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
    if (origin == null || loot.isEmpty()) return loot;
    return switch (altar) {
      case "growth" -> growth(loot, context, origin);
      case "time" -> time(loot, context, origin);
      default -> loot;
    };
  }

  private static ObjectArrayList<ItemStack> growth(ObjectArrayList<ItemStack> loot, LootContext context, Vec3 origin) {
    BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
    if (state == null || !context.getQueriedLootTableId().equals(state.getBlock().getLootTable().location())
        || state.is(AltarEffects.GROWTH_NO_BONUS_BLOCKS) || !GrowthAltarEntity.ripe(state)
        || !inside(context.getLevel(), AltarType.GROWTH, origin)) return loot;
    return multiply(loot, AltarEffectRules.GROWTH_HARVEST_MULTIPLIER,
        stack -> stack.is(Tags.Items.SEEDS) || stack.is(AltarEffects.GROWTH_NO_BONUS_ITEMS));
  }

  private static ObjectArrayList<ItemStack> time(ObjectArrayList<ItemStack> loot, LootContext context, Vec3 origin) {
    Entity killed = context.getParamOrNull(LootContextParams.THIS_ENTITY);
    Player player = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
    if (!(killed instanceof LivingEntity living) || !context.hasParam(LootContextParams.DAMAGE_SOURCE)
        || !context.getQueriedLootTableId().equals(living.getLootTable().location()) || !AltarEffects.hostile(living)
        || player == null || player instanceof FakePlayer || !inside(context.getLevel(), AltarType.TIME, origin))
      return loot;
    return multiply(loot, AltarEffectRules.TIME_LOOT_MULTIPLIER, stack -> stack.is(AltarEffects.TIME_NO_BONUS_ITEMS));
  }

  static boolean inside(ServerLevel level, AltarType type, Vec3 origin) {
    return AltarRegistry.isInsideActive(level, type, Mth.floor(origin.x), Mth.floor(origin.y), Mth.floor(origin.z));
  }

  static ObjectArrayList<ItemStack> multiply(ObjectArrayList<ItemStack> loot, int factor, Predicate<ItemStack> excluded) {
    ObjectArrayList<ItemStack> result = new ObjectArrayList<>(loot.size());
    for (ItemStack stack : loot) {
      if (stack.isEmpty()) continue;
      boolean eligible = AltarEffectRules.bonusEligible(stack.getMaxStackSize(),
          stack.getComponentsPatch().isEmpty(), excluded.test(stack));
      for (int size : AltarEffectRules.multiplied(stack.getCount(), stack.getMaxStackSize(), factor, eligible))
        result.add(stack.copyWithCount(size));
    }
    return result;
  }
}
