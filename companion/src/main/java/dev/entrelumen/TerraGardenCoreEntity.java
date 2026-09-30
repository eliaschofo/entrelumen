package dev.entrelumen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The core's state and work: its seed, whether a grow lamp woke it (the lamp is then inside it: one per
 * engine, given back when any block of the engine is broken), and the store of what it made.
 *
 * <p>Once a second of game time ({@link TerraGardenRules#BATCH_TICKS}) a woken core whose garden stands
 * rolls its crop's harvest {@link TerraGardenRules#SAMPLE_ROLLS} times, scales it to
 * {@link TerraGardenRules#HARVESTS_PER_BATCH} harvests and keeps what fits in its store; then it pushes
 * the store into the inventories next to it through NeoForge's item capability. A full store makes it
 * stop: nothing is ever made and thrown away. Every {@link TerraGardenRules#CHECK_TICKS} it checks the
 * garden's blocks again (never loading a chunk); a broken garden pauses until it is whole, and needs no
 * second waking.
 */
public final class TerraGardenCoreEntity extends BlockEntity {
  public enum Status { NO_SEED, EXCLUDED, INCOMPLETE, ASLEEP, FULL, GROWING }

  private ItemStack seed = ItemStack.EMPTY;
  private boolean awake;
  private int rotation = -1;
  private boolean standing;
  private UUID waker;
  private long nextBatch = Long.MIN_VALUE;
  private long nextCheck = Long.MIN_VALUE;
  private long lastTick = Long.MIN_VALUE;
  private long harvests;
  private Status status = Status.NO_SEED;
  /** The store, one entry per kind of item (insertion order). */
  private final Map<ItemKey, Long> store = new LinkedHashMap<>();
  private final CoreItems items = new CoreItems();

  /** An item kind: the item and its components, count ignored. */
  record ItemKey(ItemStack stack) {
    ItemKey {
      stack = stack.copyWithCount(1);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ItemKey that && ItemStack.isSameItemSameComponents(stack, that.stack);
    }

    @Override
    public int hashCode() {
      return ItemStack.hashItemAndComponents(stack);
    }
  }

  public TerraGardenCoreEntity(BlockPos pos, BlockState state) {
    super(TerraGarden.CORE_ENTITY.get(), pos, state);
  }

  // ---- ticking ---------------------------------------------------------------------------------------

  void serverTick(ServerLevel level) {
    long now = level.getGameTime();
    if (now == lastTick) return;   // an accelerator ticking the core again in the same game tick gains nothing
    lastTick = now;
    if (now >= nextCheck) {
      nextCheck = now + TerraGardenRules.CHECK_TICKS;
      check(level);
    }
    if (now < nextBatch) return;
    nextBatch = now + TerraGardenRules.BATCH_TICKS;
    batch(level);
  }

  /** Reads the garden's blocks and updates the core's look. */
  void check(ServerLevel level) {
    var scan = TerraGardenLayout.find(TerraGardenLayout.builtin(), worldPosition, preferredRotation(),
        pos -> level.isLoaded(pos) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) : null,
        (pos, name) -> property(level.getBlockState(pos), name), TerraGardenCoreEntity::normaliseId);
    boolean was = standing;
    standing = scan.complete();
    if (standing) rotation = scan.rotation();
    // an awake engine that lost a block (to anything: a player, an explosion, a piston) gives its lamp back
    if (awake && !standing && !scan.unloaded()) releaseLamp(level, scan.missing().isEmpty() ? worldPosition : scan.missing().getFirst());
    updateLook(level);
    if (was != standing) setChanged();
  }

  /**
   * Marks the engine's members formed (they stop drawing themselves; the core's model draws the engine) or
   * plain again. Only members still in place at the engine's rotation are touched.
   */
  void setFormed(ServerLevel level, boolean formed) {
    if (rotation < 0) return;
    for (var part : TerraGardenLayout.builtin().parts()) {
      if (part.core()) continue;
      BlockPos at = TerraGardenLayout.world(worldPosition, rotation, part.offset());
      if (!level.isLoaded(at)) continue;
      BlockState state = level.getBlockState(at);
      if (state.getBlock() instanceof TerraEngineMemberBlock && state.getValue(TerraEngineMemberBlock.FORMED) != formed)
        level.setBlock(at, state.setValue(TerraEngineMemberBlock.FORMED, formed), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }
  }

  /** The lamp comes out as an item at {@code at}, the engine unforms and sleeps until a lamp wakes it again. */
  void releaseLamp(ServerLevel level, BlockPos at) {
    if (!awake) return;
    awake = false;
    setFormed(level, false);
    net.minecraft.world.level.block.Block.popResource(level, at, new ItemStack(TerraGarden.GROW_LAMP.get()));
    if (!isRemoved() && level.getBlockEntity(worldPosition) == this) updateLook(level);
    setChanged();
  }

  /**
   * A player broke a block: if it belongs to an awake engine (at that engine's rotation), the lamp drops
   * with it, right away. Other losses are caught by the engine's next check.
   */
  static void onBlockBroken(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    BlockPos pos = event.getPos();
    ResourceLocation broken = normaliseId(BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()));
    for (var part : TerraGardenLayout.builtin().parts()) {
      if (part.core() || !normaliseId(part.block()).equals(broken)) continue;
      for (int r = 0; r < 4; r++) {
        BlockPos core = pos.subtract(part.offset().rotate(TerraGardenLayout.rotation(r)));
        if (level.isLoaded(core) && level.getBlockEntity(core) instanceof TerraGardenCoreEntity entity
            && entity.awake && entity.rotation == r) {
          entity.standing = false;
          entity.releaseLamp(level, pos);
          return;
        }
      }
    }
  }

  private int preferredRotation() {
    if (rotation >= 0) return rotation;
    return TerraGardenLayout.rotationFacing(getBlockState().getValue(TerraGardenCoreBlock.FACING));
  }

  /** Copper at any stage or waxed counts as the drawing's copper; a grown glow-berry vine as a vine. */
  public static ResourceLocation normaliseId(ResourceLocation id) {
    ResourceLocation plain = ArkState.normalise(id);
    if (plain.getPath().equals("cave_vines_plant") && plain.getNamespace().equals("minecraft"))
      return ResourceLocation.withDefaultNamespace("cave_vines");
    return plain;
  }

  private static String property(BlockState state, String name) {
    var property = state.getBlock().getStateDefinition().getProperty(name);
    return property == null ? null : value(state, property);
  }

  private static <T extends Comparable<T>> String value(BlockState state,
      net.minecraft.world.level.block.state.properties.Property<T> property) {
    return property.getName(state.getValue(property));
  }

  private void updateLook(ServerLevel level) {
    var look = !standing ? TerraGardenCoreBlock.Garden.UNBUILT
        : awake ? TerraGardenCoreBlock.Garden.GROWING : TerraGardenCoreBlock.Garden.BUILT;
    BlockState state = getBlockState();
    BlockState wanted = state.setValue(TerraGardenCoreBlock.GARDEN, look);
    if (standing && rotation >= 0) wanted = wanted.setValue(TerraGardenCoreBlock.FACING, TerraGardenLayout.front(rotation));
    if (wanted != state) level.setBlock(worldPosition, wanted, 3);
  }

  private void batch(ServerLevel level) {
    export(level);
    status = status(level);
    if (status != Status.GROWING) {
      level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
      return;
    }
    CropBlock crop = TerraGarden.crop(seed);
    Map<ItemKey, Long> sampled = sample(level, crop);
    Map<ItemKey, Long> full = TerraGardenRules.scale(sampled, TerraGardenRules.SAMPLE_ROLLS,
        TerraGardenRules.HARVESTS_PER_BATCH, level.random::nextDouble);
    long free = TerraGardenRules.STORE_CAPACITY - stored();
    Map<ItemKey, Long> kept = TerraGardenRules.fit(full, free);
    long made = TerraGardenRules.harvestsKept(TerraGardenRules.total(full), TerraGardenRules.total(kept),
        TerraGardenRules.HARVESTS_PER_BATCH);
    if (TerraGardenRules.total(kept) < TerraGardenRules.total(full)) status = Status.FULL;
    kept.forEach((key, count) -> store.merge(key, count, Long::sum));
    harvests += made;
    credit(level, made);
    export(level);
    level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    setChanged();
  }

  Status status(ServerLevel level) {
    CropBlock crop = TerraGarden.crop(seed);
    if (crop == null) return Status.NO_SEED;
    if (TerraGarden.excluded(seed)) return Status.EXCLUDED;
    if (!standing) return Status.INCOMPLETE;
    if (!awake) return Status.ASLEEP;
    if (stored() >= TerraGardenRules.STORE_CAPACITY) return Status.FULL;
    return Status.GROWING;
  }

  /** {@link TerraGardenRules#SAMPLE_ROLLS} rolls of the ripe crop's own loot table, summed by item kind. */
  Map<ItemKey, Long> sample(ServerLevel level, CropBlock crop) {
    BlockState ripe = crop.getStateForAge(crop.getMaxAge());
    Map<ItemKey, Long> out = new LinkedHashMap<>();
    for (int i = 0; i < TerraGardenRules.SAMPLE_ROLLS; i++) {
      var params = new LootParams.Builder(level)
          .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
          .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
          .withParameter(LootContextParams.BLOCK_STATE, ripe);
      for (ItemStack drop : ripe.getDrops(params)) {
        if (drop.isEmpty() || drop.is(TerraGarden.FORBIDDEN_DROPS)) continue;
        out.merge(new ItemKey(drop), (long) drop.getCount(), Long::sum);
      }
    }
    return out;
  }

  private void credit(ServerLevel level, long made) {
    if (waker == null || made <= 0) return;
    ServerPlayer player = level.getServer().getPlayerList().getPlayer(waker);
    if (player != null) player.awardStat(TerraGarden.HARVESTS.get(), (int) Math.min(made, Integer.MAX_VALUE));
  }

  /** The outlets' world positions for the garden's rotation (none while it does not stand). */
  List<BlockPos> outlets() {
    List<BlockPos> out = new ArrayList<>();
    if (!standing || rotation < 0) return out;
    for (var part : TerraGardenLayout.builtin().parts())
      if (part.block().equals(TerraGardenLayout.OUTLET))
        out.add(TerraGardenLayout.world(worldPosition, rotation, part.offset()));
    return out;
  }

  /**
   * The core an outlet belongs to: the one whose standing engine, at its own rotation, puts an outlet at
   * {@code pos}. At most eight candidates are looked at (two outlets, four rotations); never loads a chunk.
   */
  public static TerraGardenCoreEntity coreOfOutlet(net.minecraft.world.level.Level level, BlockPos pos) {
    for (var part : TerraGardenLayout.builtin().parts()) {
      if (!part.block().equals(TerraGardenLayout.OUTLET)) continue;
      for (int r = 0; r < 4; r++) {
        BlockPos core = pos.subtract(part.offset().rotate(TerraGardenLayout.rotation(r)));
        if (level.isLoaded(core) && level.getBlockEntity(core) instanceof TerraGardenCoreEntity entity
            && entity.standing && entity.rotation == r)
          return entity;
      }
    }
    return null;
  }

  /**
   * Pushes the store out through the outlets into the inventories that touch them (not the engine's own
   * blocks), stack by stack, until they take no more.
   */
  void export(ServerLevel level) {
    if (store.isEmpty()) return;
    int budget = TerraGardenRules.EXPORT_INSERTS_PER_BATCH;
    boolean changed = false;
    var layout = TerraGardenLayout.builtin();
    for (BlockPos outlet : outlets()) {
      for (Direction direction : Direction.values()) {
        if (store.isEmpty() || budget <= 0) break;
        BlockPos target = outlet.relative(direction);
        if (!level.isLoaded(target) || TerraGardenLayout.contains(layout, worldPosition, rotation, target)) continue;
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, direction.getOpposite());
        if (handler == null) continue;
        var entries = store.entrySet().iterator();
        while (entries.hasNext() && budget > 0) {
          var entry = entries.next();
          long left = entry.getValue();
          int max = entry.getKey().stack().getMaxStackSize();
          while (left > 0 && budget > 0) {
            budget--;
            int size = (int) Math.min(left, max);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, entry.getKey().stack().copyWithCount(size), false);
            int moved = size - rest.getCount();
            left -= moved;
            if (moved > 0) changed = true;
            if (moved < size) break;   // this neighbour takes no more of this item
          }
          if (left <= 0) entries.remove();
          else entry.setValue(left);
        }
      }
    }
    if (changed) setChanged();
  }

  // ---- gestures --------------------------------------------------------------------------------------

  void insertSeed(ServerPlayer player, ItemStack held) {
    if (TerraGarden.excluded(held)) {
      player.displayClientMessage(Component.translatable("entrelumen.terra_garden.excluded", held.getHoverName()), true);
      return;
    }
    ItemStack old = seed;
    seed = held.split(1);
    if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
    status = status((ServerLevel) level);
    player.displayClientMessage(Component.translatable("entrelumen.terra_garden.seed_in", seed.getHoverName()), true);
    seedChanged();
  }

  void takeSeed(ServerPlayer player) {
    if (seed.isEmpty()) {
      report(player);
      return;
    }
    ItemStack old = seed;
    seed = ItemStack.EMPTY;
    if (!player.getInventory().add(old)) player.drop(old, false);
    seedChanged();
  }

  /** The grow lamp's gesture: checks the garden now and wakes it when it stands. */
  void activate(ServerPlayer player, ItemStack lamp) {
    ServerLevel level = (ServerLevel) this.level;
    check(level);
    if (!standing) {
      var scan = TerraGardenLayout.find(TerraGardenLayout.builtin(), worldPosition, preferredRotation(),
          pos -> level.isLoaded(pos) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) : null,
          (pos, name) -> property(level.getBlockState(pos), name), TerraGardenCoreEntity::normaliseId);
      player.displayClientMessage(Component.translatable("entrelumen.terra_garden.incomplete", scan.missing().size(),
          TerraGardenLayout.builtin().size()), false);
      if (!scan.missing().isEmpty()) {
        BlockPos first = scan.missing().getFirst();
        player.displayClientMessage(Component.translatable("entrelumen.terra_garden.missing_at",
            first.getX(), first.getY(), first.getZ()), false);
      }
      return;
    }
    if (awake) {
      player.displayClientMessage(Component.translatable("entrelumen.terra_garden.already_awake"), true);
      return;
    }
    lamp.consume(1, player);   // the lamp goes into the engine; breaking any of its blocks gives it back
    awake = true;
    waker = player.getUUID();
    nextBatch = level.getGameTime() + TerraGardenRules.BATCH_TICKS;
    setFormed(level, true);
    updateLook(level);
    player.awardStat(TerraGarden.ACTIVATIONS.get());
    player.displayClientMessage(Component.translatable("entrelumen.terra_garden.awake"), false);
    level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
        net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.2f);
    setChanged();
  }

  void report(ServerPlayer player) {
    ServerLevel level = (ServerLevel) this.level;
    Status now = status(level);
    Component crop = seed.isEmpty() ? Component.translatable("entrelumen.terra_garden.no_seed_name") : seed.getHoverName();
    player.displayClientMessage(Component.translatable("entrelumen.terra_garden.report." + now.name().toLowerCase(java.util.Locale.ROOT),
        crop, stored(), TerraGardenRules.STORE_CAPACITY, TerraGardenRules.HARVESTS_PER_BATCH), false);
  }

  // ---- store -----------------------------------------------------------------------------------------

  long stored() {
    long sum = 0;
    for (long count : store.values()) sum += count;
    return sum;
  }

  int comparatorLevel() {
    long stored = stored();
    if (stored <= 0) return 0;
    return (int) Math.max(1, Math.min(15, stored * 15 / TerraGardenRules.STORE_CAPACITY));
  }

  IItemHandler itemHandler() {
    return items;
  }

  // ---- test hooks and accessors ----------------------------------------------------------------------

  public ItemStack seed() {
    return seed;
  }

  public boolean awake() {
    return awake;
  }

  public boolean standing() {
    return standing;
  }

  public int rotation() {
    return rotation;
  }

  public long harvests() {
    return harvests;
  }

  public Status lastStatus() {
    return status;
  }

  public Map<ItemStack, Long> storeView() {
    Map<ItemStack, Long> out = new LinkedHashMap<>();
    store.forEach((key, count) -> out.put(key.stack().copy(), count));
    return out;
  }

  /** Test hook: fills the store with {@code count} of {@code stack}'s item. */
  void fillStore(ItemStack stack, long count) {
    store.merge(new ItemKey(stack), count, Long::sum);
    setChanged();
  }

  /** Test hook: the next batch runs on the next tick. */
  void runSoon() {
    nextBatch = Long.MIN_VALUE;
    nextCheck = Long.MIN_VALUE;
    lastTick = Long.MIN_VALUE;
  }

  /** Saves and tells clients: the renderer draws the seed's crop in the pot. */
  private void seedChanged() {
    setChanged();
    if (level != null && !level.isClientSide)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
  }

  /** What clients get: the seed only (the store and the waker stay on the server). */
  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    CompoundTag tag = new CompoundTag();
    if (!seed.isEmpty()) tag.put("seed", seed.save(registries));
    return tag;
  }

  @Override
  public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
    seed = tag.contains("seed") ? ItemStack.parseOptional(registries, tag.getCompound("seed")) : ItemStack.EMPTY;
  }

  @Override
  public void onDataPacket(net.minecraft.network.Connection connection,
      net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
    handleUpdateTag(packet.getTag(), registries);
  }

  // ---- saving ----------------------------------------------------------------------------------------

  private List<TerraGarden.StoredStack> storeList() {
    List<TerraGarden.StoredStack> out = new ArrayList<>();
    store.forEach((key, count) -> out.add(new TerraGarden.StoredStack(key.stack(), count)));
    return out;
  }

  private void loadStore(List<TerraGarden.StoredStack> list) {
    store.clear();
    for (var entry : list)
      if (!entry.item().isEmpty() && entry.count() > 0) store.merge(new ItemKey(entry.item()), entry.count(), Long::sum);
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    var ops = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
    TerraGarden.Contents.CODEC.encodeStart(ops, new TerraGarden.Contents(seed, storeList()))
        .ifSuccess(contents -> tag.put("contents", contents));
    tag.putBoolean("awake", awake);
    tag.putBoolean("standing", standing);
    tag.putInt("rotation", rotation);
    tag.putLong("harvests", harvests);
    if (waker != null) tag.putUUID("waker", waker);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    var ops = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
    if (tag.contains("contents"))
      TerraGarden.Contents.CODEC.parse(ops, tag.get("contents")).ifSuccess(contents -> {
        seed = contents.seed();
        loadStore(contents.store());
      });
    awake = tag.getBoolean("awake");
    standing = tag.getBoolean("standing");
    rotation = tag.contains("rotation") ? tag.getInt("rotation") : -1;
    harvests = tag.getLong("harvests");
    waker = tag.hasUUID("waker") ? tag.getUUID("waker") : null;
  }

  /** Broken, the core keeps its seed and store on the item; the garden has to be woken again. */
  @Override
  protected void collectImplicitComponents(DataComponentMap.Builder components) {
    super.collectImplicitComponents(components);
    if (!seed.isEmpty() || !store.isEmpty())
      components.set(TerraGarden.CONTENTS.get(), new TerraGarden.Contents(seed, storeList()));
  }

  @Override
  protected void applyImplicitComponents(DataComponentInput input) {
    super.applyImplicitComponents(input);
    var contents = input.get(TerraGarden.CONTENTS.get());
    if (contents != null) {
      seed = contents.seed();
      loadStore(contents.store());
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void removeComponentsFromTag(CompoundTag tag) {
    super.removeComponentsFromTag(tag);
    tag.remove("contents");
  }

  // ---- the capability --------------------------------------------------------------------------------

  /**
   * Slot 0 takes a seed (only a growable one, and never gives it back to a pipe); slots 1 to
   * {@link TerraGardenRules#OUTPUT_SLOTS} show the store 64 at a time and only give.
   */
  private final class CoreItems implements IItemHandler {
    @Override
    public int getSlots() {
      return 1 + TerraGardenRules.OUTPUT_SLOTS;
    }

    /** The store as slots: each kind in chunks of its stack size, in order. */
    private Slot slot(int index) {
      int i = 1;
      for (var entry : store.entrySet()) {
        int max = entry.getKey().stack().getMaxStackSize();
        long chunks = (entry.getValue() + max - 1) / max;
        if (index < i + chunks) {
          long before = (long) (index - i) * max;
          int size = (int) Math.min(max, entry.getValue() - before);
          return new Slot(entry.getKey(), size);
        }
        i += (int) Math.min(chunks, Integer.MAX_VALUE - i);
      }
      return null;
    }

    private record Slot(ItemKey key, int size) {}

    @Override
    public ItemStack getStackInSlot(int index) {
      if (index == 0) return seed;
      Slot slot = slot(index);
      return slot == null ? ItemStack.EMPTY : slot.key().stack().copyWithCount(slot.size());
    }

    @Override
    public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
      if (index != 0 || stack.isEmpty() || !seed.isEmpty() || !isItemValid(0, stack)) return stack;
      if (!simulate) {
        seed = stack.copyWithCount(1);
        seedChanged();
      }
      return stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public ItemStack extractItem(int index, int amount, boolean simulate) {
      if (index == 0 || amount <= 0) return ItemStack.EMPTY;
      Slot slot = slot(index);
      if (slot == null) return ItemStack.EMPTY;
      int taken = Math.min(amount, slot.size());
      if (!simulate) {
        long left = store.get(slot.key()) - taken;
        if (left <= 0) store.remove(slot.key());
        else store.put(slot.key(), left);
        setChanged();
      }
      return slot.key().stack().copyWithCount(taken);
    }

    @Override
    public int getSlotLimit(int index) {
      return index == 0 ? 1 : 64;
    }

    @Override
    public boolean isItemValid(int index, ItemStack stack) {
      return index == 0 && TerraGarden.crop(stack) != null && !TerraGarden.excluded(stack);
    }
  }
}
