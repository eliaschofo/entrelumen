package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Protection shared by the Ark's Nature service and the altars: what counts as wild land, who acts
 * for an unattended block, and how a unit of work asks the native events before it stays.
 */
final class LandWorks {
  /** Wild ground the Nature service leaves out but the altars treat as terrain. */
  private static final Set<Block> EXTRA_TERRAIN = Set.of(Blocks.SANDSTONE, Blocks.RED_SANDSTONE,
      Blocks.TERRACOTTA, Blocks.SMOOTH_BASALT, Blocks.TUFF);
  private static final Set<Block> GROUND_BLOCKS = Set.of(Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW_BLOCK,
      Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK,
      Blocks.POWDER_SNOW);
  private static final UUID UNATTENDED = UUID.nameUUIDFromBytes("entrelumen:altar".getBytes());

  private LandWorks() {}

  /** Total garbage-collection time so far, to tell GC pauses apart from altar work when measuring. */
  static long gcMillis() {
    long total = 0;
    for (var bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())
      total += Math.max(0, bean.getCollectionTime());
    return total;
  }

  /** Wild terrain and vegetation; everything else, and every block entity, counts as built. */
  static boolean natural(BlockState state) {
    return NatureRestoration.natural(state)
        || !state.hasBlockEntity() && EXTRA_TERRAIN.contains(state.getBlock());
  }

  /** Natural ground: what a hole is made of and what a column rests on. */
  static boolean ground(BlockState state) {
    if (!natural(state) || state.isAir() || !state.getFluidState().isEmpty()) return false;
    return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
        || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Tags.Blocks.ORES)
        || GROUND_BLOCKS.contains(state.getBlock()) || EXTRA_TERRAIN.contains(state.getBlock());
  }

  /** Decorations, vehicles and other non-living entities belong to someone's build. */
  static boolean built(Entity entity) {
    return NatureRestoration.built(entity);
  }

  /** Any entity at all inside a block: nothing is placed into a player, a mob or a decoration. */
  static boolean occupied(ServerLevel level, BlockPos pos) {
    return !level.getEntities((Entity) null, new AABB(pos)).isEmpty();
  }

  /** Every chunk in the inclusive bounds is loaded with its entities; nothing is loaded here. */
  static boolean loaded(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++)
        if (!level.hasChunk(x, z) || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
    return true;
  }

  static boolean loaded(ServerLevel level, BlockPos pos) {
    return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
  }

  /**
   * The player an unattended altar acts as: its owner when online in that level, else a fake
   * player carrying the owner's profile, so claim systems judge the owner's permissions.
   */
  static Player actor(ServerLevel level, UUID owner, String name) {
    if (owner != null) {
      ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
      if (online != null && online.serverLevel() == level && !online.isRemoved()) return online;
      return FakePlayerFactory.get(level, new GameProfile(owner, name == null || name.isBlank()
          ? "[Entrelumen]" : name));
    }
    return FakePlayerFactory.get(level, new GameProfile(UNATTENDED, "[Entrelumen Altar]"));
  }

  /** Whether the native break event lets the actor remove this block; claims may cancel. */
  static boolean mayBreak(Player actor, ServerLevel level, BlockPos pos, BlockState state) {
    return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, actor)).isCanceled();
  }

  /**
   * Applies a unit, then asks the native placement event as the acting player for every changed
   * block, as block items do. Claim systems can cancel; any refusal restores the whole unit.
   */
  static boolean commit(Player actor, ServerLevel level, Map<BlockPos, BlockState> writes,
      Map<BlockPos, CompoundTag> blockEntities, int flags) {
    List<BlockSnapshot> snapshots = new ArrayList<>(writes.size());
    for (BlockPos pos : writes.keySet())
      snapshots.add(BlockSnapshot.create(level.dimension(), level, pos, flags));
    for (var entry : writes.entrySet()) {
      level.setBlock(entry.getKey(), entry.getValue(), flags);
      CompoundTag data = blockEntities.get(entry.getKey());
      BlockEntity entity = data == null ? null : level.getBlockEntity(entry.getKey());
      if (entity != null) {
        entity.loadCustomOnly(data, level.registryAccess());
        entity.setChanged();
      }
    }
    for (BlockSnapshot snapshot : snapshots) {
      if (EventHooks.onBlockPlace(actor, snapshot, Direction.UP)) {
        for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore(flags);
        return false;
      }
    }
    return true;
  }

  /** Every face neighbour of every write is natural, part of the unit, or accepted by the caller. */
  static boolean surroundedByLand(ServerLevel level, Iterable<BlockPos> writes, Set<BlockPos> unit,
      java.util.function.Predicate<BlockPos> accepted) {
    for (BlockPos pos : writes)
      for (Direction direction : Direction.values()) {
        BlockPos neighbour = pos.relative(direction);
        if (unit.contains(neighbour)) continue;
        if (!loaded(level, neighbour)) return false;
        if (!natural(level.getBlockState(neighbour)) && !accepted.test(neighbour)) return false;
      }
    return true;
  }
}
