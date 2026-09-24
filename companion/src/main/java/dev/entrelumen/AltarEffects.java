package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The area effects of the Altars of Peace and Time, answered by NeoForge events that ask the
 * {@link AltarRegistry} in constant time. Nothing here scans an area: each spawn, mob or projectile
 * checks its own position, and only when an altar of that type is active in its level (a slowed
 * mob or projectile is also checked so it can be released).
 */
public final class AltarEffects {
  static final String MOD = "entrelumen";
  static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(MOD, "time_altar_slow");
  static final TagKey<Block> GROWTH_ACCELERATED = TagKey.create(Registries.BLOCK, id("growth_altar_accelerated"));
  static final TagKey<Block> GROWTH_NO_BONUS_BLOCKS = TagKey.create(Registries.BLOCK, id("growth_altar_no_bonus"));
  static final TagKey<Item> GROWTH_NO_BONUS_ITEMS = TagKey.create(Registries.ITEM, id("growth_altar_no_bonus"));
  static final TagKey<Item> TIME_NO_BONUS_ITEMS = TagKey.create(Registries.ITEM, id("time_altar_no_bonus"));
  /** Attributes a slowed mob keeps a third of: movement, flight and its attack. */
  static final List<Holder<Attribute>> SLOWED_ATTRIBUTES = List.of(Attributes.MOVEMENT_SPEED,
      Attributes.FLYING_SPEED, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_KNOCKBACK, Attributes.ATTACK_SPEED);

  static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
      DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD);
  /** Marks a projectile flying in slow motion; saved with it, so a reload cannot strand it slow. */
  static final Supplier<AttachmentType<SlowedProjectile>> SLOWED_PROJECTILE = ATTACHMENTS.register(
      "time_altar_slowed", () -> AttachmentType.builder(SlowedProjectile::new)
          .serialize(new IAttachmentSerializer<ByteTag, SlowedProjectile>() {
            @Override
            public SlowedProjectile read(IAttachmentHolder holder, ByteTag tag,
                net.minecraft.core.HolderLookup.Provider provider) {
              return new SlowedProjectile();
            }

            @Override
            public ByteTag write(SlowedProjectile attachment, net.minecraft.core.HolderLookup.Provider provider) {
              return ByteTag.ONE;
            }
          }).build());

  static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
      DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD);
  static final Supplier<MapCodec<AltarLootModifier>> ALTAR_BONUS =
      LOOT_MODIFIERS.register("altar_bonus", () -> AltarLootModifier.CODEC);

  /** The state of a slowed projectile between the start and the end of its tick. */
  static final class SlowedProjectile {
    boolean snapshot;
    double px, py, pz, ux, uy, uz;
  }

  private AltarEffects() {}

  static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(MOD, path);
  }

  static void register(IEventBus bus) {
    ATTACHMENTS.register(bus);
    LOOT_MODIFIERS.register(bus);
    // Lowest priority: the altar has the last word over other mods' spawn rules.
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AltarEffects::onSpawnPlacement);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AltarEffects::onFinalizeSpawn);
    NeoForge.EVENT_BUS.addListener(AltarEffects::onEntityTickPre);
    NeoForge.EVENT_BUS.addListener(AltarEffects::onEntityTickPost);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AltarEffects::onDeath);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOW, AltarEffects::onExperience);
  }

  // ---- Who counts as hostile -------------------------------------------------------------------

  static boolean boss(EntityType<?> type) {
    return type.is(Tags.EntityTypes.BOSSES);
  }

  /** Hostile by kind, before the mob exists: the monster category, bosses excepted. */
  static boolean hostileType(EntityType<?> type) {
    return type.getCategory() == MobCategory.MONSTER && !boss(type);
  }

  /** A hostile, non-boss entity: an enemy or of the monster category. Players never are. */
  static boolean hostile(Entity entity) {
    return !(entity instanceof Player) && (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER)
        && !boss(entity.getType());
  }

  // ---- Altar of Peace --------------------------------------------------------------------------

  /** Spawn rules, before the mob is created: the cheap path every natural spawn attempt takes. */
  static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
    if (event.getSpawnType() != net.minecraft.world.entity.MobSpawnType.NATURAL
        || !hostileType(event.getEntityType())) return;
    ServerLevel level = event.getLevel().getLevel();
    if (!AltarRegistry.anyActive(level, AltarType.PEACE)) return;
    boolean covered = AltarRegistry.isInsideActive(level, AltarType.PEACE, event.getPos());
    if (AltarEffectRules.peaceBlocks(event.getSpawnType(), true, false, covered))
      event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
  }

  /** The catch-all for mods that spawn "natural" mobs without vanilla's spawn rules. */
  static void onFinalizeSpawn(FinalizeSpawnEvent event) {
    if (event.getSpawnType() != net.minecraft.world.entity.MobSpawnType.NATURAL
        || !hostileType(event.getEntity().getType())) return;
    ServerLevel level = event.getLevel().getLevel();
    if (!AltarRegistry.anyActive(level, AltarType.PEACE)) return;
    if (AltarRegistry.isInsideActive(level, AltarType.PEACE, Mth.floor(event.getX()), Mth.floor(event.getY()),
        Mth.floor(event.getZ())))
      event.setSpawnCancelled(true);
  }

  // ---- Altar of Time: mobs -------------------------------------------------------------------

  static void onEntityTickPre(EntityTickEvent.Pre event) {
    Entity entity = event.getEntity();
    if (!(entity.level() instanceof ServerLevel level)) return;
    if (entity instanceof Projectile projectile) projectilePre(level, projectile);
    else if (entity instanceof Mob mob && (mob.tickCount + mob.getId()) % AltarEffectRules.MOB_CHECK_INTERVAL == 0)
      checkMob(level, mob);
  }

  static void onEntityTickPost(EntityTickEvent.Post event) {
    if (event.getEntity() instanceof Projectile projectile && !projectile.level().isClientSide)
      projectilePost(projectile);
  }

  /** Slows a hostile mob inside an active Altar of Time and releases it outside. */
  static void checkMob(ServerLevel level, Mob mob) {
    boolean slowed = slowed(mob);
    if (!slowed && !AltarRegistry.anyActive(level, AltarType.TIME)) return;
    boolean inside = mob.isAlive() && hostile(mob)
        && AltarRegistry.isInsideActive(level, AltarType.TIME, mob.getBlockX(), mob.getBlockY(), mob.getBlockZ());
    if (inside && !slowed) slow(mob);
    else if (!inside && slowed) release(mob);
  }

  static boolean slowed(LivingEntity mob) {
    AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
    return speed != null && speed.hasModifier(SLOW_ID);
  }

  /** Transient modifiers: never saved, so a reload or a dimension change cannot strand them. */
  static void slow(LivingEntity mob) {
    for (Holder<Attribute> attribute : SLOWED_ATTRIBUTES) {
      AttributeInstance instance = mob.getAttribute(attribute);
      if (instance != null && !instance.hasModifier(SLOW_ID))
        instance.addTransientModifier(new AttributeModifier(SLOW_ID, AltarEffectRules.SLOW_MODIFIER,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
  }

  static void release(LivingEntity mob) {
    for (Holder<Attribute> attribute : SLOWED_ATTRIBUTES) {
      AttributeInstance instance = mob.getAttribute(attribute);
      if (instance != null) instance.removeModifier(SLOW_ID);
    }
  }

  static void onDeath(LivingDeathEvent event) {
    if (!event.getEntity().level().isClientSide && slowed(event.getEntity())) release(event.getEntity());
  }

  /** Experience of a hostile mob a real player kills inside an active Altar of Time: three times. */
  static void onExperience(LivingExperienceDropEvent event) {
    LivingEntity entity = event.getEntity();
    if (!(entity.level() instanceof ServerLevel level) || !hostile(entity)) return;
    Player player = event.getAttackingPlayer();
    if (player == null || player instanceof FakePlayer) return;
    if (!AltarRegistry.isInsideActive(level, AltarType.TIME, entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()))
      return;
    event.setDroppedExperience(event.getDroppedExperience() * AltarEffectRules.TIME_XP_MULTIPLIER);
  }

  // ---- Altar of Time: projectiles --------------------------------------------------------------

  /**
   * Before a projectile's tick: slow it on entering the area (only projectiles a hostile, non-boss
   * mob fired), release it on leaving, and remember where it starts and how fast it goes.
   */
  static void projectilePre(ServerLevel level, Projectile projectile) {
    boolean slowed = projectile.hasData(SLOWED_PROJECTILE);
    if (!slowed && !AltarRegistry.anyActive(level, AltarType.TIME)) return;
    boolean inside = AltarRegistry.isInsideActive(level, AltarType.TIME, Mth.floor(projectile.getX()),
        Mth.floor(projectile.getY()), Mth.floor(projectile.getZ()));
    if (!slowed) {
      if (!inside || !hostileSource(projectile)) return;
      enter(projectile);
    } else if (!inside) {
      leave(projectile);
      return;
    }
    SlowedProjectile state = projectile.getData(SLOWED_PROJECTILE);
    Vec3 motion = projectile.getDeltaMovement();
    state.px = projectile.getX();
    state.py = projectile.getY();
    state.pz = projectile.getZ();
    state.ux = motion.x;
    state.uy = motion.y;
    state.uz = motion.z;
    state.snapshot = true;
  }

  /**
   * After the tick: when the projectile flew freely (it moved exactly by its slowed velocity), keep a
   * third of the change its tick made and a ninth of gravity; see {@link AltarEffectRules#slowStep}.
   * A hit, a bounce or a deflection keeps the projectile's own result.
   */
  static void projectilePost(Projectile projectile) {
    if (projectile.isRemoved() || !projectile.hasData(SLOWED_PROJECTILE)) return;
    SlowedProjectile state = projectile.getData(SLOWED_PROJECTILE);
    if (!state.snapshot) return;
    state.snapshot = false;
    double dx = projectile.getX() - (state.px + state.ux), dy = projectile.getY() - (state.py + state.uy),
        dz = projectile.getZ() - (state.pz + state.uz);
    if (dx * dx + dy * dy + dz * dz > 1.0E-8) return;
    Vec3 post = projectile.getDeltaMovement();
    double gravity = projectile instanceof AbstractArrow arrow && arrow.isNoPhysics() ? 0 : projectile.getGravity();
    double[] next = AltarEffectRules.slowStep(new double[] {state.ux, state.uy, state.uz},
        new double[] {post.x, post.y, post.z}, gravity);
    projectile.setDeltaMovement(next[0], next[1], next[2]);
    // Clients predict with full gravity: resend position and motion every tick while slowed.
    projectile.hasImpulse = true;
  }

  static boolean hostileSource(Projectile projectile) {
    Entity owner = projectile.getOwner();
    return owner != null && hostile(owner);
  }

  static void enter(Projectile projectile) {
    projectile.setData(SLOWED_PROJECTILE, new SlowedProjectile());
    projectile.setDeltaMovement(projectile.getDeltaMovement().scale(AltarEffectRules.SLOW));
    if (projectile instanceof AbstractHurtingProjectile hurting) hurting.accelerationPower *= AltarEffectRules.SLOW;
    projectile.hasImpulse = true;
  }

  static void leave(Projectile projectile) {
    projectile.removeData(SLOWED_PROJECTILE);
    projectile.setDeltaMovement(projectile.getDeltaMovement().scale(1 / AltarEffectRules.SLOW));
    if (projectile instanceof AbstractHurtingProjectile hurting) hurting.accelerationPower /= AltarEffectRules.SLOW;
    projectile.hasImpulse = true;
  }
}
