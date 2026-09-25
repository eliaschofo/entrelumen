package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Vein resonators, Terra's tuning forks (Elias, 24 September 2026). FTB Ultimine only works while one is
 * worn in a Curios {@code charm} slot, and tier {@code n} (1 to 6) lets it break {@code 16 * n} blocks.
 *
 * <p>The pack's {@code ftbultimine-server.snbt} sets {@code max_blocks} to 0, so without a resonator
 * Ultimine selects nothing. Every {@link #INTERVAL_TICKS} ticks the server asks Curios, through
 * {@link CuriosCompat}, for the highest tier worn and makes one transient modifier, {@link #MODIFIER},
 * on {@code ftbultimine:max_blocks_modifier} match {@code reach(tier) - max_blocks}: the limit is exactly
 * the resonator's reach even if a server raised {@code max_blocks} locally, and zero without one. Two
 * resonators do not add up; the best one counts. Carried, held or put in a vanilla slot a resonator does
 * nothing. Without Curios nothing can be worn and Ultimine stays off; without FTB Ultimine the
 * resonators are plain items.
 */
public final class VeinResonator {
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");

  public static final int TIERS = VeinResonatorRules.TIERS;
  public static final int BLOCKS_PER_TIER = VeinResonatorRules.BLOCKS_PER_TIER;
  /** Server ticks between two checks of a player's Curios slots, as for Terra's Arm. */
  public static final int INTERVAL_TICKS = 10;
  /** The only modifier the resonators add: additive, on Ultimine's per-player block limit. */
  public static final ResourceLocation MODIFIER = ResourceLocation.parse(VeinResonatorRules.MODIFIER_ID);
  /** Curios accepts items of {@code curios:<slot>} in that slot; resonators go in a charm slot. */
  public static final TagKey<Item> CURIOS_CHARM =
      ItemTags.create(ResourceLocation.fromNamespaceAndPath(CuriosCompat.MOD_ID, "charm"));

  /** Index 0 holds tier 1. */
  public static final List<DeferredItem<Resonator>> BY_TIER = register();

  private VeinResonator() {}

  private static List<DeferredItem<Resonator>> register() {
    List<DeferredItem<Resonator>> items = new ArrayList<>();
    for (int tier = 1; tier <= TIERS; tier++) {
      int selected = tier;
      items.add(ITEMS.register(id(tier), () -> new Resonator(selected, properties(selected))));
    }
    return List.copyOf(items);
  }

  public static String id(int tier) {
    return VeinResonatorRules.id(tier);
  }

  static Item.Properties properties(int tier) {
    var rarity = tier <= 2 ? Rarity.COMMON : tier <= 4 ? Rarity.UNCOMMON : tier == 5 ? Rarity.RARE : Rarity.EPIC;
    var properties = new Item.Properties().stacksTo(1).rarity(rarity);
    return tier == TIERS ? properties.fireResistant() : properties;
  }

  static void register(IEventBus bus) {
    ITEMS.register(bus);
    NeoForge.EVENT_BUS.addListener(VeinResonator::playerTick);
  }

  /** Blocks one Ultimine use may break with this tier worn; 0 for tier 0 (no resonator). */
  public static int reach(int tier) {
    return VeinResonatorRules.reach(tier);
  }

  public static Item item(int tier) {
    return BY_TIER.get(tier - 1).get();
  }

  /** The highest tier worn in an active Curios slot; 0 without Curios or without a resonator. */
  public static int wornTier(LivingEntity entity) {
    for (int tier = TIERS; tier >= 1; tier--)
      if (CuriosCompat.equipped(entity, item(tier))) return tier;
    return 0;
  }

  private static void playerTick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player && player.tickCount % INTERVAL_TICKS == 0)
      sync(player, wornTier(player), UltimineCompat.configuredMaxBlocks());
  }

  /** The modifier amount that makes Ultimine's limit equal the tier's reach. */
  public static int modifierAmount(int tier, int configuredMaxBlocks) {
    return VeinResonatorRules.modifierAmount(tier, configuredMaxBlocks);
  }

  /**
   * Makes {@link #MODIFIER} present with {@link #modifierAmount} (absent when that is 0). Other
   * modifiers of the attribute are never touched. Returns whether it changed; false without Ultimine.
   */
  public static boolean sync(LivingEntity entity, int tier, int configuredMaxBlocks) {
    var attribute = UltimineCompat.maxBlocksAttribute();
    var limit = attribute == null ? null : entity.getAttribute(attribute);
    if (limit == null) return false;
    int amount = modifierAmount(tier, configuredMaxBlocks);
    var current = limit.getModifier(MODIFIER);
    if (current != null && current.amount() == amount
        && current.operation() == AttributeModifier.Operation.ADD_VALUE) return false;
    if (current == null && amount == 0) return false;
    if (current != null) limit.removeModifier(MODIFIER);
    if (amount != 0)
      limit.addTransientModifier(new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    return true;
  }

  /** One tier of the resonator: a stack of one, a line of lore and its reach. */
  public static final class Resonator extends Item {
    private final int tier;

    public Resonator(int tier, Properties properties) {
      super(properties);
      this.tier = tier;
    }

    public int tier() {
      return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
      tooltip.add(Component.translatable("entrelumen.vein_resonator.tooltip").withStyle(ChatFormatting.GRAY));
      tooltip.add(reachLine(tier));
    }

    /** "Ultimine: up to 16 blocks", blue like vanilla's attribute lines. */
    public static Component reachLine(int tier) {
      return Component.translatable("entrelumen.vein_resonator.reach", reach(tier)).withStyle(ChatFormatting.BLUE);
    }
  }
}
