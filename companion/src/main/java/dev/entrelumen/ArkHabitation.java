package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/** A personal, reversible expedition spawn at an installed Habitation Module. */
public final class ArkHabitation {
  private static final String KEY = "entrelumen:habitation";
  private static final int VERSION = 1;
  private static final String RESERVED = "reserved";
  private static final String HOME = "home";
  private static final String PENDING = "pending_restore";
  private static final String PENDING_CLONE = "pending_clone";
  private static final int[][] LATERAL = {
      {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
      {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1}, {1, 0, 1}
  };

  private record Spawn(ResourceKey<Level> dimension, BlockPos position, float angle,
      boolean forced) {}

  private record Stay(Spawn reserved, Spawn home, boolean pendingRestore,
      boolean pendingClone) {
    Stay awaitingHome() {
      return new Stay(reserved, home, true, false);
    }

    Stay awaitingClone() {
      return new Stay(reserved, home, false, true);
    }

    Stay cloneVerified() {
      return new Stay(reserved, home, false, false);
    }
  }

  private enum ReadState { ABSENT, VALID, MALFORMED }

  private record Stored(ReadState state, Stay stay) {}

  private ArkHabitation() {}

  /** Main-hand bed use checks in; crouched main-hand bed use checks out. */
  public static boolean use(ServerPlayer player, BlockPos module, InteractionHand hand,
      boolean checkout) {
    if (hand != InteractionHand.MAIN_HAND || checkout != player.isSecondaryUseActive()
        || !player.getItemInHand(hand).is(ItemTags.BEDS) || !player.isAlive()
        || player.isSpectator()
        || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !isHabitation(player.serverLevel().getBlockState(module))) return false;

    Stored stored = read(player);
    if (stored.state() == ReadState.MALFORMED) {
      say(player, "corrupt");
      return false;
    }
    Stay stay = stored.stay();
    if (stay != null && !matches(player, stay.reserved())) {
      if (stay.pendingClone() && player.getRespawnPosition() == null) {
        if (checkout) {
          if (!restore(player, stay.home())) {
            say(player, "restore_blocked");
            return false;
          }
          clear(player);
          say(player, "restored");
          return true;
        }
        if (!restore(player, stay.reserved())) {
          say(player, "register_blocked");
          return false;
        }
        stay = stay.cloneVerified();
        save(player, stay);
      }
      if (stay.pendingRestore() && player.getRespawnPosition() == null) {
        if (!restore(player, stay.home())) {
          say(player, "restore_blocked");
          return false;
        }
        if (checkout) {
          clear(player);
          say(player, "restored");
          return true;
        }
      }
      if (!matches(player, stay.reserved())) {
        clear(player);
        stay = null;
      }
    }

    if (checkout) {
      if (stay == null) {
        say(player, "none");
        return false;
      }
      if (!restore(player, stay.home())) {
        say(player, "restore_blocked");
        return false;
      }
      clear(player);
      say(player, "restored");
      return true;
    }

    if (stay != null) {
      boolean sameModule = stay.reserved().dimension().equals(player.serverLevel().dimension())
          && stay.reserved().position().equals(module);
      say(player, sameModule ? "already" : "other");
      return sameModule;
    }
    if (!BedBlock.canSetSpawn(player.serverLevel())) {
      say(player, "dimension");
      return false;
    }
    if (LogisticsModuleActions.controllerForDeposit(
        EngineeringDiagnostics.physicalView(player.serverLevel(), module)) == null) {
      say(player, "structure");
      return false;
    }
    if (respawn(player.serverLevel().getBlockState(module), EntityType.PLAYER,
        player.serverLevel(), module, player.getYRot()).isEmpty()) {
      say(player, "unsafe");
      return false;
    }

    Spawn home = current(player);
    Spawn reserved = new Spawn(player.serverLevel().dimension(), module.immutable(),
        player.getYRot(), false);
    if (home.dimension().equals(reserved.dimension())
        && home.position() != null && home.position().equals(reserved.position())) {
      say(player, "already_current");
      return false;
    }
    player.setRespawnPosition(reserved.dimension(), reserved.position(), reserved.angle(),
        reserved.forced(), false);
    if (!matches(player, reserved)) {
      say(player, "register_blocked");
      return false;
    }
    save(player, new Stay(reserved, home, false, false));
    say(player, "registered");
    return true;
  }

  /** The native non-forced respawn path calls this only for the installed module. */
  public static Optional<ServerPlayer.RespawnPosAngle> respawn(BlockState state,
      EntityType<?> type, LevelReader reader, BlockPos module, float angle) {
    if (type != EntityType.PLAYER || !isHabitation(state)
        || !(reader instanceof ServerLevel level) || !BedBlock.canSetSpawn(level)
        || !level.hasChunkAt(module) || !isHabitation(level.getBlockState(module)))
      return Optional.empty();

    Optional<ServerPlayer.RespawnPosAngle> above = safePosition(level, module.above(), angle);
    if (above.isPresent()) return above;
    for (int[] offset : LATERAL) {
      Optional<ServerPlayer.RespawnPosAngle> beside = safePosition(level,
          module.offset(offset[0], offset[1], offset[2]), angle);
      if (beside.isPresent()) return beside;
    }
    return Optional.empty();
  }

  /** Book rendering never creates or changes a spawn or player record. */
  public static List<Component> journalLines(ServerPlayer player) {
    Stored stored = read(player);
    if (stored.state() == ReadState.MALFORMED)
      return List.of(Component.translatable("entrelumen.habitation.corrupt"),
          Component.translatable("entrelumen.habitation.instructions"));
    Stay stay = stored.stay();
    if (stay == null)
      return List.of(Component.translatable("entrelumen.habitation.none"),
          Component.translatable("entrelumen.habitation.instructions"));
    List<Component> lines = new ArrayList<>();
    if (matches(player, stay.reserved())) {
      lines.add(Component.translatable("entrelumen.habitation.active",
          stay.reserved().dimension().location().toString(),
          stay.reserved().position().getX(), stay.reserved().position().getY(),
          stay.reserved().position().getZ()));
    } else if ((stay.pendingRestore() || stay.pendingClone())
        && player.getRespawnPosition() == null) {
      lines.add(Component.translatable("entrelumen.habitation.pending"));
    } else {
      lines.add(Component.translatable("entrelumen.habitation.stale"));
    }
    if (stay.home().position() == null)
      lines.add(Component.translatable("entrelumen.habitation.home_none"));
    else
      lines.add(Component.translatable("entrelumen.habitation.home",
          stay.home().dimension().location().toString(),
          stay.home().position().getX(), stay.home().position().getY(),
          stay.home().position().getZ()));
    lines.add(Component.translatable("entrelumen.habitation.instructions"));
    return List.copyOf(lines);
  }

  public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
  }

  /** After the native clone, verify its second, independently cancelable spawn setter. */
  public static void onPostRespawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
  }

  /** Runs after vanilla has selected a respawn but before it clones the player. */
  public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    Stored stored = read(player);
    if (stored.state() != ReadState.VALID) return;
    Stay stay = stored.stay();
    boolean reserved = matches(player, stay.reserved());
    boolean empty = player.getRespawnPosition() == null;
    if (!reserved && !(empty && (stay.pendingRestore() || stay.pendingClone()))) {
      clear(player);
      return;
    }
    if (event.getDimensionTransition() != event.getOriginalDimensionTransition()) return;
    // A failed clone copy may leave no spawn; retry reservation after this clone.
    if (!reserved && stay.pendingClone()) return;
    if (reserved && !event.getOriginalDimensionTransition().missingRespawnBlock()
        && modulePresent(player, stay.reserved())) {
      // A deliberate no-copy decision by another mod is not a canceled setter.
      save(player, event.copyOriginalSpawnPosition() ? stay.awaitingClone() : stay.cloneVerified());
      return;
    }

    if (!restore(player, stay.home())) {
      save(player, stay.awaitingHome());
      return;
    }
    var fallback = player.findRespawnPositionAndUseSpawnBlock(event.isFromEndFight(),
        event.getOriginalDimensionTransition().postDimensionTransition());
    event.setDimensionTransition(fallback);
    event.setCopyOriginalSpawnPosition(!fallback.missingRespawnBlock());
    if (fallback.missingRespawnBlock()) clear(player);
    else save(player, stay.awaitingHome());
  }

  private static Optional<ServerPlayer.RespawnPosAngle> safePosition(ServerLevel level,
      BlockPos feet, float angle) {
    BlockPos head = feet.above();
    BlockPos floor = feet.below();
    if (level.isOutsideBuildHeight(floor) || level.isOutsideBuildHeight(head)
        || !level.hasChunkAt(floor) || !level.hasChunkAt(feet)
        || !level.hasChunkAt(head))
      return Optional.empty();
    if (EntityType.PLAYER.isBlockDangerous(level.getBlockState(floor)))
      return Optional.empty();
    Vec3 position = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, level,
        feet, true);
    if (position == null) return Optional.empty();
    var occupied = EntityType.PLAYER.getDimensions().makeBoundingBox(position);
    for (BlockPos part : BlockPos.betweenClosed(
        Mth.floor(occupied.minX), Mth.floor(occupied.minY), Mth.floor(occupied.minZ),
        Mth.floor(Math.nextDown(occupied.maxX)),
        Mth.floor(Math.nextDown(occupied.maxY)),
        Mth.floor(Math.nextDown(occupied.maxZ)))) {
      if (level.isOutsideBuildHeight(part) || !level.hasChunkAt(part)
          || !level.getFluidState(part).isEmpty()
          || EntityType.PLAYER.isBlockDangerous(level.getBlockState(part)))
        return Optional.empty();
    }
    return Optional.of(new ServerPlayer.RespawnPosAngle(position, angle));
  }

  private static boolean isHabitation(BlockState state) {
    return state.getBlock() instanceof ArkFieldJournalBlock block
        && block.kind() == ArkFieldJournals.Kind.HABITATION;
  }

  private static boolean modulePresent(ServerPlayer player, Spawn reserved) {
    ServerLevel level = player.server.getLevel(reserved.dimension());
    return level != null && BedBlock.canSetSpawn(level)
        && level.hasChunkAt(reserved.position())
        && isHabitation(level.getBlockState(reserved.position()));
  }

  private static void reconcile(ServerPlayer player) {
    Stored stored = read(player);
    if (stored.state() != ReadState.VALID) return;
    Stay stay = stored.stay();
    if (matches(player, stay.reserved())) {
      if (stay.pendingClone()) save(player, stay.cloneVerified());
      return;
    }
    if (player.getRespawnPosition() == null && stay.pendingClone()) {
      if (restore(player, stay.reserved())) save(player, stay.cloneVerified());
      return;
    }
    if (stay.pendingRestore() && player.getRespawnPosition() == null
        && !restore(player, stay.home())) return;
    clear(player);
  }

  private static Spawn current(ServerPlayer player) {
    return new Spawn(player.getRespawnDimension(), player.getRespawnPosition(),
        player.getRespawnAngle(), player.isRespawnForced());
  }

  private static boolean matches(ServerPlayer player, Spawn expected) {
    Spawn actual = current(player);
    return actual.dimension().equals(expected.dimension())
        && Objects.equals(actual.position(), expected.position())
        && Float.compare(actual.angle(), expected.angle()) == 0
        && actual.forced() == expected.forced();
  }

  private static boolean restore(ServerPlayer player, Spawn home) {
    player.setRespawnPosition(home.dimension(), home.position(), home.angle(),
        home.forced(), false);
    return matches(player, home);
  }

  private static void say(ServerPlayer player, String suffix) {
    player.sendSystemMessage(Component.translatable("entrelumen.habitation." + suffix));
  }

  private static Stored read(ServerPlayer player) {
    CompoundTag data = player.getPersistentData();
    if (!data.contains(Player.PERSISTED_NBT_TAG))
      return new Stored(ReadState.ABSENT, null);
    if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
      return new Stored(ReadState.MALFORMED, null);
    CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
    if (!persisted.contains(KEY)) return new Stored(ReadState.ABSENT, null);
    if (!persisted.contains(KEY, Tag.TAG_COMPOUND))
      return new Stored(ReadState.MALFORMED, null);
    CompoundTag tag = persisted.getCompound(KEY);
    if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION
        || !tag.contains(RESERVED, Tag.TAG_COMPOUND)
        || !tag.contains(HOME, Tag.TAG_COMPOUND)
        || !booleanField(tag, PENDING)
        || !booleanField(tag, PENDING_CLONE)
        || (tag.getBoolean(PENDING) && tag.getBoolean(PENDING_CLONE)))
      return new Stored(ReadState.MALFORMED, null);
    Spawn reserved = decode(tag.getCompound(RESERVED));
    Spawn home = decode(tag.getCompound(HOME));
    if (reserved == null || reserved.position() == null || home == null)
      return new Stored(ReadState.MALFORMED, null);
    return new Stored(ReadState.VALID,
        new Stay(reserved, home, tag.getBoolean(PENDING), tag.getBoolean(PENDING_CLONE)));
  }

  private static Spawn decode(CompoundTag tag) {
    if (!tag.contains("dimension", Tag.TAG_STRING)
        || !tag.contains("has_position", Tag.TAG_BYTE)
        || !booleanField(tag, "has_position")
        || !tag.contains("angle", Tag.TAG_FLOAT)
        || !booleanField(tag, "forced")) return null;
    ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
    float angle = tag.getFloat("angle");
    if (id == null || !Float.isFinite(angle)) return null;
    boolean hasPosition = tag.getBoolean("has_position");
    BlockPos position = null;
    if (hasPosition) {
      if (!tag.contains("position", Tag.TAG_INT_ARRAY)) return null;
      int[] coordinates = tag.getIntArray("position");
      if (coordinates.length != 3) return null;
      position = new BlockPos(coordinates[0], coordinates[1], coordinates[2]);
    } else if (tag.contains("position")) {
      return null;
    }
    ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
    if (position == null && (!dimension.equals(Level.OVERWORLD)
        || Float.compare(angle, 0.0F) != 0 || tag.getBoolean("forced"))) return null;
    return new Spawn(dimension, position, angle, tag.getBoolean("forced"));
  }

  private static boolean booleanField(CompoundTag tag, String key) {
    return tag.contains(key, Tag.TAG_BYTE)
        && (tag.getByte(key) == 0 || tag.getByte(key) == 1);
  }

  private static CompoundTag encode(Spawn spawn) {
    CompoundTag tag = new CompoundTag();
    tag.putString("dimension", spawn.dimension().location().toString());
    tag.putBoolean("has_position", spawn.position() != null);
    if (spawn.position() != null)
      tag.putIntArray("position", new int[] {spawn.position().getX(),
          spawn.position().getY(), spawn.position().getZ()});
    tag.putFloat("angle", spawn.angle());
    tag.putBoolean("forced", spawn.forced());
    return tag;
  }

  private static void save(ServerPlayer player, Stay stay) {
    CompoundTag data = player.getPersistentData();
    CompoundTag persisted = data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
        ? data.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();
    CompoundTag tag = new CompoundTag();
    tag.putInt("version", VERSION);
    tag.put(RESERVED, encode(stay.reserved()));
    tag.put(HOME, encode(stay.home()));
    tag.putBoolean(PENDING, stay.pendingRestore());
    tag.putBoolean(PENDING_CLONE, stay.pendingClone());
    persisted.put(KEY, tag);
    data.put(Player.PERSISTED_NBT_TAG, persisted);
  }

  private static void clear(ServerPlayer player) {
    CompoundTag data = player.getPersistentData();
    if (data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
      data.getCompound(Player.PERSISTED_NBT_TAG).remove(KEY);
  }
}
