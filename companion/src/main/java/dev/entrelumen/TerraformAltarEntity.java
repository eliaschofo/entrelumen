package dev.entrelumen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * The Terraform Altar's work: a flat square at the level the altar stands on, blended into the
 * land around it by a smooth slope. Each column is one atomic unit: natural blocks above the
 * target are cut into the altar's buffer as their normal drops, holes are filled from the buffer
 * with surface, subsurface and deep layers matched to the surroundings, and wrong top layers are
 * swapped. Matter is only moved, never created: a column that lacks materials waits, and a column
 * whose drops do not fit waits. Builds, containers, fluids and claims refuse the whole column.
 */
public final class TerraformAltarEntity extends AltarBlockEntity implements MenuProvider {
  public enum State { IDLE, PREVIEW, RUNNING, WAITING, PAUSED, DONE }

  public static final class Totals {
    public int columns, cut, filled, swapped, refused, imported, exported;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putIntArray("values", new int[] {columns, cut, filled, swapped, refused, imported, exported});
      return tag;
    }

    void load(CompoundTag tag) {
      int[] values = Arrays.copyOf(tag.getIntArray("values"), 7);
      columns = values[0];
      cut = values[1];
      filled = values[2];
      swapped = values[3];
      refused = values[4];
      imported = values[5];
      exported = values[6];
    }
  }

  /** Materials the flat surface takes after the untouched land around it. */
  record Palette(BlockState surface, BlockState subsurface, BlockState deep) {
    static final Palette DEFAULT = new Palette(Blocks.GRASS_BLOCK.defaultBlockState(),
        Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState());
  }

  public static final int SLOTS = 27;
  static final int VERSION = 1;
  private static final int FREE_SLOTS = 4;
  private static final long LOOT_SALT = 0x7465727261666F72L;
  private static final List<Item> DIRT_CLASS = List.of(Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT);
  private static final List<Item> SAND_CLASS = List.of(Items.SAND, Items.RED_SAND);
  /** Stone that stays natural ground once placed, so later runs and the Renewal Altar accept it. */
  private static final List<Item> STONE_CLASS = List.of(Items.STONE, Items.DEEPSLATE, Items.ANDESITE,
      Items.DIORITE, Items.GRANITE, Items.TUFF, Items.SANDSTONE, Items.RED_SANDSTONE);
  /** Mined stone counts as built once placed, so it only goes where no one looks again. */
  private static final List<Item> DEEP_ONLY = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE);
  private static final List<Block> COVERS = List.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM);

  private State state = State.IDLE;
  private int size = TerraformRules.DEFAULT_SIZE;
  private int flatTop;
  private int cursor;
  private Palette palette = Palette.DEFAULT;
  private final Totals totals = new Totals();
  private final Map<String, Integer> refusals = new HashMap<>();
  private long previewUntil;
  private long retryAt;
  private List<TerraformRules.Column> columns;
  long workingTicks;
  long tickNanos;
  long maxTickNanos;
  final long[] tickSamples = new long[4096];
  long gcTicks;
  long maxGcMillis;
  /** The buffer alone, for the altar's own transfers. */
  private final IItemHandler handler = new InvWrapper(this) {
    @Override
    public ItemStack getStackInSlot(int slot) {
      return bufferActive() ? super.getStackInSlot(slot).copy() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return bufferActive() ? super.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return bufferActive() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }
  };

  /** What pipes and hoppers see: the fuel slot first, then the 27-slot buffer. */
  private final IItemHandler exposed;

  public TerraformAltarEntity(BlockPos pos, BlockState state) {
    super(Altars.TERRAFORM_ENTITY.get(), pos, state, AltarType.TERRAFORM, SLOTS);
    exposed = new net.neoforged.neoforge.items.wrapper.CombinedInvWrapper(fuelHandler(),
        (net.neoforged.neoforge.items.IItemHandlerModifiable) handler);
  }

  public IItemHandler itemHandler() {
    return exposed;
  }

  private boolean bufferActive() {
    return active() && !contentsDropped;
  }

  @Override
  protected int areaRadius() {
    return TerraformRules.reach(size);
  }

  @Override
  protected boolean working() {
    return state == State.RUNNING;
  }

  /** New materials may unblock a column that waits for them. */
  @Override
  protected void onContentsChanged() {
    retryAt = 0;
  }

  public State state() {
    return state;
  }

  public Totals totals() {
    return totals;
  }

  public int size() {
    return size;
  }

  int cursor() {
    return cursor;
  }

  int flatTop() {
    return flatTop;
  }

  Palette palette() {
    return palette;
  }

  Map<String, Integer> refusals() {
    return Map.copyOf(refusals);
  }

  /** Test hook: pick a size without the preview gesture. */
  void configureSize(int value) {
    if (TerraformRules.validSize(value)) {
      size = value;
      columns = null;
      setChanged();
    }
  }

  // ---- Gestures -------------------------------------------------------------------------------

  /** Standing, empty hand: preview, then confirm; while working, pause or resume. */
  @Override
  public void use(ServerPlayer player) {
    claimOwner(player);
    long now = player.serverLevel().getGameTime();
    switch (state) {
      case IDLE, DONE -> {
        state = State.PREVIEW;
        previewUntil = now + TerraformRules.PREVIEW_TICKS;
        player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.preview",
            2 * size + 1, 2 * size + 1, worldPosition.getY() - 1, TerraformRules.BAND,
            TerraformRules.PREVIEW_TICKS / 20));
      }
      case PREVIEW -> {
        start((ServerLevel) level);
        player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.started",
            2 * size + 1, 2 * size + 1, flatTop));
      }
      case RUNNING, WAITING -> {
        state = State.PAUSED;
        player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.paused"));
      }
      case PAUSED -> {
        state = State.RUNNING;
        retryAt = 0;
        player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.resumed"));
      }
    }
    for (Component line : statusLines()) player.sendSystemMessage(line);
    setChanged();
  }

  /** Crouched, empty hand: change size during the preview, otherwise open the buffer. */
  @Override
  public void crouchUse(ServerPlayer player) {
    if (state == State.PREVIEW) {
      size = TerraformRules.nextSize(size);
      columns = null;
      previewUntil = player.serverLevel().getGameTime() + TerraformRules.PREVIEW_TICKS;
      player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.size", 2 * size + 1, 2 * size + 1));
      setChanged();
      return;
    }
    player.openMenu(this);
  }

  /** A block item in the main hand is added to the buffer as fill material. */
  void supply(ServerPlayer player, ItemStack held) {
    ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, held.copy(), false);
    int moved = held.getCount() - rest.getCount();
    held.shrink(moved);
    player.getInventory().setChanged();
    retryAt = 0;
    player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.supplied", moved, bufferCount()));
  }

  List<Component> statusLines() {
    List<Component> lines = new ArrayList<>();
    int total = columns().size();
    lines.add(Component.translatable("entrelumen.altar.terraform.status",
        Component.translatable("entrelumen.altar.state." + state.name().toLowerCase(Locale.ROOT)),
        2 * size + 1, 2 * size + 1, state == State.IDLE || state == State.PREVIEW ? worldPosition.getY() - 1 : flatTop,
        Math.min(cursor, total), total));
    lines.add(Component.translatable("entrelumen.altar.terraform.progress", totals.cut, totals.filled,
        totals.swapped, totals.refused, bufferCount()));
    lines.add(Component.translatable("entrelumen.altar.terraform.fuel", fuel.getCount(), activeTicks / 20, fuelUsed));
    if (state == State.WAITING)
      lines.add(Component.translatable("entrelumen.altar.terraform.waiting"));
    return lines;
  }

  /** A fresh run at the level the altar stands on, with materials sampled from the land around. */
  void start(ServerLevel world) {
    flatTop = worldPosition.getY() - 1;
    palette = sample(world);
    cursor = 0;
    columns = null;
    totals.load(new CompoundTag());
    fuelUsed = 0;
    refusals.clear();
    state = State.RUNNING;
    retryAt = 0;
    setChanged();
  }

  private List<TerraformRules.Column> columns() {
    if (columns == null) columns = TerraformRules.columns(size);
    return columns;
  }

  int bufferCount() {
    int count = 0;
    for (ItemStack stack : contents) count += stack.getCount();
    return count;
  }

  // ---- Ticking -------------------------------------------------------------------------------

  private enum Result { APPLIED, SKIPPED, REFUSED, MATERIALS, FULL, BUDGET }

  @Override
  public void serverTick(ServerLevel world) {
    try {
      tick(world);
    } finally {
      refreshRegistration();
    }
  }

  private void tick(ServerLevel world) {
    long now = world.getGameTime();
    if ((state == State.PREVIEW || previewUntil > now) && now % 10 == 0) preview(world);
    if (state == State.PREVIEW && now > previewUntil) {
      state = State.IDLE;
      setChanged();
    }
    if (state == State.WAITING && now >= retryAt) state = State.RUNNING;
    if (state != State.RUNNING) return;
    if (!actorWarm(world)) return;
    long started = System.nanoTime();
    long gc = LandWorks.gcMillis();
    try {
      work(world, now);
    } finally {
      long paused = LandWorks.gcMillis() - gc;
      long spent = Math.max(0, System.nanoTime() - started - paused * 1_000_000L);
      if (paused > 0) {
        gcTicks++;
        maxGcMillis = Math.max(maxGcMillis, paused);
      }
      tickSamples[(int) (workingTicks % tickSamples.length)] = spent;
      workingTicks++;
      tickNanos += spent;
      maxTickNanos = Math.max(maxTickNanos, spent);
    }
  }

  private void work(ServerLevel world, long now) {
    int reach = TerraformRules.reach(size) + 2;
    if (!LandWorks.loaded(world, (worldPosition.getX() - reach) >> 4, (worldPosition.getZ() - reach) >> 4,
        (worldPosition.getX() + reach) >> 4, (worldPosition.getZ() + reach) >> 4)) {
      state = State.WAITING;
      retryAt = now + 40;
      return;
    }
    // Only ticks that actually work pay active time.
    if (!payActiveTick()) {
      state = State.WAITING;
      retryAt = now + 20;
      setChanged();
      return;
    }
    Player actor = actor(world);
    AltarRules.TickBudget budget = new AltarRules.TickBudget(TerraformRules.OPS_PER_TICK,
        AltarRules.NANOS_PER_TICK, System::nanoTime);
    List<TerraformRules.Column> all = columns();
    while (budget.open() && cursor < all.size()) {
      exportOverflow(world);
      budget.scan();
      Result result = column(world, actor, all.get(cursor), budget);
      switch (result) {
        case BUDGET -> {
          return;
        }
        case MATERIALS, FULL -> {
          state = State.WAITING;
          retryAt = now + 20;
          setChanged();
          return;
        }
        case REFUSED -> {
          totals.refused++;
          cursor++;
        }
        default -> cursor++;
      }
      setChanged();
    }
    if (cursor >= all.size()) {
      state = State.DONE;
      setChanged();
      if (owner != null) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(owner);
        if (player != null)
          player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.done",
              worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), totals.cut, totals.filled,
              totals.swapped, totals.refused));
      }
    }
  }

  private Result column(ServerLevel world, Player actor, TerraformRules.Column column, AltarRules.TickBudget budget) {
    if (column.dx() == 0 && column.dz() == 0) return Result.SKIPPED;
    int x = worldPosition.getX() + column.dx(), z = worldPosition.getZ() + column.dz();
    int target = TerraformRules.target(column.dx(), column.dz(), size, flatTop,
        (ox, oz) -> outerGround(world, worldPosition.getX() + ox, worldPosition.getZ() + oz));
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    TerraformRules.Plan plan = TerraformRules.column(target, y -> cell(world.getBlockState(probe.set(x, y, z))),
        y -> matches(world.getBlockState(probe.set(x, y, z)), TerraformRules.layer(target - y)));
    if (plan.refused()) return refuse(plan.refusal());
    if (plan.ops().isEmpty()) return Result.SKIPPED;
    if (!budget.allows(plan.ops().size())) return Result.BUDGET;
    for (TerraformRules.Op op : plan.ops())
      if (op.kind() == TerraformRules.Kind.CUT)
        for (Direction direction : Direction.Plane.HORIZONTAL) {
          BlockState side = world.getBlockState(new BlockPos(x, op.y(), z).relative(direction));
          if (side.getFluidState().isSource()) return refuse("fluid");
        }
    importMaterials(world, plan);
    NonNullList<ItemStack> ledger = copy(contents);
    List<BlockPos> removals = new ArrayList<>();
    LinkedHashMap<BlockPos, BlockState> places = new LinkedHashMap<>();
    List<ItemStack> produced = new ArrayList<>();
    int cuts = 0, swaps = 0, fills = 0;
    for (TerraformRules.Op op : plan.ops()) {
      BlockPos pos = new BlockPos(x, op.y(), z);
      switch (op.kind()) {
        case CUT -> {
          removals.add(pos);
          produced.addAll(drops(world, pos, world.getBlockState(pos)));
          cuts++;
        }
        case SWAP -> {
          Item item = withdraw(ledger, preference(op.layer()));
          if (item == null) continue;
          removals.add(pos);
          produced.addAll(drops(world, pos, world.getBlockState(pos)));
          places.put(pos, placed(op.layer(), item));
          swaps++;
        }
        case FILL -> {
          Item item = withdraw(ledger, preference(op.layer()));
          if (item == null) return Result.MATERIALS;
          places.put(pos, placed(op.layer(), item));
          fills++;
        }
      }
    }
    for (ItemStack stack : produced) if (!deposit(ledger, stack)) return Result.FULL;
    for (BlockPos pos : places.keySet()) if (LandWorks.occupied(world, pos)) return refuse("entity");
    for (BlockPos pos : removals)
      if (!LandWorks.mayBreak(actor, world, pos, world.getBlockState(pos))) return refuse("claim");
    List<net.neoforged.neoforge.common.util.BlockSnapshot> removed = new ArrayList<>(removals.size());
    for (BlockPos pos : removals) {
      removed.add(net.neoforged.neoforge.common.util.BlockSnapshot.create(world.dimension(), world, pos, Block.UPDATE_ALL));
      world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
    if (!places.isEmpty() && !LandWorks.commit(actor, world, places, Map.of(), Block.UPDATE_ALL)) {
      for (int i = removed.size() - 1; i >= 0; i--) removed.get(i).restore(Block.UPDATE_ALL);
      return refuse("claim");
    }
    for (int slot = 0; slot < SLOTS; slot++) contents.set(slot, ledger.get(slot));
    totals.cut += cuts;
    totals.swapped += swaps;
    totals.filled += fills;
    totals.columns++;
    budget.spend(plan.ops().size());
    setChanged();
    return Result.APPLIED;
  }

  private Result refuse(String reason) {
    refusals.merge(reason, 1, Integer::sum);
    return Result.REFUSED;
  }

  /** What the column means to the planner; flowing fluid is open, sources refuse the column. */
  static TerraformRules.Cell cell(BlockState state) {
    if (state.isAir()) return TerraformRules.Cell.AIR;
    if (!state.getFluidState().isEmpty()) {
      if (state.getFluidState().isSource()) return TerraformRules.Cell.FLUID;
      return state.getBlock() instanceof LiquidBlock ? TerraformRules.Cell.AIR : TerraformRules.Cell.FLUID;
    }
    if (!LandWorks.natural(state)) return TerraformRules.Cell.BUILT;
    return LandWorks.ground(state) ? TerraformRules.Cell.GROUND : TerraformRules.Cell.LOOSE;
  }

  private boolean matches(BlockState state, TerraformRules.Layer layer) {
    return switch (layer) {
      case SURFACE -> state.is(palette.surface().getBlock());
      case SUBSURFACE -> state.is(palette.subsurface().getBlock()) || classOf(palette.subsurface()).contains(state.getBlock().asItem());
      case DEEP -> true;
    };
  }

  /**
   * Materials each layer accepts, best first. The surface takes its own block (dirt for a grassy
   * cover) or the subsurface's family; the subsurface also takes natural stone; deep layers take
   * any of them plus cobblestone. Everything placed above the deep layers stays natural ground.
   */
  List<Item> preference(TerraformRules.Layer layer) {
    LinkedHashSet<Item> result = new LinkedHashSet<>();
    switch (layer) {
      case SURFACE -> {
        result.add(palette.surface().getBlock().asItem());
        if (COVERS.contains(palette.surface().getBlock())) result.add(Items.DIRT);
        result.add(palette.subsurface().getBlock().asItem());
        result.addAll(classOf(palette.subsurface()));
      }
      case SUBSURFACE -> {
        result.add(palette.subsurface().getBlock().asItem());
        result.addAll(classOf(palette.subsurface()));
        result.addAll(STONE_CLASS);
      }
      case DEEP -> {
        result.add(palette.deep().getBlock().asItem());
        result.addAll(STONE_CLASS);
        result.addAll(DEEP_ONLY);
        result.addAll(classOf(palette.subsurface()));
      }
    }
    result.remove(Items.AIR);
    result.removeIf(item -> !(item instanceof BlockItem blockItem) || !natural(blockItem) && !DEEP_ONLY.contains(item));
    return List.copyOf(result);
  }

  private static boolean natural(BlockItem item) {
    return LandWorks.natural(item.getBlock().defaultBlockState());
  }

  /** Every material any layer accepts. */
  private List<Item> fillMaterials() {
    LinkedHashSet<Item> all = new LinkedHashSet<>();
    for (TerraformRules.Layer layer : TerraformRules.Layer.values()) all.addAll(preference(layer));
    return List.copyOf(all);
  }

  private static List<Item> classOf(BlockState state) {
    if (state.is(BlockTags.DIRT)) return DIRT_CLASS;
    if (state.is(BlockTags.SAND)) return SAND_CLASS;
    return STONE_CLASS;
  }

  /** Dirt on the surface takes the palette's cover; any other material is placed as itself. */
  private BlockState placed(TerraformRules.Layer layer, Item item) {
    if (layer == TerraformRules.Layer.SURFACE && (item == palette.surface().getBlock().asItem()
        || item == Items.DIRT && COVERS.contains(palette.surface().getBlock())))
      return palette.surface();
    return item instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState();
  }

  /** The block's normal drops for a matching tool, seeded by position so a retry is identical. */
  private static List<ItemStack> drops(ServerLevel world, BlockPos pos, BlockState state) {
    if (state.isAir()) return List.of();
    ItemStack tool = new ItemStack(state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? Items.IRON_SHOVEL
        : state.is(BlockTags.MINEABLE_WITH_AXE) ? Items.IRON_AXE
        : state.is(BlockTags.MINEABLE_WITH_HOE) ? Items.IRON_HOE : Items.IRON_PICKAXE);
    LootParams params = new LootParams.Builder(world)
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, tool)
        .withParameter(LootContextParams.BLOCK_STATE, state)
        .create(LootContextParamSets.BLOCK);
    LootTable table = world.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
    long seed = NatureRestorationRules.mix(world.getSeed(), pos.getX() ^ pos.getY() * 31, pos.getZ(), LOOT_SALT);
    return List.copyOf(table.getRandomItems(params, seed));
  }

  private static NonNullList<ItemStack> copy(NonNullList<ItemStack> source) {
    NonNullList<ItemStack> result = NonNullList.withSize(source.size(), ItemStack.EMPTY);
    for (int i = 0; i < source.size(); i++) result.set(i, source.get(i).copy());
    return result;
  }

  /** Takes one of the first preferred item present, from the last stack that holds it. */
  private static Item withdraw(NonNullList<ItemStack> ledger, List<Item> preference) {
    for (Item item : preference)
      for (int slot = ledger.size() - 1; slot >= 0; slot--) {
        ItemStack stack = ledger.get(slot);
        if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
          stack.shrink(1);
          if (stack.isEmpty()) ledger.set(slot, ItemStack.EMPTY);
          return item;
        }
      }
    return null;
  }

  /** Stacks into existing stacks, then empty slots; false if any of it does not fit. */
  private static boolean deposit(NonNullList<ItemStack> ledger, ItemStack stack) {
    ItemStack rest = stack.copy();
    for (int slot = 0; slot < ledger.size() && !rest.isEmpty(); slot++) {
      ItemStack held = ledger.get(slot);
      if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, rest)) {
        int moved = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
        held.grow(moved);
        rest.shrink(moved);
      }
    }
    for (int slot = 0; slot < ledger.size() && !rest.isEmpty(); slot++)
      if (ledger.get(slot).isEmpty()) {
        int moved = Math.min(rest.getCount(), rest.getMaxStackSize());
        ledger.set(slot, rest.copyWithCount(moved));
        rest.shrink(moved);
      }
    return rest.isEmpty();
  }

  private List<IItemHandler> neighbours(ServerLevel world) {
    List<IItemHandler> result = new ArrayList<>();
    for (Direction direction : Direction.values()) {
      BlockPos pos = worldPosition.relative(direction);
      if (!world.hasChunkAt(pos) || world.getBlockEntity(pos) instanceof TerraformAltarEntity) continue;
      IItemHandler found = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction.getOpposite());
      if (found != null) result.add(found);
    }
    return result;
  }

  /** Moves stacks that are not fill material to adjacent containers when the buffer runs low. */
  private void exportOverflow(ServerLevel world) {
    int free = 0;
    for (ItemStack stack : contents) if (stack.isEmpty()) free++;
    if (free >= FREE_SLOTS) return;
    List<Item> keep = new ArrayList<>(preference(TerraformRules.Layer.SURFACE));
    keep.addAll(preference(TerraformRules.Layer.SUBSURFACE));
    List<IItemHandler> targets = neighbours(world);
    if (targets.isEmpty()) return;
    for (int slot = SLOTS - 1; slot >= 0 && free < FREE_SLOTS; slot--) {
      ItemStack stack = contents.get(slot);
      if (stack.isEmpty() || keep.contains(stack.getItem())) continue;
      ItemStack rest = stack.copy();
      for (IItemHandler target : targets) rest = ItemHandlerHelper.insertItemStacked(target, rest, false);
      totals.exported += stack.getCount() - rest.getCount();
      contents.set(slot, rest);
      if (rest.isEmpty()) free++;
      setChanged();
    }
  }

  /** Pulls the fill material a column lacks from adjacent containers into the buffer. */
  private void importMaterials(ServerLevel world, TerraformRules.Plan plan) {
    Map<TerraformRules.Layer, Integer> needed = new EnumMap<>(TerraformRules.Layer.class);
    for (TerraformRules.Op op : plan.ops())
      if (op.kind() == TerraformRules.Kind.FILL) needed.merge(op.layer(), 1, Integer::sum);
    if (needed.isEmpty()) return;
    int want = 0;
    for (int count : needed.values()) want += count;
    int have = 0;
    List<Item> accepted = fillMaterials();
    for (ItemStack stack : contents) if (accepted.contains(stack.getItem())) have += stack.getCount();
    if (have >= want) return;
    int missing = want - have;
    for (IItemHandler source : neighbours(world))
      for (int slot = 0; slot < source.getSlots() && missing > 0; slot++) {
        ItemStack offered = source.getStackInSlot(slot);
        if (offered.isEmpty() || !accepted.contains(offered.getItem())) continue;
        ItemStack simulated = source.extractItem(slot, missing, true);
        if (simulated.isEmpty()) continue;
        ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, simulated, true);
        int movable = simulated.getCount() - rest.getCount();
        if (movable <= 0) return;
        ItemStack taken = source.extractItem(slot, movable, false);
        ItemStack left = ItemHandlerHelper.insertItemStacked(handler, taken, false);
        if (!left.isEmpty()) source.insertItem(slot, left, false);
        int moved = taken.getCount() - left.getCount();
        totals.imported += moved;
        missing -= moved;
      }
  }

  /** Ground top next to the square, water surface included; unknown when built or unloaded. */
  static int outerGround(ServerLevel world, int x, int z) {
    if (!world.hasChunk(x >> 4, z >> 4)) return TerraformRules.UNKNOWN;
    int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (int steps = 0; steps < 64 && y >= world.getMinBuildHeight(); steps++, y--) {
      BlockState state = world.getBlockState(probe.set(x, y, z));
      if (state.getFluidState().isSource()) return y;
      TerraformRules.Cell cell = cell(state);
      if (cell == TerraformRules.Cell.GROUND) return y;
      if (cell == TerraformRules.Cell.BUILT) return TerraformRules.UNKNOWN;
    }
    return TerraformRules.UNKNOWN;
  }

  /** The most common surface, subsurface and deep blocks around the square. */
  private Palette sample(ServerLevel world) {
    Map<Block, Integer> surface = new HashMap<>(), subsurface = new HashMap<>(), deep = new HashMap<>();
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (TerraformRules.Column column : TerraformRules.outerRing(size)) {
      int x = worldPosition.getX() + column.dx(), z = worldPosition.getZ() + column.dz();
      int top = outerGround(world, x, z);
      if (top == TerraformRules.UNKNOWN) continue;
      BlockState at = world.getBlockState(probe.set(x, top, z));
      if (!LandWorks.ground(at)) continue;
      surface.merge(at.getBlock(), 1, Integer::sum);
      for (int depth = 1; depth <= TerraformRules.SUBSURFACE_DEPTH; depth++) {
        BlockState below = world.getBlockState(probe.set(x, top - depth, z));
        if (LandWorks.ground(below) && !below.is(Tags.Blocks.ORES)) subsurface.merge(below.getBlock(), 1, Integer::sum);
      }
      BlockState low = world.getBlockState(probe.set(x, top - TerraformRules.SUBSURFACE_DEPTH - 2, z));
      if (LandWorks.ground(low) && !low.is(Tags.Blocks.ORES)) deep.merge(low.getBlock(), 1, Integer::sum);
    }
    return new Palette(dominant(surface, Palette.DEFAULT.surface()), dominant(subsurface, Palette.DEFAULT.subsurface()),
        dominant(deep, Palette.DEFAULT.deep()));
  }

  private static BlockState dominant(Map<Block, Integer> counts, BlockState fallback) {
    return counts.entrySet().stream()
        .filter(entry -> entry.getKey().asItem() != Items.AIR)
        .max(Comparator.<Map.Entry<Block, Integer>>comparingInt(Map.Entry::getValue)
            .thenComparing(entry -> BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString(), Comparator.reverseOrder()))
        .map(entry -> entry.getKey().defaultBlockState()).orElse(fallback);
  }

  /** The flat square's edge in white and the slope's outer edge in green, at their target heights. */
  private void preview(ServerLevel world) {
    int top = state == State.PREVIEW || state == State.IDLE ? worldPosition.getY() - 1 : flatTop;
    for (int ring : new int[] {size, TerraformRules.reach(size)})
      for (int i = -ring; i <= ring; i++)
        for (int[] offset : new int[][] {{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
          int x = worldPosition.getX() + offset[0], z = worldPosition.getZ() + offset[1];
          if (!world.hasChunk(x >> 4, z >> 4)) continue;
          int y = ring == size ? top + 1 : world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
          world.sendParticles(ring == size ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER,
              x + 0.5, y + 0.2, z + 0.5, 1, 0, 0, 0, 0);
        }
  }

  // ---- Menu -----------------------------------------------------------------------------------

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.entrelumen.terraform_altar");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return ChestMenu.threeRows(id, inventory, this);
  }

  // ---- Persistence ---------------------------------------------------------------------------

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    int version = tag.getInt("version");
    if (version > VERSION) throw new IllegalStateException("Unsupported terraform altar version " + version);
    state = State.IDLE;
    for (State value : State.values()) if (value.name().equals(tag.getString("state"))) state = value;
    if (state == State.PREVIEW) state = State.IDLE;
    size = TerraformRules.validSize(tag.getInt("size")) ? tag.getInt("size") : TerraformRules.DEFAULT_SIZE;
    flatTop = tag.contains("flatTop", Tag.TAG_INT) ? tag.getInt("flatTop") : worldPosition.getY() - 1;
    columns = null;
    cursor = Math.max(0, Math.min(columns().size(), tag.getInt("cursor")));
    var blocks = registries.lookupOrThrow(Registries.BLOCK);
    palette = tag.contains("palette", Tag.TAG_COMPOUND) ? new Palette(
        NbtUtils.readBlockState(blocks, tag.getCompound("palette").getCompound("surface")),
        NbtUtils.readBlockState(blocks, tag.getCompound("palette").getCompound("subsurface")),
        NbtUtils.readBlockState(blocks, tag.getCompound("palette").getCompound("deep"))) : Palette.DEFAULT;
    totals.load(tag.getCompound("totals"));
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("version", VERSION);
    tag.putString("state", (state == State.PREVIEW ? State.IDLE : state).name());
    tag.putInt("size", size);
    tag.putInt("flatTop", flatTop);
    tag.putInt("cursor", cursor);
    CompoundTag materials = new CompoundTag();
    materials.put("surface", NbtUtils.writeBlockState(palette.surface()));
    materials.put("subsurface", NbtUtils.writeBlockState(palette.subsurface()));
    materials.put("deep", NbtUtils.writeBlockState(palette.deep()));
    tag.put("palette", materials);
    tag.put("totals", totals.save());
  }
}
