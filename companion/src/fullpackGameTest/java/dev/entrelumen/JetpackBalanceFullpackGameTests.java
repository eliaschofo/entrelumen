package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Installed-pack QA of Elias's jetpack balance (25 September 2026, docs/design/recipe-design-rules.md): every jetpack
 * pays 2.5 times its native energy per tick. The settings the mods read from pack/config are loaded, and the KubeJS
 * surcharge takes 1.5 times the native loss of the jetpacks that fix their cost in code.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class JetpackBalanceFullpackGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** content/jetpack-balance.json: Iron Jetpacks usage, FE/t, after the factor (defaults 32 ... 880). */
  static final Map<String, Integer> IRON_JETPACKS = new TreeMap<>(Map.ofEntries(
      Map.entry("wood", 80), Map.entry("stone", 175), Map.entry("copper", 213), Map.entry("iron", 300),
      Map.entry("bronze", 300), Map.entry("silver", 375), Map.entry("gold", 750), Map.entry("electrum", 775),
      Map.entry("invar", 875), Map.entry("steel", 875), Map.entry("diamond", 1625), Map.entry("platinum", 1800),
      Map.entry("emerald", 2200), Map.entry("creative", 0)));
  /** Oritech sections: energyUsage RF/t and fuelUsage mB/t (defaults 128/10 and 256/15). */
  static final Map<String, List<Integer>> ORITECH = Map.of(
      "basicJetpack", List.of(320, 25), "elytraJetpack", List.of(320, 25),
      "exoJetpack", List.of(640, 38), "exoElytraJetpack", List.of(640, 38));
  static final int JET_BOOTS_AIR = 30;

  private JetpackBalanceFullpackGameTests() {}

  static void requireSuite() {
    for (String mod : List.of("kubejs", "ironjetpacks", "oritech", "pneumaticcraft", "mekanism",
        "modern_industrialization", "ad_astra"))
      if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Required real mod is absent: " + mod);
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Fullpack QA cannot run against the synthetic fixture");
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
        .orElseThrow(() -> new IllegalStateException("Unregistered item " + id));
  }

  private static Object field(Object owner, String name) throws ReflectiveOperationException {
    Field field = (owner instanceof Class<?> type ? type : owner.getClass()).getField(name);
    field.setAccessible(true);
    return field.get(owner instanceof Class<?> ? null : owner);
  }

  private static int intValue(Object owner, String name) throws ReflectiveOperationException {
    return ((ModConfigSpec.IntValue) field(owner, name)).get();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void jetpackSettingsLoadTwoAndAHalfTimesTheCost(GameTestHelper helper) throws Exception {
    requireSuite();
    List<String> problems = new ArrayList<>();
    // Iron Jetpacks: the types the server loaded (and syncs to clients) from config/ironjetpacks/jetpacks.
    Class<?> registry = Class.forName("com.blakebr0.ironjetpacks.registry.JetpackRegistry");
    Object jetpacks = registry.getMethod("getJetpacks").invoke(registry.getMethod("getInstance").invoke(null));
    Map<String, Integer> usage = new TreeMap<>();
    for (Object jetpack : (List<?>) jetpacks)
      usage.put((String) field(jetpack, "name"), (Integer) field(jetpack, "usage"));
    if (!usage.equals(IRON_JETPACKS)) problems.add("Iron Jetpacks usage " + usage);
    // Oritech: the startup config the four jetpack items read.
    Class<?> oritech = Class.forName("rearth.oritech.init.OritechStartupConfig");
    Map<String, List<Integer>> loaded = new TreeMap<>();
    for (String section : ORITECH.keySet()) {
      Object config = field(oritech, section);
      loaded.put(section, List.of(intValue(config, "energyUsage"), intValue(config, "fuelUsage")));
    }
    if (!loaded.equals(new TreeMap<>(ORITECH))) problems.add("Oritech jetpacks " + loaded);
    // PneumaticCraft: air per tick of each Jet Boots upgrade.
    Object common = Class.forName("me.desht.pneumaticcraft.common.config.ConfigHelper").getMethod("common").invoke(null);
    int air = intValue(field(common, "armor"), "jetBootsAirUsage");
    if (air != JET_BOOTS_AIR) problems.add("Jet Boots air " + air);
    LOGGER.info("ENTRELUMEN_JETPACK_SETTINGS ironjetpacks={} oritech={} jetBootsAir={}", usage, loaded, air);
    helper.assertTrue(problems.isEmpty(), "Jetpack settings: " + problems);
    helper.succeed();
  }

  /** One worn piece: how much fuel it holds and what the mod itself takes in one tick of flight. */
  private record Piece(String id, ItemStack stack, ToLongFunction<ItemStack> amount, Consumer<ItemStack> nativeTick,
      long nativePerTick) {}

  private static void tickKubeJS(ServerPlayer player) throws ReflectiveOperationException {
    // KubeJS's own listener for the end of a player tick, without the rest of the pack's tick listeners.
    Class.forName("dev.latvian.mods.kubejs.player.KubeJSPlayerEventHandler")
        .getMethod("tick", PlayerTickEvent.Post.class).invoke(null, new PlayerTickEvent.Post(player));
  }

  @SuppressWarnings("unchecked")
  private static List<Piece> pieces() throws ReflectiveOperationException {
    List<Piece> pieces = new ArrayList<>();
    // Mekanism: hydrogen through its chemical capability; the jetpack's own useJetpackFuel is the native tick.
    Object chemicalType = Class.forName("mekanism.common.capabilities.Capabilities").getField("CHEMICAL").get(null);
    var chemical = (ItemCapability<Object, Void>) chemicalType.getClass().getMethod("item").invoke(chemicalType);
    Class<?> handler = Class.forName("mekanism.api.chemical.IChemicalHandler");
    Class<?> chemicalStack = Class.forName("mekanism.api.chemical.ChemicalStack");
    Class<?> action = Class.forName("mekanism.api.Action");
    Object execute = action.getField("EXECUTE").get(null);
    Object hydrogen = Class.forName("mekanism.common.registries.MekanismChemicals").getField("HYDROGEN").get(null);
    Method asStack = hydrogen.getClass().getMethod("asStack", long.class);
    Method insert = handler.getMethod("insertChemical", chemicalStack, action);
    Method inTank = handler.getMethod("getChemicalInTank", int.class);
    Method amount = chemicalStack.getMethod("getAmount");
    for (String id : List.of("mekanism:jetpack", "mekanism:jetpack_armored")) {
      ItemStack stack = new ItemStack(item(id));
      for (int fill = 0; fill < 4; fill++) // the tank takes at most 16 mB per insertion
        insert.invoke(stack.getCapability(chemical), asStack.invoke(hydrogen, 16L), execute);
      Method use = stack.getItem().getClass().getMethod("useJetpackFuel", ItemStack.class);
      pieces.add(new Piece(id, stack, s -> {
        try {
          return (Long) amount.invoke(inTank.invoke(s.getCapability(chemical), 0));
        } catch (ReflectiveOperationException error) {
          throw new IllegalStateException(error);
        }
      }, s -> {
        try {
          use.invoke(s.getItem(), s);
        } catch (ReflectiveOperationException error) {
          throw new IllegalStateException(error);
        }
      }, 1));
    }
    // Modern Industrialization: diesel through the NeoForge fluid capability; FluidFuelItemHelper.decrement is the
    // native tick, called twice while climbing.
    ItemStack diesel = new ItemStack(item("modern_industrialization:diesel_jetpack"));
    var fuel = BuiltInRegistries.FLUID.get(ResourceLocation.parse("modern_industrialization:diesel"));
    diesel.getCapability(Capabilities.FluidHandler.ITEM).fill(new FluidStack(fuel, 1000), IFluidHandler.FluidAction.EXECUTE);
    Method decrement = Class.forName("aztech.modern_industrialization.items.FluidFuelItemHelper")
        .getMethod("decrement", ItemStack.class);
    pieces.add(new Piece("modern_industrialization:diesel_jetpack", diesel,
        s -> s.getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0).getAmount(), s -> {
          try {
            decrement.invoke(null, s);
            decrement.invoke(null, s);
          } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
          }
        }, 2));
    // Ad Astra: energy through the NeoForge capability; full flight takes 100 FE in JetSuitItem.consume.
    ItemStack suit = new ItemStack(item("ad_astra:jet_suit"));
    suit.getCapability(Capabilities.EnergyStorage.ITEM).receiveEnergy(100_000, false);
    pieces.add(new Piece("ad_astra:jet_suit", suit,
        s -> s.getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored(),
        s -> s.getCapability(Capabilities.EnergyStorage.ITEM).extractEnergy(100, false), 100));
    return pieces;
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void hardcodedJetpacksPayTheSurchargePerTick(GameTestHelper helper) throws Exception {
    requireSuite();
    List<String> problems = new ArrayList<>();
    Map<String, String> seen = new LinkedHashMap<>();
    for (Piece piece : pieces()) {
      ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
          new GameProfile(UUID.randomUUID(), "entrelumen_jetpack_" + seen.size()));
      ItemStack full = piece.stack().copy();
      player.setItemSlot(EquipmentSlot.CHEST, piece.stack());
      ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
      long start = piece.amount().applyAsLong(worn);
      if (start < 10 * piece.nativePerTick()) {
        problems.add(piece.id() + " was not filled: " + start);
        continue;
      }
      tickKubeJS(player); // the script's first look: nothing to charge
      tickKubeJS(player); // an idle tick: nothing lost, nothing charged
      long idle = piece.amount().applyAsLong(worn);
      for (int flight = 0; flight < 4; flight++) {
        piece.nativeTick().accept(worn);
        tickKubeJS(player);
      }
      long flown = piece.amount().applyAsLong(worn);
      // Four native ticks and 1.5 times as much again: 2.5 times in total.
      long expected = idle - 10 * piece.nativePerTick();
      // Swapping back to the full copy is not flight, and neither is the emptier piece after it.
      player.setItemSlot(EquipmentSlot.CHEST, full);
      long before = piece.amount().applyAsLong(full);
      tickKubeJS(player);
      tickKubeJS(player);
      long after = piece.amount().applyAsLong(full);
      ItemStack emptier = worn.copy();
      piece.nativeTick().accept(emptier);
      piece.nativeTick().accept(emptier);
      long emptierBefore = piece.amount().applyAsLong(emptier);
      player.setItemSlot(EquipmentSlot.CHEST, emptier);
      tickKubeJS(player);
      if (piece.amount().applyAsLong(emptier) != emptierBefore)
        problems.add(piece.id() + " was charged after a swap to an emptier piece");
      seen.put(piece.id(), start + " -> idle " + idle + " -> flown " + flown + " (expected " + expected + "), swap "
          + before + " -> " + after);
      if (idle != start) problems.add(piece.id() + " lost fuel while idle: " + start + " -> " + idle);
      if (flown != expected) problems.add(piece.id() + " after four native ticks has " + flown + ", expected " + expected);
      if (after != before) problems.add(piece.id() + " was charged after a swap: " + before + " -> " + after);
    }
    LOGGER.info("ENTRELUMEN_JETPACK_SURCHARGE {}", seen);
    helper.assertTrue(problems.isEmpty(), "Jetpack surcharge: " + problems);
    helper.succeed();
  }
}
