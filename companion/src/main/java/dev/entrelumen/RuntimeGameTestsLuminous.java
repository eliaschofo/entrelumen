package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests for the luminous armour material, tool tier, never-breaking gear, light repair,
 * the set bonus and the sword. Excluded from the distributable jar by the {@code RuntimeGameTests*}
 * pattern; the pack recipes are covered by the full-pack {@code LuminousGameTests}.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsLuminous {
  private RuntimeGameTestsLuminous() {}

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    Session(GameTestHelper helper, String name, BlockPos relative) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
      var pos = helper.absolutePos(relative);
      player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public void close() {
      try {
        connection.disconnect(Component.literal("Entrelumen luminous QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static double sum(ItemStack stack, Holder<Attribute> attribute, EquipmentSlotGroup group) {
    double[] total = {0};
    stack.getAttributeModifiers().forEach(group, (holder, modifier) -> {
      if (holder.equals(attribute)) total[0] += modifier.amount();
    });
    return total[0];
  }

  private static List<ItemStack> allGear() {
    return List.of(new ItemStack(Luminous.HELMET.get()), new ItemStack(Luminous.CHESTPLATE.get()),
        new ItemStack(Luminous.LEGGINGS.get()), new ItemStack(Luminous.BOOTS.get()),
        new ItemStack(Luminous.SWORD.get()), new ItemStack(Luminous.PICKAXE.get()),
        new ItemStack(Luminous.AXE.get()), new ItemStack(Luminous.SHOVEL.get()), new ItemStack(Luminous.HOE.get()));
  }

  private static void spend(GameTestHelper helper, ItemStack stack) {
    stack.hurtAndBreak(stack.getMaxDamage() * 3, helper.getLevel(), null,
        item -> helper.fail("A luminous piece broke: " + BuiltInRegistries.ITEM.getKey(item)));
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void luminousArmourMaterialAndTierFollowTheStatTable(GameTestHelper helper) {
    var material = Luminous.MATERIAL.value();
    helper.assertTrue(material.getDefense(ArmorItem.Type.HELMET) == LuminousRules.HELMET_ARMOR
        && material.getDefense(ArmorItem.Type.CHESTPLATE) == LuminousRules.CHESTPLATE_ARMOR
        && material.getDefense(ArmorItem.Type.LEGGINGS) == LuminousRules.LEGGINGS_ARMOR
        && material.getDefense(ArmorItem.Type.BOOTS) == LuminousRules.BOOTS_ARMOR
        && material.toughness() == LuminousRules.TOUGHNESS
        && material.knockbackResistance() == LuminousRules.KNOCKBACK_RESISTANCE
        && material.enchantmentValue() == LuminousRules.ENCHANTABILITY, "Armour material differs from the stat table");
    helper.assertTrue(material.repairIngredient().get().test(new ItemStack(Luminous.INGOT.get()))
        && !material.repairIngredient().get().test(new ItemStack(Items.NETHERITE_INGOT)), "Repair ingredient is not the ingot");
    var layer = material.layers().getFirst();
    helper.assertTrue(material.layers().size() == 1
        && layer.texture(false).equals(ResourceLocation.parse("entrelumen:textures/models/armor/luminous_layer_1.png"))
        && layer.texture(true).equals(ResourceLocation.parse("entrelumen:textures/models/armor/luminous_layer_2.png")),
        "Armour layer textures moved");
    int[] durability = {11 * LuminousRules.ARMOR_DURABILITY_FACTOR, 16 * LuminousRules.ARMOR_DURABILITY_FACTOR,
        15 * LuminousRules.ARMOR_DURABILITY_FACTOR, 13 * LuminousRules.ARMOR_DURABILITY_FACTOR};
    var armour = List.of(Luminous.HELMET, Luminous.CHESTPLATE, Luminous.LEGGINGS, Luminous.BOOTS);
    var groups = List.of(EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET);
    var tags = List.of(ItemTags.HEAD_ARMOR, ItemTags.CHEST_ARMOR, ItemTags.LEG_ARMOR, ItemTags.FOOT_ARMOR);
    for (int i = 0; i < 4; i++) {
      var stack = new ItemStack(armour.get(i).get());
      helper.assertTrue(stack.getMaxDamage() == durability[i], "Durability of " + stack + " is " + stack.getMaxDamage());
      helper.assertTrue(sum(stack, Attributes.ARMOR, groups.get(i)) == LuminousRules.armorByPiece().get(i)
          && sum(stack, Attributes.ARMOR_TOUGHNESS, groups.get(i)) == LuminousRules.TOUGHNESS
          && Math.abs(sum(stack, Attributes.KNOCKBACK_RESISTANCE, groups.get(i)) - LuminousRules.KNOCKBACK_RESISTANCE) < 1e-6,
          "Worn attributes differ for " + stack);
      helper.assertTrue(stack.is(tags.get(i)) && stack.is(Luminous.GEAR_TAG) && stack.isEnchantable()
          && stack.getItem().getEnchantmentValue(stack) == LuminousRules.ENCHANTABILITY, "Tags or enchantability of " + stack);
    }
    var sword = new ItemStack(Luminous.SWORD.get());
    helper.assertTrue(sword.getMaxDamage() == LuminousRules.TOOL_USES && sword.is(ItemTags.SWORDS)
        && sum(sword, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) + 1 == LuminousRules.swordDamage()
        && Math.abs(sum(sword, Attributes.ATTACK_SPEED, EquipmentSlotGroup.MAINHAND) - LuminousRules.SWORD_SPEED) < 1e-6,
        "Sword stats differ");
    var axe = new ItemStack(Luminous.AXE.get());
    helper.assertTrue(sum(axe, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) + 1 == LuminousRules.axeDamage()
        && axe.is(ItemTags.AXES), "Axe stats differ");
    // Like netherite tools, the attributes live in the default component that mods such as
    // Apotheosis read directly to file a weapon's affix category.
    for (var tool : List.of(sword, axe, new ItemStack(Luminous.PICKAXE.get()), new ItemStack(Luminous.SHOVEL.get())))
      helper.assertTrue(!tool.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
          net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
          "Attributes missing from the default component of " + tool);
    var pickaxe = new ItemStack(Luminous.PICKAXE.get());
    helper.assertTrue(pickaxe.is(ItemTags.PICKAXES) && pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()) == LuminousRules.TOOL_SPEED
        && pickaxe.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())
        && pickaxe.isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState())
        && pickaxe.isCorrectToolForDrops(Blocks.CRYING_OBSIDIAN.defaultBlockState()), "Pickaxe cannot harvest netherite-tier blocks");
    helper.assertTrue(new ItemStack(Luminous.SHOVEL.get()).is(ItemTags.SHOVELS) && new ItemStack(Luminous.HOE.get()).is(ItemTags.HOES)
        && Math.abs(sum(new ItemStack(Luminous.HOE.get()), Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND)
            - (LuminousRules.TOOL_ATTACK_BONUS + LuminousRules.HOE_DAMAGE)) < 1e-6, "Shovel or hoe differ");
    for (var stack : allGear())
      helper.assertTrue(stack.has(DataComponents.FIRE_RESISTANT) && !stack.hasFoil()
          && stack.getItem().isValidRepairItem(stack, new ItemStack(Luminous.INGOT.get()))
          && stack.getHoverName().getStyle().getColor() != null
          && stack.getHoverName().getStyle().getColor().getValue() == LuminousRules.LUMINOUS_COLOR,
          "Fire resistance, glint, repair or name colour of " + stack);
    int count = 0;
    for (var discipline : LuminousRules.Discipline.values()) {
      var stack = new ItemStack(Luminous.LUMINOSITIES.get(discipline).get());
      helper.assertTrue(stack.is(Luminous.LUMINOSITIES_TAG) && !stack.hasFoil()
          && stack.getHoverName().getStyle().getColor().getValue() == discipline.color
          && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(discipline.item()), "Luminosity " + discipline);
      count++;
    }
    var ingot = new ItemStack(Luminous.INGOT.get());
    helper.assertTrue(count == 6 && !ingot.hasFoil() && ingot.has(DataComponents.FIRE_RESISTANT)
        && ingot.getHoverName().getStyle().getColor().getValue() == LuminousRules.LUMINOUS_COLOR, "Ingot or Luminosity count");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void luminousGearGoesDarkInsteadOfBreakingAndLightMendsIt(GameTestHelper helper) {
    for (var stack : allGear()) {
      spend(helper, stack);
      helper.assertTrue(stack.getCount() == 1 && stack.getDamageValue() == stack.getMaxDamage() - 1
          && LuminousGear.dimmed(stack), "Not resting dimmed: " + stack);
      stack.hurtAndBreak(5, helper.getLevel(), null, item -> helper.fail("Broke on its last point"));
      helper.assertTrue(stack.getCount() == 1 && LuminousGear.dimmed(stack), "Last point was spent");
    }
    var chest = new ItemStack(Luminous.CHESTPLATE.get());
    spend(helper, chest);
    helper.assertTrue(sum(chest, Attributes.ARMOR, EquipmentSlotGroup.CHEST) == 0
        && sum(chest, Attributes.ARMOR_TOUGHNESS, EquipmentSlotGroup.CHEST) == 0, "A dark chestplate still protects");
    var sword = new ItemStack(Luminous.SWORD.get());
    spend(helper, sword);
    helper.assertTrue(sum(sword, Attributes.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND) == 0, "A dark sword still hits hard");
    var pickaxe = new ItemStack(Luminous.PICKAXE.get());
    spend(helper, pickaxe);
    helper.assertTrue(pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 1f
        && !pickaxe.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "A dark pickaxe still mines");
    // Anvil repair with one ingot brings it back.
    pickaxe.setDamageValue(pickaxe.getDamageValue() - pickaxe.getMaxDamage() / 4);
    helper.assertTrue(!LuminousGear.dimmed(pickaxe) && pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()) == LuminousRules.TOOL_SPEED,
        "A mended pickaxe stays dark");

    // Light at the eyes mends every luminous piece the player carries: inventory, armour and offhand.
    BlockPos feet = new BlockPos(2, 1, 2);
    for (var offset : List.of(new BlockPos(1, 2, 2), new BlockPos(3, 2, 2), new BlockPos(2, 2, 1), new BlockPos(2, 2, 3)))
      helper.setBlock(offset, Blocks.GLOWSTONE);
    helper.runAfterDelay(10, () -> {
      try (var session = new Session(helper, "LuminousMend", feet)) {
        var player = session.player;
        var carried = new ItemStack(Luminous.SWORD.get());
        carried.setDamageValue(500);
        var worn = new ItemStack(Luminous.HELMET.get());
        worn.setDamageValue(worn.getMaxDamage() - 1);
        var held = new ItemStack(Luminous.SHOVEL.get());
        held.setDamageValue(2);
        var other = new ItemStack(Items.NETHERITE_SWORD);
        other.setDamageValue(100);
        player.getInventory().setItem(3, carried);
        player.getInventory().setItem(4, other);
        player.setItemSlot(EquipmentSlot.HEAD, worn);
        player.setItemSlot(EquipmentSlot.OFFHAND, held);
        int brightness = Luminous.brightness(player);
        helper.assertTrue(brightness >= LuminousRules.REPAIR_MIN_BRIGHTNESS, "Test spot is not lit: " + brightness);
        int step = LuminousRules.lightRepair(brightness, 1000);
        Luminous.refresh(player);
        helper.assertTrue(player.getInventory().getItem(3).getDamageValue() == 500 - step
            && player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == worn.getMaxDamage() - 1 - step
            && !LuminousGear.dimmed(player.getItemBySlot(EquipmentSlot.HEAD))
            && player.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue() == Math.max(0, 2 - step)
            && player.getInventory().getItem(4).getDamageValue() == 100,
            "Light repair did not follow brightness " + brightness);
        helper.assertTrue(Luminous.repair(player.getInventory(), LuminousRules.REPAIR_MIN_BRIGHTNESS - 1) == 0,
            "Repaired below brightness 12");
      }
      helper.succeed();
    });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void luminousFullSetGrantsSteadyNightVisionAndCancelsFalls(GameTestHelper helper) {
    try (var session = new Session(helper, "LuminousSet", new BlockPos(2, 1, 2))) {
      var player = session.player;
      var pieces = List.of(Luminous.HELMET, Luminous.CHESTPLATE, Luminous.LEGGINGS, Luminous.BOOTS);
      for (int i = 0; i < 4; i++)
        player.setItemSlot(Luminous.SET_SLOTS.get(i), new ItemStack(pieces.get(i).get()));
      helper.assertTrue(Luminous.fullSet(player), "Four luminous pieces are not a full set");
      Luminous.refresh(player);
      MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
      helper.assertTrue(vision != null && vision.isAmbient() && !vision.isVisible()
          && vision.getDuration() == LuminousRules.NIGHT_VISION_TICKS && !vision.endsWithin(200), "No steady night vision");
      float[] fall = CommonHooks.onLivingFall(player, 40f, 1f);
      helper.assertTrue(fall != null && fall[1] == 0f, "Full set still takes fall damage");

      // A dark helmet breaks the set: the set's night vision goes, falls hurt again.
      var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
      spend(helper, helmet);
      helper.assertTrue(!Luminous.fullSet(player), "A dark piece still completes the set");
      // setBonus alone: a full refresh would first mend the helmet in the test area's daylight.
      Luminous.setBonus(player);
      helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION),
          "The set's night vision outlived the set: " + player.getEffect(MobEffects.NIGHT_VISION));
      fall = CommonHooks.onLivingFall(player, 40f, 1f);
      helper.assertTrue(fall != null && fall[1] == 1f, "Falls stay harmless without the set: "
          + (fall == null ? "cancelled" : fall[1]));

      // Night vision from a potion is never removed by the set.
      helmet.setDamageValue(0);
      Luminous.setBonus(player);
      player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600));
      player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
      Luminous.setBonus(player);
      var potion = player.getEffect(MobEffects.NIGHT_VISION);
      helper.assertTrue(potion != null && !potion.isAmbient() && potion.getDuration() > 3000, "Removed a potion's night vision");

      // Whoever wears it: a zombie with the full set ignores falls too.
      var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
      for (int i = 0; i < 4; i++)
        zombie.setItemSlot(Luminous.SET_SLOTS.get(i), new ItemStack(pieces.get(i).get()));
      fall = CommonHooks.onLivingFall(zombie, 40f, 1f);
      helper.assertTrue(fall != null && fall[1] == 0f, "Set bonus is player-only");
      zombie.discard();
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void luminousSwordRevealsTargetsAndSmitesTheUndead(GameTestHelper helper) {
    try (var session = new Session(helper, "LuminousSword", new BlockPos(2, 1, 2))) {
      var player = session.player;
      var sword = new ItemStack(Luminous.SWORD.get());
      player.setItemSlot(EquipmentSlot.MAINHAND, sword);
      var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
      var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));
      var source = player.damageSources().playerAttack(player);
      Item item = sword.getItem();
      float hit = LuminousRules.swordDamage();
      helper.assertTrue(item.getAttackDamageBonus(zombie, hit, source) == hit * LuminousRules.SWORD_UNDEAD_BONUS
          && item.getAttackDamageBonus(cow, hit, source) == 0f, "Undead bonus differs");
      item.hurtEnemy(sword, cow, player);
      var glow = cow.getEffect(MobEffects.GLOWING);
      helper.assertTrue(glow != null && glow.getDuration() == LuminousRules.SWORD_REVEAL_TICKS, "A hit did not reveal the target");
      spend(helper, sword);
      var dark = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 1));
      item.hurtEnemy(sword, dark, player);
      helper.assertTrue(!dark.hasEffect(MobEffects.GLOWING) && item.getAttackDamageBonus(zombie, hit, source) == 0f,
          "A dark sword still reveals or smites");
      zombie.discard();
      cow.discard();
      dark.discard();
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void luminousFullSetFliesWithoutTouchingOtherFlight(GameTestHelper helper) {
    try (var session = new Session(helper, "LuminousFlight", new BlockPos(2, 1, 2))) {
      var player = session.player;
      var pieces = List.of(Luminous.HELMET, Luminous.CHESTPLATE, Luminous.LEGGINGS, Luminous.BOOTS);
      helper.assertTrue(!player.mayFly(), "A survival player already flies");
      for (int i = 0; i < 4; i++)
        player.setItemSlot(Luminous.SET_SLOTS.get(i), new ItemStack(pieces.get(i).get()));
      Luminous.setBonus(player);
      helper.assertTrue(player.mayFly() && Luminous.setFlight(player), "The full set does not fly");
      // Taking a piece off in mid-air ends the set's flight and lands the player on slow falling.
      player.getAbilities().flying = true;
      var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
      player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      Luminous.setBonus(player);
      helper.assertTrue(!player.mayFly() && !player.getAbilities().flying && !Luminous.setFlight(player)
          && player.hasEffect(MobEffects.SLOW_FALLING), "Losing the set left flight on or dropped the player");
      // Another flight modifier (a ring or jetpack) and abilities.mayfly survive the set coming and going.
      var flight = player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT);
      var ring = ResourceLocation.parse("entrelumen_test:ring");
      flight.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ring, 1.0,
          net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
      player.setItemSlot(EquipmentSlot.HEAD, helmet);
      Luminous.setBonus(player);
      player.getAbilities().flying = true;
      player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      Luminous.setBonus(player);
      helper.assertTrue(player.mayFly() && player.getAbilities().flying && flight.hasModifier(ring)
          && !Luminous.setFlight(player), "The set removed someone else's flight");
      flight.removeModifier(ring);
      player.getAbilities().mayfly = true;
      player.setItemSlot(EquipmentSlot.HEAD, helmet);
      Luminous.setBonus(player);
      player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      Luminous.setBonus(player);
      helper.assertTrue(player.mayFly() && player.getAbilities().mayfly, "The set cleared abilities.mayfly");
      player.getAbilities().mayfly = false;
      // A dark piece breaks the set too.
      player.setItemSlot(EquipmentSlot.HEAD, helmet);
      Luminous.setBonus(player);
      helper.assertTrue(Luminous.setFlight(player), "The rebuilt set does not fly");
      spend(helper, helmet);
      Luminous.setBonus(player);
      helper.assertTrue(!Luminous.setFlight(player), "A dark piece still flies");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void luminousDynamicLightDataMatchesLitPiecesOnly(GameTestHelper helper) throws Exception {
    var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
        helper.getLevel().registryAccess());
    java.util.Map<String, net.minecraft.advancements.critereon.ItemPredicate> matches = new java.util.HashMap<>();
    for (String file : List.of("luminous_gear", "luminous_materials")) {
      try (var stream = RuntimeGameTestsLuminous.class.getResourceAsStream(
          "/assets/entrelumen/dynamiclights/item/" + file + ".json")) {
        helper.assertTrue(stream != null, "Dynamic light data missing: " + file);
        var json = com.google.gson.JsonParser.parseReader(
            new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        matches.put(file, net.minecraft.advancements.critereon.ItemPredicate.CODEC
            .parse(ops, json.get("match")).getOrThrow());
      }
    }
    var gear = matches.get("luminous_gear");
    var sword = new ItemStack(Luminous.SWORD.get());
    helper.assertTrue(gear.test(sword) && gear.test(new ItemStack(Luminous.CHESTPLATE.get()))
        && !gear.test(new ItemStack(Items.NETHERITE_SWORD)), "Gear light predicate is wrong");
    spend(helper, sword);
    helper.assertTrue(!gear.test(sword), "A dark sword still lights");
    var materials = matches.get("luminous_materials");
    helper.assertTrue(materials.test(new ItemStack(Luminous.INGOT.get()))
        && materials.test(new ItemStack(Luminous.LUMINOSITIES.get(LuminousRules.Discipline.ARCANE).get()))
        && !materials.test(new ItemStack(Items.GLOWSTONE)), "Material light predicate is wrong");
    helper.succeed();
  }
}
