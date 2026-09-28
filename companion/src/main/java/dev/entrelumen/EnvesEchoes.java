package dev.entrelumen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesBalance.Role;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

/**
 * Echoes: the enemies the sour light condenses in the Envés (docs/design/dungeon-enves.md,
 * «Encuentros»). An echo is any mob of the pack spawned by {@link #spawn}: it carries an
 * {@link Data} attachment (attempt, floor, role, affixes, damage factor), a proper name («Eco de …»),
 * a pale aura, health and damage scaled by the attempt's World Tier and the floor, and its drops come
 * only from the Envés's own loot tables ({@code entrelumen:enves/echo_<role>}): no vanilla junk.
 * Apotheosis's spawn augments and tier augments never touch it, because the attempt's tier, not the
 * nearest player's, sets its strength.
 */
public final class EnvesEchoes {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** The sour light's colour on echoes: a pale yellow-green. */
  public static final float[] SOUR = {0.86f, 0.9f, 0.48f};
  static final ResourceLocation SCALE_ARMOR = ResourceLocation.fromNamespaceAndPath("entrelumen", "enves_tier_armor");
  static final ResourceLocation CHAMPION_KNOCKBACK = ResourceLocation.fromNamespaceAndPath("entrelumen", "enves_champion_knockback");
  static final ResourceLocation APOTHEOSIS_AUGMENTED = ResourceLocation.fromNamespaceAndPath("apotheosis", "tier_augments_applied");

  /** What makes a mob an echo; persisted with the mob. */
  public record Data(UUID attempt, int depth, String role, List<String> affixes, float damage, int tier) {
    public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("attempt").forGetter(Data::attempt),
        Codec.INT.fieldOf("depth").forGetter(Data::depth),
        Codec.STRING.fieldOf("role").forGetter(Data::role),
        Codec.STRING.listOf().fieldOf("affixes").forGetter(Data::affixes),
        Codec.FLOAT.fieldOf("damage").forGetter(Data::damage),
        Codec.INT.fieldOf("tier").forGetter(Data::tier)).apply(i, Data::new));
    public static final Data NONE = new Data(new UUID(0, 0), 0, "", List.of(), 1f, 0);

    public Data {
      affixes = List.copyOf(affixes);
    }

    public Role roleOf() {
      return Role.byId(role);
    }

    public boolean has(EnvesAffix affix) {
      return affixes.contains(affix.id);
    }

    public Tier tierOf() {
      Tier[] tiers = Tier.values();
      return tiers[Math.clamp(tier, 0, tiers.length - 1)];
    }
  }

  static Supplier<AttachmentType<Data>> ATTACHMENT;

  /** Live echoes, ticked for their aura and affixes. */
  static final Set<Mob> LIVE = new LinkedHashSet<>();

  private EnvesEchoes() {}

  static void register() {
    var bus = NeoForge.EVENT_BUS;
    bus.addListener(EnvesEchoes::onJoin);
    bus.addListener(EnvesEchoes::onLeave);
    bus.addListener(EventPriority.LOW, EnvesEchoes::onDrops);
    bus.addListener(EnvesEchoes::onExperience);
    bus.addListener(EventPriority.HIGH, EnvesEchoes::onIncomingDamage);
    bus.addListener(EnvesEchoes::onDamaged);
    bus.addListener(EventPriority.LOW, EnvesEchoes::onDeath);
  }

  public static boolean isEcho(Entity entity) {
    return entity instanceof Mob mob && ATTACHMENT != null && mob.hasData(ATTACHMENT.get());
  }

  public static Optional<Data> data(Entity entity) {
    return isEcho(entity) ? Optional.of(((Mob) entity).getData(ATTACHMENT.get())) : Optional.empty();
  }

  // ---- Spawning -----------------------------------------------------------------------------

  /** What to spawn: an entry of a table (or a bare id), its role, where it belongs and its affixes. */
  public record Spec(EnvesEchoTables.Entry entry, Role role, EnvesData.Attempt attempt, int depth, List<EnvesAffix> affixes) {}

  /**
   * Spawns an echo at {@code pos} (feet) and sets it on {@code target}. Null when neither the entry's
   * entity nor its fallback exists in this pack, or it is not a mob.
   */
  public static Mob spawn(ServerLevel level, Spec spec, Vec3 pos, ServerPlayer target) {
    String id = EnvesEchoTables.resolve(spec.entry(), known -> {
      var rl = ResourceLocation.tryParse(known);
      return rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
    });
    if (id == null) {
      LOGGER.warn("Envés: neither {} nor its fallback exist; the echo is skipped", spec.entry().entity());
      return null;
    }
    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
    Entity entity = type.create(level);
    if (!(entity instanceof Mob mob)) {
      if (entity != null) entity.discard();
      LOGGER.warn("Envés: {} is not a mob; the echo is skipped", id);
      return null;
    }
    mob.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0f);
    skipApotheosisAugments(mob);
    // The mob's own set-up (weapons, variants), without FinalizeSpawnEvent: no mod turns an echo
    // into one of its elites or invaders.
    @SuppressWarnings("deprecation")
    var ignored = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.EVENT, null);
    // No jockeys and no babies: an echo is one grown mob (a zombie's set-up may add a chicken to ride).
    if (mob.getVehicle() != null) {
      Entity vehicle = mob.getVehicle();
      mob.stopRiding();
      vehicle.discard();
    }
    mob.getPassengers().forEach(Entity::discard);
    if (mob.isBaby()) mob.setBaby(false);
    equip(mob, spec.entry());
    for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0f);
    Tier tier = spec.attempt().tier;
    var balance = EnvesContentConfig.balance();
    double health = spec.role() == Role.TREASURE ? Math.max(mob.getMaxHealth(), balance.role(Role.TREASURE).health())
        : balance.health(spec.role(), spec.entry().health(), tier, spec.depth());
    float damage = (float) balance.damage(spec.role(), spec.entry().damage(), tier, spec.depth());
    mob.setData(ATTACHMENT.get(), new Data(spec.attempt().id, spec.depth(), spec.role().id(),
        spec.affixes().stream().map(a -> a.id).toList(), damage, tier.ordinal()));
    var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
    if (maxHealth != null) maxHealth.setBaseValue(health);
    mob.setHealth(mob.getMaxHealth());
    var armor = mob.getAttribute(Attributes.ARMOR);
    double tierArmor = balance.tier(tier).armor();
    if (armor != null && tierArmor > 0 && spec.role() != Role.TREASURE)
      armor.addOrUpdateTransientModifier(new AttributeModifier(SCALE_ARMOR, tierArmor, AttributeModifier.Operation.ADD_VALUE));
    var follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
    if (follow != null && follow.getBaseValue() < 32) follow.setBaseValue(32);
    var knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
    if (knockback != null && spec.role() == Role.CHAMPION)
      knockback.addOrUpdateTransientModifier(new AttributeModifier(CHAMPION_KNOCKBACK, 0.5, AttributeModifier.Operation.ADD_VALUE));
    EnvesAffixes.apply(mob, spec.affixes());
    mob.setCustomName(name(mob, spec));
    mob.setCustomNameVisible(spec.role() != Role.ESCORT && spec.role() != Role.TREASURE);
    mob.setPersistenceRequired();
    mob.setCanPickUpLoot(false);
    if (!level.addFreshEntity(mob)) return null;
    if (target != null && spec.role() != Role.TREASURE) aim(mob, target);
    level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, SOUR[0], SOUR[1], SOUR[2]), pos.x,
        pos.y + mob.getBbHeight() / 2, pos.z, 24, mob.getBbWidth() / 2, mob.getBbHeight() / 2, mob.getBbWidth() / 2, 0.02);
    level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 0.8f, 0.6f);
    return mob;
  }

  /**
   * A standing spot near {@code pos} (feet): air at the feet and above, a solid floor under them, on
   * the same Envés floor; the nearest within {@code radius}, or {@code pos} itself when none is.
   */
  public static BlockPos safeSpot(ServerLevel level, BlockPos pos, int radius) {
    int depth = EnvesGeometry.depthAt(pos.getY());
    BlockPos best = null;
    double bestDistance = Double.MAX_VALUE;
    for (int dy = -1; dy <= 1; dy++)
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++) {
          BlockPos at = pos.offset(dx, dy, dz);
          if (EnvesGeometry.depthAt(at.getY()) != depth) continue;
          if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) continue;
          if (!level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) continue;
          if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) continue;
          double distance = dx * dx + dz * dz + dy * dy * 4;
          if (distance < bestDistance) {
            best = at;
            bestDistance = distance;
          }
        }
    return best != null ? best : pos;
  }

  /** Sets a mob on a player: its target, and a neutral mob's anger. */
  static void aim(Mob mob, ServerPlayer target) {
    mob.setTarget(target);
    if (mob instanceof NeutralMob neutral) {
      neutral.setPersistentAngerTarget(target.getUUID());
      neutral.setRemainingPersistentAngerTime(Integer.MAX_VALUE / 2);
    }
  }

  /** Apotheosis applies its monster tier augments (by the nearest player's tier) on join unless this is set. */
  @SuppressWarnings("unchecked")
  static void skipApotheosisAugments(Mob mob) {
    var type = NeoForgeRegistries.ATTACHMENT_TYPES.get(APOTHEOSIS_AUGMENTED);
    if (type == null) return;
    try {
      mob.setData((AttachmentType<Boolean>) type, Boolean.TRUE);
    } catch (ClassCastException | IllegalArgumentException e) {
      LOGGER.debug("Could not mark an echo as augmented", e);
    }
  }

  private static void equip(Mob mob, EnvesEchoTables.Entry entry) {
    for (var slot : entry.equipment().entrySet()) {
      var id = ResourceLocation.tryParse(slot.getValue());
      if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
      EquipmentSlot target = switch (slot.getKey()) {
        case "offhand" -> EquipmentSlot.OFFHAND;
        case "head" -> EquipmentSlot.HEAD;
        case "chest" -> EquipmentSlot.CHEST;
        case "legs" -> EquipmentSlot.LEGS;
        case "feet" -> EquipmentSlot.FEET;
        default -> EquipmentSlot.MAINHAND;
      };
      mob.setItemSlot(target, new ItemStack(BuiltInRegistries.ITEM.get(id)));
    }
  }

  /** «Eco de Draugr — Veloz, Vampírico», coloured by role. */
  static Component name(Mob mob, Spec spec) {
    Component kind = mob.getType().getDescription();
    MutableComponent base = spec.entry().name().isEmpty() ? Component.translatable("entrelumen.enves.echo", kind)
        : Component.translatable(spec.entry().name(), kind);
    MutableComponent out = base;
    if (!spec.affixes().isEmpty()) {
      MutableComponent list = Component.empty();
      for (int i = 0; i < spec.affixes().size(); i++) {
        if (i > 0) list.append(Component.literal(", "));
        list.append(Component.translatable(spec.affixes().get(i).langKey()));
      }
      out = Component.translatable("entrelumen.enves.echo.affixed", base, list);
    }
    int colour = switch (spec.role()) {
      case ELITE -> 0xD8E07A;
      case GUARDIAN -> 0x9FE0D0;
      case CHAMPION, BOSS -> 0xF6D77A;
      case TREASURE -> 0xC9A0F0;
      default -> 0xE6E2D6;
    };
    return out.withStyle(style -> style.withColor(TextColor.fromRgb(colour)));
  }

  // ---- Events -------------------------------------------------------------------------------

  static void onJoin(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide() && isEcho(event.getEntity())) LIVE.add((Mob) event.getEntity());
  }

  static void onLeave(EntityLeaveLevelEvent event) {
    if (event.getEntity() instanceof Mob mob) {
      LIVE.remove(mob);
      EnvesAffixes.forget(mob);
    }
  }

  /** Echo drops are the Envés's own loot table for the role, never the mob's or its equipment. */
  static void onDrops(LivingDropsEvent event) {
    var data = data(event.getEntity());
    if (data.isEmpty() || !(event.getEntity().level() instanceof ServerLevel level)) return;
    event.getDrops().clear();
    String table = switch (data.get().roleOf()) {
      case ELITE -> "echo_elite";
      case GUARDIAN -> "echo_guardian";
      case CHAMPION -> "echo_champion";
      case TREASURE -> "grottol";
      default -> null;
    };
    if (table == null) return;
    LivingEntity mob = event.getEntity();
    for (ItemStack stack : roll(level, table, mob, event.getSource())) {
      ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
      item.setDefaultPickUpDelay();
      event.getDrops().add(item);
    }
  }

  static List<ItemStack> roll(ServerLevel level, String table, LivingEntity mob, net.minecraft.world.damagesource.DamageSource source) {
    ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
        ResourceLocation.fromNamespaceAndPath("entrelumen", "enves/" + table));
    LootTable loot = level.getServer().reloadableRegistries().getLootTable(key);
    var builder = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, mob)
        .withParameter(LootContextParams.ORIGIN, mob.position()).withParameter(LootContextParams.DAMAGE_SOURCE, source)
        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
    Player killer = mob.getLastHurtByMob() instanceof Player p ? p : source.getEntity() instanceof Player p ? p : null;
    if (killer != null) builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer).withLuck(killer.getLuck());
    List<ItemStack> out = new ArrayList<>(loot.getRandomItems(builder.create(LootContextParamSets.ENTITY)));
    out.removeIf(ItemStack::isEmpty);
    return out;
  }

  static void onExperience(LivingExperienceDropEvent event) {
    data(event.getEntity()).ifPresent(data ->
        event.setDroppedExperience(EnvesContentConfig.balance().xp(data.roleOf(), data.tierOf())));
  }

  /**
   * Echo damage is scaled by its factor (tier × floor × role), whatever way it hits: blows,
   * projectiles, spells. Echoes never hurt each other. Affixes and blessings add their part.
   */
  static void onIncomingDamage(LivingIncomingDamageEvent event) {
    Entity attacker = event.getSource().getEntity();
    var attackerData = data(attacker);
    if (attackerData.isPresent() && isEcho(event.getEntity())) {
      event.setCanceled(true);
      return;
    }
    if (attackerData.isPresent()) {
      event.setAmount(event.getAmount() * attackerData.get().damage());
      EnvesAffixes.onHit(event, (Mob) attacker, attackerData.get());
    }
  }

  static void onDamaged(LivingDamageEvent.Post event) {
    Entity attacker = event.getSource().getEntity();
    data(attacker).ifPresent(data -> EnvesAffixes.afterHit(event, (Mob) attacker, data));
  }

  /** Champions, guardians and the boss report their fall. */
  static void onDeath(LivingDeathEvent event) {
    if (event.isCanceled()) return;
    var data = data(event.getEntity());
    if (data.isEmpty() || !(event.getEntity().level() instanceof ServerLevel level)) return;
    switch (data.get().roleOf()) {
      case CHAMPION -> EnvesEncounters.championFell(level, (Mob) event.getEntity(), data.get());
      case GUARDIAN -> EnvesSeals.guardianFell(level, (Mob) event.getEntity(), data.get());
      case BOSS -> EnvesBoss.defeated(level, (Mob) event.getEntity(), data.get());
      default -> {}
    }
  }

  // ---- Ticking ------------------------------------------------------------------------------

  /** Every tick: affixes; twice a second: the pale aura of each echo near a player. */
  static void tick(net.minecraft.server.MinecraftServer server) {
    if (LIVE.isEmpty()) return;
    int now = server.getTickCount();
    for (Mob mob : List.copyOf(LIVE)) {
      if (mob.isRemoved() || !mob.isAlive()) {
        if (mob.isRemoved()) LIVE.remove(mob);
        continue;
      }
      if (!(mob.level() instanceof ServerLevel level)) continue;
      var data = mob.getData(ATTACHMENT.get());
      EnvesAffixes.tick(level, mob, data, now);
      if ((now + mob.getId()) % 10 == 0 && level.getNearestPlayer(mob, 32) != null) {
        double w = mob.getBbWidth() * 0.6, h = mob.getBbHeight();
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, SOUR[0], SOUR[1], SOUR[2]),
            mob.getX(), mob.getY() + h * 0.5, mob.getZ(), data.roleOf() == Role.ESCORT ? 1 : 3, w, h * 0.4, w, 0.01);
        if (data.roleOf() == Role.CHAMPION || data.roleOf() == Role.GUARDIAN || !data.affixes().isEmpty())
          level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + h * 0.6, mob.getZ(), 1, w, h * 0.3, w, 0.005);
      }
    }
  }

  /**
   * A live echo by its UUID. The level's own lookup only sees entities of sections that finished
   * loading, which a freshly spawned echo or a chunk just loaded may not be yet; this sees every echo
   * that joined the level and has not left it.
   */
  public static Optional<Mob> find(UUID id) {
    if (id == null) return Optional.empty();
    for (Mob mob : LIVE) if (!mob.isRemoved() && mob.getUUID().equals(id)) return Optional.of(mob);
    return Optional.empty();
  }

  /** Every echo of an attempt still in the world (for the circle and the champion checks). */
  static List<Mob> of(UUID attempt) {
    List<Mob> out = new ArrayList<>();
    for (Mob mob : LIVE) if (!mob.isRemoved() && mob.getData(ATTACHMENT.get()).attempt().equals(attempt)) out.add(mob);
    return out;
  }
}
