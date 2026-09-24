package dev.entrelumen;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Server glue for {@link SatietyOverflow}. Two native paths feed the same formula, cooldown and
 * glut:
 *
 * <ul>
 *   <li>Items: every food or drink that goes through the vanilla use cycle, from any mod. The last
 *       {@code LivingEntityUseItemEvent.Tick} before completion records hunger and saturation, and
 *       {@code Finish} (fired after the food is applied) compares that snapshot with the item's
 *       food values.
 *   <li>Blocks: cake, candle cake, Farmer's Delight pies and any block that feeds the player in
 *       place. {@code PlayerInteractEvent.RightClickBlock} opens a bite window before the block
 *       runs. While it is open, {@code FoodDataMixin} reports every {@code FoodData.add} on that
 *       player's food data together with the state just before the bite. The window closes after
 *       the interaction, at the next server tick boundary, and the bite is converted once.
 * </ul>
 */
public final class SatietyOverflowEvents {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final String TAG = "entrelumen_satiety";
  static final ResourceLocation RESOURCE =
      ResourceLocation.fromNamespaceAndPath("entrelumen", "satiety/overflow.json");

  private static volatile SatietyOverflow.Settings settings = SatietyOverflow.DEFAULTS;
  private static final Map<UUID, Snapshot> BEFORE = new HashMap<>();
  /** Open block-bite windows by food data identity; read by the mixin on any thread. */
  private static final Map<FoodData, Bite> BITES = new ConcurrentHashMap<>();

  record Snapshot(int food, float saturation, long tick) {}

  /** Surplus measured while one block interaction runs. */
  private static final class Bite {
    final ServerPlayer player;
    double points;

    Bite(ServerPlayer player) {
      this.player = player;
    }
  }

  /** What one meal did; returned for tests and diagnostics. */
  public record Result(double points, double effective, boolean coolingDown, List<MobEffectInstance> granted) {
    static final Result NONE = new Result(0, 0, false, List.of());
  }

  private SatietyOverflowEvents() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SatietyOverflowEvents::onUseTick);
    // First on Finish, so a Spice of Life milestone message sent in the same tick is the one shown.
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SatietyOverflowEvents::onUseFinish);
    NeoForge.EVENT_BUS.addListener(SatietyOverflowEvents::onLogout);
    // First, and even when cancelled: a mod may eat the block from its own handler and cancel the
    // event (Amendments turns a bitten cake into its directional cake). A window only measures food
    // actually added, so a protection cancel that eats nothing converts nothing.
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerInteractEvent.RightClickBlock.class,
        SatietyOverflowEvents::onRightClickBlock);
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> closeBites());
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> closeBites());
    NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new ReloadListener()));
  }

  public static SatietyOverflow.Settings settings() {
    return settings;
  }

  /** Test hook: swaps the active settings and returns the previous ones. */
  static SatietyOverflow.Settings swapSettings(SatietyOverflow.Settings replacement) {
    var previous = settings;
    settings = replacement;
    return previous;
  }

  static boolean counts(ServerPlayer player) {
    return !(player instanceof FakePlayer) && !player.isCreative() && !player.isSpectator();
  }

  static void onUseTick(LivingEntityUseItemEvent.Tick event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !counts(player)) return;
    if (event.getItem().getFoodProperties(player) == null) return;
    // An item meal is measured by its own snapshot, never also as a block bite.
    closeBite(player.getFoodData());
    var food = player.getFoodData();
    BEFORE.put(player.getUUID(),
        new Snapshot(food.getFoodLevel(), food.getSaturationLevel(), player.level().getGameTime()));
  }

  static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    var before = BEFORE.remove(player.getUUID());
    if (before == null || before.tick() != player.level().getGameTime() || !counts(player)) return;
    FoodProperties food = event.getItem().getFoodProperties(player);
    if (food == null) return;
    convert(player, before.food(), before.saturation(), food.nutrition(), food.saturation(),
        settings, player.getRandom());
  }

  private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    BEFORE.remove(event.getEntity().getUUID());
    BITES.remove(event.getEntity().getFoodData());
  }

  /** Before the block runs: opens a bite window for this player, closing any previous one. */
  static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !counts(player)) return;
    closeBite(player.getFoodData());
    BITES.put(player.getFoodData(), new Bite(player));
  }

  /**
   * Called by {@code FoodDataMixin} at the head of {@code FoodData.add}, before vanilla clamps the
   * values. Only food data with an open bite window is measured; everything else returns at once.
   */
  public static void onFoodAdded(FoodData food, int nutrition, float saturation) {
    if (BITES.isEmpty()) return;
    var bite = BITES.get(food);
    if (bite == null) return;
    bite.points += SatietyOverflow.overflow(food.getFoodLevel(), food.getSaturationLevel(), nutrition, saturation);
  }

  /** After the interaction: converts the surplus of every open bite window. */
  static void closeBites() {
    if (BITES.isEmpty()) return;
    for (var food : List.copyOf(BITES.keySet())) closeBite(food);
  }

  private static void closeBite(FoodData food) {
    var bite = BITES.remove(food);
    if (bite == null || !(bite.points > 0) || bite.player.isRemoved() || !counts(bite.player)) return;
    convertPoints(bite.player, bite.points, settings, bite.player.getRandom());
  }

  static long now(ServerPlayer player) {
    return player.server.overworld().getGameTime();
  }

  private static Holder<MobEffect> holder(ResourceLocation id) {
    return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
  }

  /**
   * Converts the surplus of one meal. {@code food}/{@code saturation} are the values before the
   * meal; {@code nutrition}/{@code foodSaturation} are the food's own values.
   */
  public static Result convert(ServerPlayer player, int food, float saturation, int nutrition,
      float foodSaturation, SatietyOverflow.Settings settings, RandomSource random) {
    return convertPoints(player, SatietyOverflow.overflow(food, saturation, nutrition, foodSaturation),
        settings, random);
  }

  /** Applies glut, cooldown and the plan to the surplus of one meal or bite. */
  static Result convertPoints(ServerPlayer player, double points, SatietyOverflow.Settings settings,
      RandomSource random) {
    if (!settings.enabled() || !(points > 0)) return Result.NONE;
    long now = now(player);
    CompoundTag tag = player.getPersistentData().getCompound(TAG);
    double glut = SatietyOverflow.decay(tag.getDouble("glut"), now - tag.getLong("glut_at"),
        settings.glutHalfLifeTicks());
    double effective = SatietyOverflow.effective(points, glut, settings.glutSoftness());
    tag.putDouble("glut", glut + points);
    tag.putLong("glut_at", now);
    boolean cooling = tag.contains("granted_at") && now - tag.getLong("granted_at") < settings.cooldownTicks()
        && now >= tag.getLong("granted_at");
    List<MobEffectInstance> granted = new ArrayList<>();
    var plan = cooling ? java.util.Optional.<SatietyOverflow.Plan>empty()
        : SatietyOverflow.plan(effective, settings);
    if (plan.isPresent()) {
      var p = plan.get();
      List<SatietyOverflow.Entry> eligible = new ArrayList<>();
      for (var entry : settings.pool()) {
        var effect = holder(entry.effect());
        if (effect == null) continue;
        var existing = player.getEffect(effect);
        if (SatietyOverflow.improves(existing == null ? -1 : existing.getAmplifier(),
            existing == null ? 0 : existing.isInfiniteDuration() ? -1 : existing.getDuration(),
            SatietyOverflow.amplifier(entry, p), SatietyOverflow.durationTicks(entry, p)))
          eligible.add(entry);
      }
      for (var entry : SatietyOverflow.pick(eligible, p.count(), random::nextInt)) {
        var instance = new MobEffectInstance(holder(entry.effect()), SatietyOverflow.durationTicks(entry, p),
            SatietyOverflow.amplifier(entry, p), true, true, true);
        if (player.addEffect(instance)) granted.add(new MobEffectInstance(instance));
      }
      if (!granted.isEmpty()) {
        tag.putLong("granted_at", now);
        if (settings.announce()) announce(player, granted);
      }
    }
    player.getPersistentData().put(TAG, tag);
    return new Result(points, effective, cooling, List.copyOf(granted));
  }

  private static void announce(ServerPlayer player, List<MobEffectInstance> granted) {
    MutableComponent names = Component.empty();
    for (int i = 0; i < granted.size(); i++) {
      var instance = granted.get(i);
      if (i > 0) names.append(", ");
      Component name = instance.getEffect().value().getDisplayName();
      names.append(instance.getAmplifier() > 0
          ? Component.translatable("potion.withAmplifier", name,
              Component.translatable("potion.potency." + instance.getAmplifier()))
          : name);
    }
    player.displayClientMessage(Component.translatable("entrelumen.satiety.overflow", names), true);
    player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getEyeY() - 0.2,
        player.getZ(), 5, 0.3, 0.2, 0.3, 0.01);
  }

  /** Effects a pool may use: registered, not instant and not harmful. */
  static boolean usable(ResourceLocation id) {
    var effect = BuiltInRegistries.MOB_EFFECT.get(id);
    return effect != null && !effect.isInstantenous() && effect.getCategory() != MobEffectCategory.HARMFUL;
  }

  /** Loads {@link #RESOURCE}; a rejected file keeps the previous settings instead of failing the reload. */
  static final class ReloadListener extends SimplePreparableReloadListener<SatietyOverflow.Settings> {
    @Override
    protected SatietyOverflow.Settings prepare(ResourceManager manager, ProfilerFiller profiler) {
      var resource = manager.getResource(RESOURCE);
      if (resource.isEmpty()) return SatietyOverflow.DEFAULTS;
      try (var reader = resource.get().openAsReader()) {
        return SatietyOverflow.parse(JsonParser.parseReader(reader), SatietyOverflowEvents::usable, LOGGER::warn);
      } catch (Exception e) {
        LOGGER.error("Rejected {} from datapack {}; previous satiety overflow settings retained. {}",
            RESOURCE, resource.get().sourcePackId(), e.getMessage());
        return settings;
      }
    }

    @Override
    protected void apply(SatietyOverflow.Settings loaded, ResourceManager manager, ProfilerFiller profiler) {
      settings = loaded;
      LOGGER.info("Satiety overflow {} with {} pool effects", loaded.enabled() ? "enabled" : "disabled",
          loaded.pool().size());
    }
  }
}
