package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Terra's Arm, the artifact of the act II ruin (the sunken workshop): a cyborg arm Terra built for
 * herself. Worn in a Curios {@code hands} slot it adds {@link #REACH_BONUS} to
 * {@code minecraft:player.block_interaction_range}, and nothing else.
 *
 * <p>The item is a curio by data only: the {@code curios:hands} item tag lets Curios accept it and
 * {@code entrelumen:curios/entities/terra_arm.json} makes sure players have that slot. The companion
 * never links against Curios. Every {@link #INTERVAL_TICKS} ticks the server asks Curios, through
 * {@link CuriosCompat}, whether the player wears an arm, and {@link #sync} makes the transient
 * {@link #REACH} modifier match. The modifier is not Curios' own: its {@code curios:attribute_modifiers}
 * component would rename the modifier after the slot ({@code curios:hands0}), and with the two
 * {@code hands} slots of the pack, taking off one of two arms would remove the modifier of the other.
 * Without Curios the item exists, can be held or stored, and does nothing.
 */
public final class TerraArm {
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");

  /** The only modifier the arm ever adds: additive, on block interaction range. */
  public static final ResourceLocation REACH = id("terra_arm_reach");
  /** Elias, 24 September 2026: +3 (was +5). */
  public static final double REACH_BONUS = 3.0;
  /** Server ticks between two checks of a player's Curios slots. */
  public static final int INTERVAL_TICKS = 10;
  /** Curios accepts items of {@code curios:<slot>} in that slot; the arm goes on the hands. */
  public static final TagKey<Item> CURIOS_HANDS =
      ItemTags.create(ResourceLocation.fromNamespaceAndPath(CuriosCompat.MOD_ID, "hands"));
  /** Chest of the act II ruin, the sunken workshop; the arm is guaranteed. */
  public static final ResourceKey<LootTable> WORKSHOP_LOOT =
      ResourceKey.create(Registries.LOOT_TABLE, id("chests/ruin_act2_workshop"));

  public static final DeferredItem<Arm> ITEM = ITEMS.register("terra_arm",
      () -> new Arm(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

  private TerraArm() {}

  static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("entrelumen", path);
  }

  static void register(IEventBus bus) {
    ITEMS.register(bus);
    NeoForge.EVENT_BUS.addListener(TerraArm::playerTick);
  }

  private static void playerTick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player && player.tickCount % INTERVAL_TICKS == 0)
      sync(player, worn(player));
  }

  /** True while an arm sits in an active Curios slot of {@code entity}; always false without Curios. */
  public static boolean worn(LivingEntity entity) {
    return CuriosCompat.equipped(entity, ITEM.get());
  }

  /**
   * Adds or removes {@link #REACH} so that it is present exactly when {@code worn}. Two arms give the
   * same single bonus. Other modifiers of the attribute are never touched. Returns whether it changed.
   */
  public static boolean sync(LivingEntity entity, boolean worn) {
    var reach = entity.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
    if (reach == null || reach.hasModifier(REACH) == worn) return false;
    if (worn) reach.addTransientModifier(modifier());
    else reach.removeModifier(REACH);
    return true;
  }

  public static AttributeModifier modifier() {
    return new AttributeModifier(REACH, REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
  }

  /** The arm: one per stack, no durability, no recipe; a line of lore and its effect. */
  public static final class Arm extends Item {
    public Arm(Properties properties) {
      super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
      tooltip.add(Component.translatable("entrelumen.terra_arm.tooltip").withStyle(ChatFormatting.GRAY));
      // Same colour as vanilla's attribute lines ("+3 Block Interaction Range" is blue).
      tooltip.add(reachLine());
    }

    /** The effect line, "+3 block reach", in the colour of vanilla attribute modifiers. */
    public static Component reachLine() {
      return Component.translatable("entrelumen.terra_arm.reach", reachText()).withStyle(ChatFormatting.BLUE);
    }

    static String reachText() {
      return REACH_BONUS == Math.rint(REACH_BONUS) ? Long.toString((long) REACH_BONUS) : Double.toString(REACH_BONUS);
    }
  }
}
