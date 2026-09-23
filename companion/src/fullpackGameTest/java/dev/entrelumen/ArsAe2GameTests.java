package dev.entrelumen;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** QA-JAR-only power test through real Ars Nouveau, Ars Énergistique and AE2 blocks. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ArsAe2GameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final AEItemKey COBBLESTONE = AEItemKey.of(Items.COBBLESTONE);

  private ArsAe2GameTests() {}

  @GameTest(template = "empty", timeoutTicks = 900)
  public static void finiteSourceAcceptorPowersMeStorageThenCutsOff(GameTestHelper helper) {
    for (String mod : List.of("ars_nouveau", "arseng", "ae2"))
      helper.assertTrue(ModList.get().isLoaded(mod), "Required real mod is absent: " + mod);

    var level = helper.getLevel();
    BlockPos acceptorPos = helper.absolutePos(new BlockPos(2, 2, 2));
    BlockPos drivePos = helper.absolutePos(new BlockPos(1, 2, 2));
    BlockPos energyPos = helper.absolutePos(new BlockPos(3, 2, 2));
    BlockPos relayPos = helper.absolutePos(new BlockPos(4, 2, 2));
    BlockPos jarPos = helper.absolutePos(new BlockPos(4, 2, 3));
    level.setBlockAndUpdate(acceptorPos,
        block(helper, "arseng:source_acceptor").defaultBlockState());
    level.setBlockAndUpdate(drivePos, AEBlocks.DRIVE.block().defaultBlockState());
    level.setBlockAndUpdate(energyPos, AEBlocks.ENERGY_CELL.block().defaultBlockState());
    level.setBlockAndUpdate(relayPos, block(helper, "ars_nouveau:relay").defaultBlockState());
    level.setBlockAndUpdate(jarPos, block(helper, "ars_nouveau:source_jar").defaultBlockState());

    Object acceptorTile = blockEntity(helper, acceptorPos);
    helper.assertTrue(acceptorTile.getClass().getName()
        .equals("gripe._90.arseng.block.entity.SourceConverterBlockEntity")
        && acceptorTile instanceof AENetworkedBlockEntity,
        "source_acceptor is not the loaded Ars Énergistique AE2 converter");
    var acceptor = (AENetworkedBlockEntity) acceptorTile;
    var drive = blockEntity(helper, drivePos, DriveBlockEntity.class);
    var energy = blockEntity(helper, energyPos, EnergyCellBlockEntity.class);
    Object relay = blockEntity(helper, relayPos);
    Object jar = blockEntity(helper, jarPos);
    requireClass(helper, relay, "com.hollingsworth.arsnouveau.common.block.tile.RelayTile");
    requireClass(helper, jar, "com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile");
    for (int slot = 0; slot < 4; slot++)
      helper.assertTrue(drive.getInternalInventory()
          .insertItem(slot, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(),
          "ME drive rejected a real 1K storage cell in slot " + slot);
    helper.assertTrue(energy.getAECurrentPower() == 0,
        "The real AE2 energy cell unexpectedly began charged");

    var network = new Network(acceptor, drive, energy, relay, jar);
    awaitPhysicalGrid(helper, network, 0);
  }

  private static void awaitPhysicalGrid(GameTestHelper helper, Network net, int waited) {
    IGrid grid = net.grid();
    boolean ready = grid != null
        && net.drive.getMainNode().getGrid() == grid
        && net.energy.getMainNode().getGrid() == grid
        && grid.getEnergyService().getMaxStoredPower() > 1000;
    if (!ready) {
      helper.assertTrue(waited < 120,
          "Source Acceptor, ME drive and finite AE2 cell did not join one physical grid");
      helper.runAfterDelay(5, () -> awaitPhysicalGrid(helper, net, waited + 5));
      return;
    }
    double ratio = aePerSource();
    helper.assertTrue(Double.isFinite(ratio) && ratio > 0,
        "Ars Énergistique AE_PER_SOURCE is not a positive finite conversion rate");
    int initial = (int) Math.ceil(512.0 / ratio);
    helper.assertTrue(initial > 0 && initial <= 1000 && initial <= maxSource(net.jar),
        "Configured Source ratio cannot supply a bounded finite jar/relay fixture: " + ratio);
    helper.assertTrue(grid.getEnergyService().getStoredPower() == 0,
        "The ME grid had power before any Source was supplied");
    helper.assertTrue(link(net.relay, "setTakeFrom", blockPos(net.jar))
        && link(net.relay, "setSendTo", net.acceptor.getBlockPos()),
        "Native Ars relay rejected source jar or Source Acceptor capability link");
    seedSource(net.jar, initial);
    helper.assertTrue(source(net.jar) == initial && source(net.relay) == 0,
        "Finite Source fixture did not reside only in the real Ars jar");
    awaitPowered(helper, net, initial, ratio, 0);
  }

  private static void awaitPowered(GameTestHelper helper, Network net, int initial,
      double ratio, int waited) {
    IGrid grid = net.grid();
    boolean ready = grid != null
        && grid.getEnergyService().isNetworkPowered()
        && net.acceptor.getMainNode().isActive()
        && net.drive.getMainNode().isActive()
        && net.drive.getCellInventory(0) != null
        && net.energy.getAECurrentPower() > 0
        && source(net.jar) < initial;
    if (!ready) {
      helper.assertTrue(waited < 160,
          "Real Source did not flow through relay/Source Acceptor to power the ME grid: jar="
              + source(net.jar) + " relay=" + source(net.relay)
              + " AE=" + net.energy.getAECurrentPower());
      helper.runAfterDelay(5, () -> awaitPowered(helper, net, initial, ratio, waited + 5));
      return;
    }
    awaitSourceExhaustion(helper, net, initial, ratio, 0);
  }

  private static void awaitSourceExhaustion(GameTestHelper helper, Network net, int initial,
      double ratio, int waited) {
    if (source(net.jar) != 0 || source(net.relay) != 0) {
      helper.assertTrue(waited < 120,
          "Finite Source remained stranded in the jar/relay instead of reaching AE2: jar="
              + source(net.jar) + " relay=" + source(net.relay));
      helper.runAfterDelay(5, () -> awaitSourceExhaustion(helper, net, initial, ratio, waited + 5));
      return;
    }
    awaitIndexedPower(helper, net, initial, ratio, 0);
  }

  private static void awaitIndexedPower(GameTestHelper helper, Network net, int initial,
      double ratio, int waited) {
    IGrid grid = net.grid();
    double stored = grid.getEnergyService().getStoredPower();
    double cellAE = net.energy.getAECurrentPower();
    double idle = grid.getEnergyService().getIdlePowerUsage();
    boolean powered = grid.getEnergyService().isNetworkPowered();
    helper.assertTrue(source(net.jar) == 0 && source(net.relay) == 0,
        "Source reappeared while waiting for AE2 to index its finite stored power");
    if (waited == 0 || (powered && stored > 0 && cellAE > 0 && idle > 0))
      LOGGER.info("ENTRELUMEN_ARS_AE2_POWER sampleWait={} powered={} cachedStoredAE={} "
          + "physicalCellAE={} idleAEPerTick={}", waited, powered, stored, cellAE, idle);
    if (!powered || stored <= 0 || cellAE <= 0 || idle <= 0) {
      // EnergyService caches getStoredPower for up to 90 ticks after an injection.
      // Wait for its own refresh while checking the actual AE cell throughout.
      helper.assertTrue(waited < 110,
          "Real ME power did not stabilize after Source exhaustion: powered=" + powered
              + " cachedStoredAE=" + stored + " physicalCellAE=" + cellAE
              + " idleAEPerTick=" + idle + " waited=" + waited);
      helper.runAfterDelay(5, () -> awaitIndexedPower(helper, net, initial, ratio, waited + 5));
      return;
    }
    var storage = grid.getStorageService().getInventory();
    long inserted = storage.insert(COBBLESTONE, 8, Actionable.MODULATE,
        IActionSource.ofMachine(net.acceptor));
    helper.assertTrue(inserted == 8 && storage.getAvailableStacks().get(COBBLESTONE) == 8,
        "Source-powered ME drive failed to store eight real cobblestone");
    int sustainTicks = Math.clamp((int) (stored / idle / 5), 10, 35);
    helper.assertTrue(stored > idle * (2 * sustainTicks + 5),
        "Finite Source produced too little AE to show sustained ME operation");
    helper.runAfterDelay(sustainTicks,
        () -> afterSustainedInsert(helper, net, initial, ratio, stored, sustainTicks));
  }

  private static void afterSustainedInsert(GameTestHelper helper, Network net, int initial,
      double ratio, double storedAtExhaustion, int sustainTicks) {
    IGrid grid = net.grid();
    double afterFirst = grid.getEnergyService().getStoredPower();
    helper.assertTrue(source(net.jar) == 0 && source(net.relay) == 0
        && grid.getEnergyService().isNetworkPowered()
        && afterFirst > 0 && afterFirst < storedAtExhaustion
        && grid.getStorageService().getInventory().getAvailableStacks().get(COBBLESTONE) == 8,
        "ME operation did not remain powered while finite AE decreased after Source exhaustion");
    long extracted = grid.getStorageService().getInventory().extract(COBBLESTONE, 4,
        Actionable.MODULATE, IActionSource.ofMachine(net.acceptor));
    helper.assertTrue(extracted == 4
        && grid.getStorageService().getInventory().getAvailableStacks().get(COBBLESTONE) == 4,
        "Powered ME drive failed a second real storage operation");
    helper.runAfterDelay(sustainTicks,
        () -> afterSustainedExtract(helper, net, initial, ratio, storedAtExhaustion, afterFirst));
  }

  private static void afterSustainedExtract(GameTestHelper helper, Network net, int initial,
      double ratio, double storedAtExhaustion, double afterFirst) {
    IGrid grid = net.grid();
    double beforeLoad = grid.getEnergyService().getStoredPower();
    helper.assertTrue(source(net.jar) == 0 && source(net.relay) == 0
        && grid.getEnergyService().isNetworkPowered()
        && beforeLoad > 0 && beforeLoad < afterFirst
        && grid.getStorageService().getInventory().getAvailableStacks().get(COBBLESTONE) == 4,
        "ME grid did not sustain two powered operations while its AE store declined");

    // A bounded consumer request through AE2's real energy service empties its remaining
    // battery after the Source supply is gone. It never inserts or fabricates energy.
    double drawn = grid.getEnergyService().extractAEPower(beforeLoad,
        Actionable.MODULATE, PowerMultiplier.ONE);
    helper.assertTrue(drawn > 0 && drawn <= beforeLoad + 0.001,
        "AE2 refused a real energy-service load after Source was exhausted");
    awaitCutoff(helper, net, initial, ratio, storedAtExhaustion, drawn, 0);
  }

  private static void awaitCutoff(GameTestHelper helper, Network net, int initial,
      double ratio, double storedAtExhaustion, double drawn, int waited) {
    boolean cut = !net.grid().getEnergyService().isNetworkPowered();
    if (!cut) {
      helper.assertTrue(waited < 100,
          "ME grid remained powered without Source after its stored AE was drawn: remainingAE="
              + net.grid().getEnergyService().getStoredPower());
      helper.runAfterDelay(5,
          () -> awaitCutoff(helper, net, initial, ratio, storedAtExhaustion, drawn, waited + 5));
      return;
    }
    double atCutoff = net.grid().getEnergyService().getStoredPower();
    helper.assertTrue(source(net.jar) == 0 && source(net.relay) == 0
        && atCutoff < storedAtExhaustion,
        "Grid cut off without consuming its finite Source/AE supply");
    helper.runAfterDelay(40, () -> {
      helper.assertTrue(source(net.jar) == 0 && source(net.relay) == 0
          && !net.grid().getEnergyService().isNetworkPowered()
          && net.grid().getEnergyService().getStoredPower() <= atCutoff + 0.001,
          "Empty Source jar/relay repowered the ME grid or replenished AE");
      LOGGER.info("ENTRELUMEN_ARS_AE2 sourceInitial={} sourceFinal=0 ratio={} "
              + "aeAtExhaustion={} aeDrawn={} aeAtCutoff={} meItemsRetained=4 gridOff=true",
          initial, ratio, storedAtExhaustion, drawn, atCutoff);
      helper.succeed();
    });
  }

  private static Block block(GameTestHelper helper, String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(key)
        && BuiltInRegistries.BLOCK.get(key) != Blocks.AIR,
        "Required native Source block is absent: " + id);
    return BuiltInRegistries.BLOCK.get(key);
  }

  private static Object blockEntity(GameTestHelper helper, BlockPos pos) {
    Object entity = helper.getLevel().getBlockEntity(pos);
    helper.assertTrue(entity != null, "Required native block entity is absent at " + pos);
    return entity;
  }

  private static <T> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
    Object entity = blockEntity(helper, pos);
    helper.assertTrue(type.isInstance(entity), "Expected real block entity " + type.getSimpleName());
    return type.cast(entity);
  }

  private static void requireClass(GameTestHelper helper, Object object, String name) {
    helper.assertTrue(object.getClass().getName().equals(name),
        "Unexpected native Source block entity: " + object.getClass().getName());
  }

  private static int source(Object tile) {
    return ((Number) invoke(tile, "getSource", new Class<?>[0])).intValue();
  }

  private static int maxSource(Object tile) {
    return ((Number) invoke(tile, "getMaxSource", new Class<?>[0])).intValue();
  }

  private static void seedSource(Object jar, int amount) {
    invoke(jar, "setSource", new Class<?>[] {int.class}, amount);
  }

  private static boolean link(Object relay, String method, BlockPos target) {
    return (Boolean) invoke(relay, method, new Class<?>[] {BlockPos.class}, target);
  }

  private static BlockPos blockPos(Object tile) {
    return (BlockPos) invoke(tile, "getBlockPos", new Class<?>[0]);
  }

  private static double aePerSource() {
    try {
      Class<?> config = Class.forName("gripe._90.arseng.definition.ArsEngConfig");
      Field setting = config.getField("AE_PER_SOURCE");
      Object value = setting.get(null);
      return ((Number) value.getClass().getMethod("get").invoke(value)).doubleValue();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read the loaded Source-to-AE conversion setting", e);
    }
  }

  private static Object invoke(Object target, String method, Class<?>[] types, Object... args) {
    try {
      Method selected = target.getClass().getMethod(method, types);
      return selected.invoke(target, args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Native Source API call failed: " + method, e);
    }
  }

  private record Network(AENetworkedBlockEntity acceptor, DriveBlockEntity drive,
      EnergyCellBlockEntity energy, Object relay, Object jar) {
    IGrid grid() {
      return acceptor.getMainNode().getGrid();
    }
  }
}
