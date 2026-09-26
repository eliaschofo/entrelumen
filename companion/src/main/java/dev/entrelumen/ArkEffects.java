package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * The Ark as a global beacon: every two seconds each online player gets the effects of the modules
 * active in their team's Ark, wherever they are, and loses those of modules that went out. Attribute
 * bonuses are transient modifiers with Entrelumen IDs, so they never outlive the Ark or the mod;
 * Regeneration and Hero of the Village are infinite, ambient effects without particles, recognised
 * as ours by that shape and removed only then.
 *
 * <p>Numbers (server config {@code entrelumen-ark-server.toml}; see {@code ark-modules-v2.md}):
 * Engineering charges every item with Forge Energy by 0.5 % of its capacity per second, at least
 * 100 FE/s and at most 10,000 FE/s; Arcane adds 50 % per Ark level to maximum mana and mana
 * regeneration; Nature gives Regeneration of the Ark's level and halves hunger; Logistics gives Hero
 * of the Village of the Ark's level. The level is 1 plus one per beacon on the columns (at most 5).
 * Exploration only marks the player as a traveller (a synced attribute read by the Waystones hook).
 */
public final class ArkEffects {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final int PERIOD_TICKS = 40;
  static final ResourceLocation TRAVELLER_ID = id("ark_traveller"), MANA = id("ark_overflowing_mana");
  static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, "entrelumen");
  /**
   * 1 while the player's team has the Exploration module active, else 0. Synced to the owning client,
   * so the Waystones condition answers the same on both sides (the warp list shows no cost).
   */
  public static final DeferredHolder<Attribute, Attribute> TRAVELLER = ATTRIBUTES.register("ark_traveller",
      () -> new RangedAttribute("attribute.entrelumen.ark_traveller", 0.0, 0.0, 1.0).setSyncable(true));

  public static final ModConfigSpec SPEC;
  static final ModConfigSpec.DoubleValue CHARGE_PERCENT, MANA_BONUS, HUNGER_FACTOR;
  static final ModConfigSpec.IntValue CHARGE_MIN, CHARGE_MAX, HERO_AMPLIFIER;
  static final ModConfigSpec.ConfigValue<List<? extends String>> MANA_ATTRIBUTES;
  /**
   * Maximum-mana and regeneration attributes of the pinned magic mods, read from their JARs on 25
   * September 2026: Ars Nouveau 5.13.1 ({@code PerkAttributes}), Iron's Spells 3.16.3
   * ({@code AttributeRegistry}) and Psi 110 ({@code ModAttributes}). Missing ones are skipped.
   */
  public static final List<String> DEFAULT_MANA_ATTRIBUTES = List.of(
      "ars_nouveau:ars_nouveau.perk.max_mana", "ars_nouveau:ars_nouveau.perk.mana_regen",
      "irons_spellbooks:max_mana", "irons_spellbooks:mana_regen", "psi:total_psi", "psi:regen");

  static {
    var builder = new ModConfigSpec.Builder();
    builder.push("ark");
    CHARGE_PERCENT = builder.comment("Engineering module: share of an item's energy capacity charged per second.")
        .translation("entrelumen.config.ark.charge_percent").defineInRange("chargePercentPerSecond", 0.5, 0.0, 100.0);
    CHARGE_MIN = builder.comment("Engineering module: least FE per second offered to an item that is not full.")
        .translation("entrelumen.config.ark.charge_min").defineInRange("chargeMinPerSecond", 100, 0, 1_000_000);
    CHARGE_MAX = builder.comment("Engineering module: most FE per second offered to one item.")
        .translation("entrelumen.config.ark.charge_max").defineInRange("chargeMaxPerSecond", 10_000, 0, 10_000_000);
    MANA_BONUS = builder.comment("Arcane module: bonus to maximum mana and mana regeneration per Ark level (0.5 = +50%).")
        .translation("entrelumen.config.ark.mana_bonus").defineInRange("manaBonus", 0.5, 0.0, 10.0);
    MANA_ATTRIBUTES = builder.comment("Arcane module: attributes that take the bonus; absent ones are ignored.")
        .translation("entrelumen.config.ark.mana_attributes")
        .defineListAllowEmpty("manaAttributes", DEFAULT_MANA_ATTRIBUTES, () -> "",
            value -> value instanceof String text && ResourceLocation.tryParse(text) != null);
    HUNGER_FACTOR = builder.comment("Nature module: hunger rate while the module is active (0.5 = half).")
        .translation("entrelumen.config.ark.hunger_factor").defineInRange("hungerFactor", 0.5, 0.0, 1.0);
    HERO_AMPLIFIER = builder.comment("Logistics module: Hero of the Village level minus one at Ark level 1;",
            "each beacon on the Ark adds one level. -1 turns the discount off.")
        .translation("entrelumen.config.ark.hero_amplifier").defineInRange("heroAmplifier", 0, -1, 4);
    builder.pop();
    SPEC = builder.build();
  }

  /** Food data of players whose Nature module is active; read by {@code FoodDataMixin}. */
  private static final Set<FoodData> SLOW_HUNGER = Collections.newSetFromMap(new WeakHashMap<>());
  /** Nanoseconds of the last pass and the most of any pass since start, for {@code /entrelumen admin ark}. */
  static volatile long lastPassNanos, maxPassNanos, lastChargeNanos;
  static volatile int lastPassPlayers, lastChargedItems;

  private ArkEffects() {}

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("entrelumen", path);
  }

  static void register(IEventBus modBus) {
    ATTRIBUTES.register(modBus);
    modBus.addListener((EntityAttributeModificationEvent event) -> event.add(EntityType.PLAYER, TRAVELLER));
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
      if (event.getServer().getTickCount() % PERIOD_TICKS == 0) pass(event.getServer());
    });
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> refresh(event.getEntity()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> refresh(event.getEntity()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> refresh(event.getEntity()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
        SLOW_HUNGER.remove(event.getEntity().getFoodData()));
  }

  private static void refresh(Player player) {
    if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isRemoved())
      apply(serverPlayer, ArkState.status(serverPlayer), false);
  }

  static ArkRules.Charging charging() {
    try {
      return new ArkRules.Charging(CHARGE_PERCENT.getAsDouble(), CHARGE_MIN.getAsInt(), CHARGE_MAX.getAsInt());
    } catch (IllegalStateException notLoaded) {
      return ArkRules.Charging.DEFAULT;
    }
  }

  static double manaBonus() {
    try {
      return MANA_BONUS.getAsDouble();
    } catch (IllegalStateException notLoaded) {
      return MANA_BONUS.getDefault();
    }
  }

  static List<String> manaAttributes() {
    try {
      return List.copyOf(MANA_ATTRIBUTES.get());
    } catch (IllegalStateException notLoaded) {
      return DEFAULT_MANA_ATTRIBUTES;
    }
  }

  static double hungerFactor() {
    try {
      return HUNGER_FACTOR.getAsDouble();
    } catch (IllegalStateException notLoaded) {
      return HUNGER_FACTOR.getDefault();
    }
  }

  static int heroAmplifier() {
    try {
      return HERO_AMPLIFIER.getAsInt();
    } catch (IllegalStateException notLoaded) {
      return HERO_AMPLIFIER.getDefault();
    }
  }

  /** One pass over every online player; each team's status is read once. */
  static void pass(MinecraftServer server) {
    long start = System.nanoTime();
    long charge = 0;
    int charged = 0;
    Map<UUID, ArkRules.Status> teams = new HashMap<>();
    ArkData data = ArkData.get(server);
    List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
    for (ServerPlayer player : players) {
      if (player.isRemoved()) continue;
      ArkRules.Status ark = teams.computeIfAbsent(CampaignActions.campaignId(player), data::status);
      long before = System.nanoTime();
      charged += apply(player, ark, true);
      charge += System.nanoTime() - before;
      ArkCommerce.observeVisits(player);
      ArkMigration.deliverRefunds(player);
    }
    lastChargeNanos = charge;
    lastChargedItems = charged;
    lastPassPlayers = players.size();
    lastPassNanos = System.nanoTime() - start;
    maxPassNanos = Math.max(maxPassNanos, lastPassNanos);
  }

  /**
   * Brings a player's effects in line with their team's Ark; {@code charge} also runs one period of
   * wireless charging. Returns the number of items that took energy.
   */
  public static int apply(ServerPlayer player, ArkRules.Status ark, boolean charge) {
    int level = Math.max(1, ark.level());
    modifier(player.getAttribute(TRAVELLER), TRAVELLER_ID, 1.0, AttributeModifier.Operation.ADD_VALUE,
        ark.active(ArkRules.Module.EXPLORATION));

    boolean arcane = ark.active(ArkRules.Module.ARCANE);
    for (String id : manaAttributes()) manaModifier(player, id, manaBonus() * level, arcane);

    boolean vital = ark.active(ArkRules.Module.NATURE);
    ambient(player, MobEffects.REGENERATION, level - 1, vital);
    if (vital) SLOW_HUNGER.add(player.getFoodData());
    else SLOW_HUNGER.remove(player.getFoodData());

    int hero = heroAmplifier();
    ambient(player, MobEffects.HERO_OF_THE_VILLAGE, Math.min(4, Math.max(0, hero) + level - 1),
        ark.active(ArkRules.Module.LOGISTICS) && hero >= 0);

    if (charge && ark.active(ArkRules.Module.ENGINEERING) && !player.isSpectator())
      return charge(player, charging(), PERIOD_TICKS / 20.0);
    return 0;
  }

  /** Whether the player travels with the Ark's Exploration module; readable on the client too. */
  public static boolean traveller(Player player) {
    AttributeInstance traveller = player.getAttribute(TRAVELLER);
    return traveller != null && traveller.getValue() > 0;
  }

  private static void modifier(AttributeInstance instance, ResourceLocation id, double amount,
      AttributeModifier.Operation operation, boolean on) {
    if (instance == null) return;
    AttributeModifier current = instance.getModifier(id);
    if (on) {
      if (current != null && current.amount() == amount && current.operation() == operation) return;
      if (current != null) instance.removeModifier(id);
      instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    } else if (current != null) {
      instance.removeModifier(id);
    }
  }

  private static void manaModifier(ServerPlayer player, String attributeId, double bonus, boolean on) {
    ResourceLocation id = ResourceLocation.tryParse(attributeId);
    if (id == null) return;
    Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(id).<Holder<Attribute>>map(holder -> holder)
        .orElse(null);
    if (attribute == null) return;
    modifier(player.getAttribute(attribute), MANA, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
  }

  /**
   * An infinite, ambient effect: ours. Beacons and potions never last forever, and a beacon that
   * overlaps it may turn its particles on, so visibility is not part of the mark.
   */
  static boolean ours(MobEffectInstance instance) {
    return instance != null && instance.isInfiniteDuration() && instance.isAmbient();
  }

  private static void ambient(ServerPlayer player, Holder<MobEffect> effect, int amplifier, boolean on) {
    MobEffectInstance current = player.getEffect(effect);
    if (!on) {
      if (ours(current)) player.removeEffect(effect);
      return;
    }
    if (ours(current) && current.getAmplifier() == amplifier && !current.isVisible()) return;
    // Ours at another level (a beacon added or removed): replace it.
    if (ours(current) && current.getAmplifier() != amplifier) {
      player.removeEffect(effect);
      current = null;
    }
    // A stronger effect from elsewhere, or someone else's infinite one, is left alone.
    if (current != null && current.getAmplifier() > amplifier) return;
    if (current != null && current.isInfiniteDuration() && !current.isAmbient()) return;
    // Adding again also turns off particles a beacon switched on over ours.
    player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, true, false, true));
  }

  /** Called by {@code FoodDataMixin} for every exhaustion added: the Nature module halves it. */
  public static float exhaustion(FoodData food, float exhaustion) {
    if (SLOW_HUNGER.isEmpty() || exhaustion <= 0 || !SLOW_HUNGER.contains(food)) return exhaustion;
    return (float) (exhaustion * hungerFactor());
  }

  static boolean slowHunger(FoodData food) {
    return SLOW_HUNGER.contains(food);
  }

  // ---- Engineering: wireless charging ----------------------------------------------------------

  /** Every item stack the player carries or wears, curios included: live stacks, charged in place. */
  static List<ItemStack> carried(ServerPlayer player) {
    List<ItemStack> stacks = new ArrayList<>();
    var inventory = player.getInventory();
    stacks.addAll(inventory.items);
    stacks.addAll(inventory.armor);
    stacks.addAll(inventory.offhand);
    stacks.addAll(CuriosCompat.equippedStacks(player));
    return stacks;
  }

  /** Charges every item with energy for {@code seconds}; returns how many took some. */
  static int charge(ServerPlayer player, ArkRules.Charging charging, double seconds) {
    int charged = 0;
    for (ItemStack stack : carried(player)) {
      if (stack.isEmpty()) continue;
      try {
        var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy == null || !energy.canReceive()) continue;
        int offer = ArkRules.chargeOffer(energy.getMaxEnergyStored(), energy.getEnergyStored(), charging, seconds);
        if (offer > 0 && energy.receiveEnergy(offer, false) > 0) charged++;
      } catch (RuntimeException broken) {
        LOGGER.debug("Skipped an item whose energy capability failed: {}", stack, broken);
      }
    }
    if (charged > 0) player.getInventory().setChanged();
    return charged;
  }
}
