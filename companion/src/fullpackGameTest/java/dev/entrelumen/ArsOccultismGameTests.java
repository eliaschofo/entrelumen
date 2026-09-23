package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Runs a genuine Occultism crusher inside an Ars containment jar via its loaded item capability. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ArsOccultismGameTests {
  private static final ResourceLocation JAR = ResourceLocation.parse("ars_nouveau:mob_jar");
  private static final ResourceLocation FOLIOT = ResourceLocation.parse("occultism:foliot");
  private static final ResourceLocation RECIPE = ResourceLocation.parse("occultism:crushing/othercobblestone");
  private static final ResourceLocation INPUT = ResourceLocation.parse("occultism:otherstone");
  private static final ResourceLocation OUTPUT = ResourceLocation.parse("occultism:othercobblestone");
  private static final String JOB = "occultism:crush_tier1";
  private static final String OUTPUT_TAG = "occultism:dropped_by_crusher";

  @GameTest(template = "empty", timeoutTicks = 360)
  public static void containedFoliotCrushesOneInputThroughNativeCapability(GameTestHelper helper)
      throws Exception {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Ars Occultism QA requires the three native mods");
    for (String mod : List.of("ars_nouveau", "occultism", "ars_ocultas"))
      helper.assertTrue(ModList.get().isLoaded(mod), "Missing native magic mod " + mod);

    var level = helper.getLevel();
    Item inputItem = item(helper, INPUT);
    Item outputItem = item(helper, OUTPUT);
    var holder = level.getRecipeManager().byKey(RECIPE);
    helper.assertTrue(holder.isPresent(), "Native Occultism crushing recipe is not loaded: " + RECIPE);
    var recipe = holder.orElseThrow().value();
    helper.assertTrue(recipe.getClass().getName().equals(
        "com.klikli_dev.occultism.crafting.recipe.CrushingRecipe"),
        "Unexpected crushing recipe implementation: " + recipe.getClass().getName());
    Class<?> tieredInputClass = Class.forName(
        "com.klikli_dev.occultism.crafting.recipe.TieredSingleRecipeInput");
    RecipeInput tierOneInput = (RecipeInput) tieredInputClass
        .getConstructor(ItemStack.class, int.class).newInstance(new ItemStack(inputItem), 1);
    helper.assertTrue(Boolean.TRUE.equals(recipe.getClass()
        .getMethod("matches", tieredInputClass, net.minecraft.world.level.Level.class)
        .invoke(recipe, tierOneInput, level)),
        "The loaded Occultism recipe rejects the Foliot tier or ingredient");
    ItemStack expected = recipe.getResultItem(level.registryAccess());
    helper.assertTrue(expected.is(outputItem) && expected.getCount() == 1,
        "Native crusher recipe does not declare one othercobblestone");
    helper.assertTrue(Boolean.TRUE.equals(recipe.getClass()
        .getMethod("getIgnoreCrushingMultiplier").invoke(recipe)),
        "The selected recipe is no longer a deterministic one-for-one case");

    BlockPos jarPos = helper.absolutePos(new BlockPos(2, 2, 2));
    level.setBlockAndUpdate(jarPos, block(helper, JAR).defaultBlockState());
    BlockEntity jar = level.getBlockEntity(jarPos);
    helper.assertTrue(jar != null && jar.getClass().getName().equals(
        "com.hollingsworth.arsnouveau.common.block.tile.MobJarTile"),
        "Native Ars containment jar block entity is missing");

    EntityType<?> foliotType = BuiltInRegistries.ENTITY_TYPE.get(FOLIOT);
    helper.assertTrue(foliotType != null, "Native Foliot entity type is missing");
    Entity summoned = foliotType.create(level);
    Class<?> spiritClass = Class.forName("com.klikli_dev.occultism.common.entity.spirit.SpiritEntity");
    Class<?> jobClass = Class.forName("com.klikli_dev.occultism.common.entity.job.SpiritJob");
    helper.assertTrue(summoned != null && spiritClass.isInstance(summoned),
        "Native Foliot spirit could not be created");
    Class<?> jobsClass = Class.forName("com.klikli_dev.occultism.registry.OccultismSpiritJobs");
    Object factory = ((Supplier<?>) jobsClass.getField("CRUSH_TIER1").get(null)).get();
    Object crusherJob = factory.getClass().getMethod("create", spiritClass).invoke(factory, summoned);
    spiritClass.getMethod("setJob", jobClass, boolean.class).invoke(summoned, crusherJob, false);
    helper.assertTrue(JOB.equals(spiritClass.getMethod("getJobID").invoke(summoned)),
        "The native Foliot did not retain its tier-one crusher job");

    boolean captured = (Boolean) jar.getClass().getMethod("setEntityData", Entity.class)
        .invoke(jar, summoned);
    helper.assertTrue(captured, "Ars jar rejected the native Occultism spirit");
    jar = level.getBlockEntity(jarPos);
    Entity contained = (Entity) jar.getClass().getMethod("getEntity").invoke(jar);
    helper.assertTrue(contained != null && contained != summoned && spiritClass.isInstance(contained),
        "Ars jar did not rehydrate a contained Occultism spirit");
    helper.assertTrue(JOB.equals(spiritClass.getMethod("getJobID").invoke(contained)),
        "The contained spirit lost its Occultism crusher job");

    AtomicBoolean occultasBehavior = new AtomicBoolean();
    jar.getClass().getMethod("dispatchBehavior", Consumer.class).invoke(jar,
        (Consumer<Object>) behavior -> occultasBehavior.set(occultasBehavior.get()
            || behavior.getClass().getName().equals(
                "com.mystchonky.arsocultas.content.spirit_jar.SpiritJarBehaviour")));
    helper.assertTrue(occultasBehavior.get(), "Ars Ocultas spirit behavior is not registered on the jar");

    IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, jarPos, Direction.UP);
    helper.assertTrue(handler != null && handler.getSlots() == 1,
        "Loaded Ars Ocultas jar does not expose the native Foliot item capability");
    helper.assertTrue(handler.getStackInSlot(0).isEmpty(), "Newly captured Foliot has stale input");
    helper.assertTrue(outputCount(helper, jarPos, outputItem) == 0,
        "The test structure contains pre-existing crusher output");

    ItemStack offered = new ItemStack(inputItem);
    helper.assertTrue(handler.insertItem(0, offered.copy(), true).isEmpty(),
        "Jar capability cannot simulate insertion of the native ingredient");
    helper.assertTrue(handler.getStackInSlot(0).isEmpty(),
        "Simulated insertion consumed an ingredient");
    helper.assertTrue(handler.insertItem(0, offered.copy(), false).isEmpty(),
        "Jar capability refused the real ingredient");
    helper.assertTrue(handler.getStackInSlot(0).is(inputItem)
        && handler.getStackInSlot(0).getCount() == 1,
        "Jar capability did not store exactly one real ingredient");

    awaitProduct(helper, jarPos, handler, inputItem, outputItem, 0);
  }

  private static void awaitProduct(GameTestHelper helper, BlockPos jarPos, IItemHandler handler,
      Item inputItem, Item outputItem, int waited) {
    int produced = outputCount(helper, jarPos, outputItem);
    if (produced == 0 && waited < 220) {
      helper.runAfterDelay(5, () -> awaitProduct(
          helper, jarPos, handler, inputItem, outputItem, waited + 5));
      return;
    }
    helper.assertTrue(produced == 1,
        "Contained Foliot did not produce exactly one native output after " + waited
            + " ticks; observed " + produced);
    helper.assertTrue(handler.getStackInSlot(0).isEmpty(),
        "Native crusher produced output without consuming exactly one input");
    helper.assertTrue(inputCount(helper, jarPos, inputItem) == 0,
        "The consumed ingredient was also dropped into the world");

    // A retry that only simulates insertion must not commit another ingredient.
    helper.assertTrue(handler.insertItem(0, new ItemStack(inputItem), true).isEmpty(),
        "Native jar capability stopped accepting the ingredient on retry");
    helper.assertTrue(handler.getStackInSlot(0).isEmpty(),
        "Retry simulation committed a second ingredient");
    helper.runAfterDelay(80, () -> {
      helper.assertTrue(handler.getStackInSlot(0).isEmpty(),
          "The completed native job recreated an ingredient");
      helper.assertTrue(outputCount(helper, jarPos, outputItem) == 1,
          "The completed native job duplicated output on retry or later ticks");
      LogUtils.getLogger().info(
          "ENTRELUMEN_ARS_OCCULTISM_NATIVE jar={} spirit={} job={} recipe={} input=1 output=1 retryDuplicate=false",
          JAR, FOLIOT, JOB, RECIPE);
      helper.succeed();
    });
  }

  private static int outputCount(GameTestHelper helper, BlockPos jarPos, Item outputItem) {
    int total = 0;
    for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
        new AABB(jarPos).inflate(5.0), ItemEntity::isAlive)) {
      if (entity.getItem().is(outputItem) && entity.getTags().contains(OUTPUT_TAG))
        total += entity.getItem().getCount();
    }
    return total;
  }

  private static int inputCount(GameTestHelper helper, BlockPos jarPos, Item inputItem) {
    int total = 0;
    for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
        new AABB(jarPos).inflate(5.0), ItemEntity::isAlive))
      if (entity.getItem().is(inputItem)) total += entity.getItem().getCount();
    return total;
  }

  private static Item item(GameTestHelper helper, ResourceLocation id) {
    Item item = BuiltInRegistries.ITEM.get(id);
    helper.assertTrue(item != Items.AIR, "Missing native item " + id);
    return item;
  }

  private static Block block(GameTestHelper helper, ResourceLocation id) {
    Block block = BuiltInRegistries.BLOCK.get(id);
    helper.assertTrue(block != null && BuiltInRegistries.BLOCK.containsKey(id),
        "Missing native block " + id);
    return block;
  }
}
