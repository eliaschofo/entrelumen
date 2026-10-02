package dev.entrelumen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.joml.Vector3f;

/**
 * The Sour Shackle (Grillete Agrio), the Sour Light's unique curio (docs/design/dungeon-enves.md,
 * «El Grillete Agrio»): worn in a Curios {@code bracelet} slot, the wearer's melee hits leave marks and
 * the fifth bursts around its target ({@link SourShackleRules}).
 *
 * <p>Like Terra's Arm it is a curio by data only ({@code curios:bracelet} item tag and
 * {@code entrelumen:curios/entities/sour_shackle.json}); the companion asks Curios through
 * {@link CuriosCompat} and never links against it. Without Curios the item exists and does nothing.
 *
 * <p>A mark is a melee hit that lands: {@link AttackEntityEvent} names the swing's own target, and the
 * damage it then takes from that swing ({@link LivingDamageEvent.Post}, same tick, a player attack
 * from the wearer) counts once. Sweeps, thorns, projectiles and the burst itself never mark. A hit that
 * does no damage (invulnerability frames, a cancelled hurt) does not mark either.
 *
 * <p>The burst is {@code entrelumen:sour_burst}, a magic-like damage type that ignores armour (its tags
 * follow vanilla {@code minecraft:magic}, plus {@code bypasses_cooldown} so the target that has just been
 * hit takes it whole). It spares the wearer, their allies (vanilla team or FTB party), players they
 * cannot harm, tamed pets and armour stands.
 *
 * <p>The marks live on the server and are sent to the wearer and whoever sees them ({@link Marks}), so
 * the cuff on the wrist shows them in first and third person. Whether a player ever had a shackle is
 * a saved, death-proof flag ({@link #OWNED}), set when the boss chest rolls one for them
 * ({@link Drop}) or when one ticks in their inventory; the chest gives {@link SourShackleRules#REPEAT_SHARDS}
 * shards instead to a player who has the flag.
 */
public final class SourShackle {
  private SourShackle() {}

  static final String MOD = "entrelumen";
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD);
  static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD);
  static final DeferredRegister<LootPoolEntryType> LOOT_ENTRIES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, MOD);

  static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(MOD, path);
  }

  public static final DeferredItem<Shackle> ITEM = ITEMS.register("sour_shackle",
      () -> new Shackle(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

  /** Curios accepts items of {@code curios:<slot>} in that slot; the shackle goes on the bracelet. */
  public static final TagKey<Item> CURIOS_BRACELET =
      ItemTags.create(ResourceLocation.fromNamespaceAndPath(CuriosCompat.MOD_ID, "bracelet"));
  public static final ResourceKey<DamageType> BURST = ResourceKey.create(Registries.DAMAGE_TYPE, id("sour_burst"));

  /** The player has had a shackle: saved, and kept through death. */
  public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> OWNED = ATTACHMENTS.register("sour_shackle_owned",
      () -> AttachmentType.builder(() -> Boolean.FALSE).serialize(Codec.BOOL).copyOnDeath().build());
  /** The wearer's marks on the server; not saved (a relog or a death starts a fresh cycle). */
  public static final DeferredHolder<AttachmentType<?>, AttachmentType<Wearer>> WEARER = ATTACHMENTS.register("sour_shackle_wearer",
      () -> AttachmentType.builder(Wearer::new).build());
  /** What a client knows of a player's cuff, from {@link Marks}; never on the server's players. */
  public static final DeferredHolder<AttachmentType<?>, AttachmentType<View>> VIEW = ATTACHMENTS.register("sour_shackle_view",
      () -> AttachmentType.builder(() -> View.NONE).build());

  public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> LOOT_DROP =
      LOOT_ENTRIES.register("sour_shackle", () -> new LootPoolEntryType(Drop.CODEC));

  /** Server ticks between two checks of whether a player wears the shackle. */
  public static final int INTERVAL_TICKS = 10;

  /** Whether a player wears a shackle in an active Curios slot. The isolated GameTests, without Curios, swap it. */
  static Predicate<Player> wearing = player -> CuriosCompat.equipped(player, ITEM.get());

  static void register(IEventBus bus) {
    ITEMS.register(bus);
    ATTACHMENTS.register(bus);
    LOOT_ENTRIES.register(bus);
    bus.addListener(SourShackle::registerPayloads);
    NeoForge.EVENT_BUS.addListener(SourShackle::attack);
    NeoForge.EVENT_BUS.addListener(SourShackle::damaged);
    NeoForge.EVENT_BUS.addListener(SourShackle::playerTick);
    NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
      if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer wearer
          && wearer.hasData(WEARER.get()) && watcher.connection != null)
        PacketDistributor.sendToPlayer(watcher, marks(wearer, wearer.getData(WEARER.get())));
    });
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> resend(event.getEntity()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> resend(event.getEntity()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> resend(event.getEntity()));
  }

  public static boolean worn(Player player) {
    return wearing.test(player);
  }

  public static boolean owned(Player player) {
    return player.getData(OWNED.get());
  }

  public static void markOwned(Player player) {
    if (!player.getData(OWNED.get())) player.setData(OWNED.get(), Boolean.TRUE);
  }

  // ---- The marks ----------------------------------------------------------------------------

  /** The server's side of a wearer: the cycle, the swing waiting for its damage, and what clients were told. */
  public static final class Wearer {
    final SourShackleRules.Cycle cycle = new SourShackleRules.Cycle();
    int swingTarget = -1;
    long swingTick = -1;
    boolean worn;

    public int marks() {
      return cycle.marks();
    }

    public boolean cooling(long now) {
      return cycle.cooling(now);
    }

    public int coolingTicks(long now) {
      return cycle.coolingTicks(now);
    }
  }

  public static Wearer wearer(Player player) {
    return player.getData(WEARER.get());
  }

  static void attack(AttackEntityEvent event) {
    // A multipart boss (the dragon, a hydra) is hit through its parts; the damage lands on the parent.
    Entity hit = event.getTarget() instanceof net.neoforged.neoforge.entity.PartEntity<?> part ? part.getParent() : event.getTarget();
    if (!(event.getEntity() instanceof ServerPlayer player) || !(hit instanceof LivingEntity target)) return;
    if (!worn(player)) return;
    Wearer wearer = wearer(player);
    wearer.worn = true;   // seen worn just now, ahead of the next periodic check
    wearer.swingTarget = target.getId();
    wearer.swingTick = player.level().getGameTime();
  }

  static void damaged(LivingDamageEvent.Post event) {
    DamageSource source = event.getSource();
    if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
        || !source.is(DamageTypes.PLAYER_ATTACK) || !player.hasData(WEARER.get())) return;
    Wearer wearer = player.getData(WEARER.get());
    LivingEntity target = event.getEntity();
    long now = player.level().getGameTime();
    if (wearer.swingTarget != target.getId() || wearer.swingTick != now) return;
    wearer.swingTarget = -1;
    hit(player, wearer, target, now);
  }

  /** A melee hit of a wearer that landed on {@code target}: a mark, or the burst. Returns the outcome. */
  static SourShackleRules.Outcome hit(ServerPlayer player, Wearer wearer, LivingEntity target, long now) {
    var outcome = wearer.cycle.hit(now);
    if (outcome == SourShackleRules.Outcome.BURST) burst(player, target);
    if (outcome != SourShackleRules.Outcome.COOLING) sync(player, wearer, outcome == SourShackleRules.Outcome.BURST);
    return outcome;
  }

  /** The burst around {@code target}; returns how many it caught. */
  static int burst(ServerPlayer wearer, LivingEntity target) {
    ServerLevel level = wearer.serverLevel();
    Vec3 centre = target.position();
    double r = SourShackleRules.BURST_RADIUS;
    var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BURST);
    DamageSource source = new DamageSource(type, wearer);
    List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(r),
        e -> caught(wearer, e) && SourShackleRules.inBurst(e.position().distanceToSqr(centre)));
    for (LivingEntity e : caught) e.hurt(source, SourShackleRules.burstDamage(e.getType().is(Tags.EntityTypes.BOSSES)));
    double y = centre.y + target.getBbHeight() * 0.5;
    level.sendParticles(ParticleTypes.END_ROD, centre.x, y, centre.z, 24, 0.4, 0.4, 0.4, 0.18);
    level.sendParticles(SOUR_DUST, centre.x, y, centre.z, 60, r * 0.45, 0.6, r * 0.45, 0.0);
    level.playSound(null, centre.x, y, centre.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.6F);
    level.playSound(null, centre.x, y, centre.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 0.7F, 1.4F);
    return caught.size();
  }

  /** The sour light's glow colour (TIP[2] of art/authoring/draw_enves.py). */
  static final DustParticleOptions SOUR_DUST = new DustParticleOptions(new Vector3f(0xF3 / 255F, 0xF0 / 255F, 0xA8 / 255F), 1.4F);

  /** Whether the burst of {@code wearer} hurts {@code e}. */
  static boolean caught(ServerPlayer wearer, LivingEntity e) {
    if (e == wearer || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) return false;
    if (wearer.isAlliedTo(e) || e.isAlliedTo(wearer)) return false;
    if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) return false;
    if (e instanceof ServerPlayer other && (!wearer.canHarmPlayer(other) || Enves.teamOf(other).equals(Enves.teamOf(wearer))))
      return false;
    return true;
  }

  static void playerTick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL_TICKS != 0) return;
    boolean worn = worn(player);
    if (!worn && !player.hasData(WEARER.get())) return;
    Wearer wearer = wearer(player);
    if (worn) markOwned(player);
    if (wearer.worn != worn) {
      wearer.worn = worn;
      sync(player, wearer, false);
    }
  }

  static void resend(Player player) {
    if (player instanceof ServerPlayer server && server.hasData(WEARER.get())) sync(server, server.getData(WEARER.get()), false);
  }

  static Marks marks(ServerPlayer player, Wearer wearer) {
    return marks(player, wearer, false);
  }

  static Marks marks(ServerPlayer player, Wearer wearer, boolean burst) {
    return new Marks(player.getId(), wearer.marks(), wearer.coolingTicks(player.level().getGameTime()), wearer.worn, burst);
  }

  static void sync(ServerPlayer player, Wearer wearer, boolean burst) {
    if (player.connection == null) return;
    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, marks(player, wearer, burst));
  }

  // ---- The packet and the client's view -----------------------------------------------------

  /** A player's cuff: marks, ticks of cooldown left, whether it is worn and whether it has just burst. */
  public record Marks(int entity, int marks, int coolingTicks, boolean worn, boolean burst) implements CustomPacketPayload {
    public static final Type<Marks> TYPE = new Type<>(id("sour_shackle_marks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Marks> CODEC = StreamCodec.ofMember(
        (m, buf) -> {
          buf.writeVarInt(m.entity());
          buf.writeByte(m.marks());
          buf.writeVarInt(m.coolingTicks());
          buf.writeBoolean(m.worn());
          buf.writeBoolean(m.burst());
        },
        buf -> new Marks(buf.readVarInt(), Math.min(buf.readUnsignedByte(), SourShackleRules.MARKS), buf.readVarInt(), buf.readBoolean(),
            buf.readBoolean()));

    @Override
    public Type<Marks> type() {
      return TYPE;
    }
  }

  /**
   * The client's copy, in the client level's game time. After a burst the five studs stay lit for
   * {@link #FLASH_TICKS} before the cooldown look, so the fifth mark is seen.
   */
  public record View(int marks, long coolUntil, boolean worn, long flashUntil) {
    public static final View NONE = new View(0, 0, false, 0);
    public static final int FLASH_TICKS = 10;

    public boolean flashing(long now) {
      return now < flashUntil;
    }

    public boolean cooling(long now) {
      return now < coolUntil;
    }
  }

  /** Stores a received {@link Marks} on the entity it names; the client installs it. */
  public static void receive(Level level, Marks marks) {
    Entity entity = level.getEntity(marks.entity());
    long now = level.getGameTime();
    if (entity != null) entity.setData(VIEW.get(), new View(marks.marks(), now + marks.coolingTicks(), marks.worn(),
        marks.burst() ? now + View.FLASH_TICKS : 0));
  }

  /** Assigned by the client; common code never touches client classes. */
  public static Consumer<Marks> clientReceiver = marks -> {};

  static void registerPayloads(RegisterPayloadHandlersEvent event) {
    event.registrar("1").playToClient(Marks.TYPE, Marks.CODEC, (marks, context) -> context.enqueueWork(() -> clientReceiver.accept(marks)));
  }

  // ---- The drop -----------------------------------------------------------------------------

  /**
   * {@code entrelumen:sour_shackle}, in the boss chest's table: the shackle for a player who never had
   * one (and from then on they have), {@link SourShackleRules#REPEAT_SHARDS} sour light shards for one
   * who did. The player is the loot's {@code this_entity}: Lootr rolls a chest once per player with
   * them there, as does a vanilla chest opened by hand. Without a player (a hopper under a vanilla
   * chest) it gives the shackle.
   */
  public static final class Drop extends LootPoolSingletonContainer {
    public static final MapCodec<Drop> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(
        i -> singletonFields(i).apply(i, Drop::new));

    Drop(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
      super(weight, quality, conditions, functions);
    }

    @Override
    public LootPoolEntryType getType() {
      return LOOT_DROP.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> out, LootContext context) {
      out.accept(roll(context.getParamOrNull(LootContextParams.THIS_ENTITY)));
    }

    /** What one player gets, marking them as an owner. */
    public static ItemStack roll(Entity opener) {
      if (opener instanceof Player player) {
        if (owned(player)) return new ItemStack(EnvesContent.SOUR_LIGHT_SHARD.get(), SourShackleRules.REPEAT_SHARDS);
        markOwned(player);
      }
      return new ItemStack(ITEM.get());
    }
  }

  // ---- The item -----------------------------------------------------------------------------

  /** The shackle: one per stack, no durability; its effect in two lines. */
  public static final class Shackle extends Item {
    public Shackle(Properties properties) {
      super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      if (!level.isClientSide && entity instanceof ServerPlayer player && entity.tickCount % 20 == 0) markOwned(player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.entrelumen.sour_shackle.marks", SourShackleRules.MARKS)
          .withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.entrelumen.sour_shackle.burst", number(SourShackleRules.BURST_DAMAGE),
          number(SourShackleRules.BURST_RADIUS), number(SourShackleRules.BOSS_MULTIPLIER),
          SourShackleRules.COOLDOWN_TICKS / 20).withStyle(ChatFormatting.BLUE));
    }

    static String number(double value) {
      return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
  }

  /** For the GameTests: whether the burst's damage type ignores armour and invulnerability frames. */
  static boolean burstTagged(ServerLevel level) {
    var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BURST);
    return type.is(DamageTypeTags.BYPASSES_ARMOR) && type.is(DamageTypeTags.BYPASSES_COOLDOWN);
  }
}
