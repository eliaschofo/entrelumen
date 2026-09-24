package dev.entrelumen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/**
 * Turns shop and native tables into vanilla {@link MerchantOffer}s: plain items, enchanted books,
 * raw data components, Heliodor lore books and blank survey maps. Offers whose item or enchantment
 * is missing (a mod absent from this instance) are skipped, so the same tables serve the full pack
 * and the isolated test server. Survey maps chart the nearest structure when used in the
 * Overworld, from where the player stands.
 */
final class CommerceOffers {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** Custom data key of a blank survey map. */
  static final String SURVEY = "entrelumen_survey";
  /** Chunk radius of a survey's structure search, as vanilla explorer maps. */
  static final int SURVEY_RADIUS = 100;
  /** Demand carried from one restock to the next stays within this range. */
  static final int MAX_DEMAND = 50;
  private static final Set<String> WARNED = new HashSet<>();

  private CommerceOffers() {}

  /** What closes an offer for a player: the act gate, and for a native's Luminosity, sleep. */
  record Lock(ProtectionRules.ActGate gate, boolean luminosity) {}

  /** Built offers and, aligned with them, their locks. */
  record Built(MerchantOffers offers, List<Lock> locks) {}

  /**
   * Builds a table's offers. {@code previous} (possibly empty) lends each offer its demand when the
   * same result sits at the same index, after vanilla's restock update, kept within 0..50.
   */
  static Built build(ServerLevel level, CommerceRules.Table table, MerchantOffers previous) {
    RegistryAccess access = level.registryAccess();
    MerchantOffers offers = new MerchantOffers();
    List<Lock> locks = new ArrayList<>();
    List<String> explicit = table.explicitItems();
    for (CommerceRules.OfferSpec spec : table.offers()) {
      if (spec.sell().expandsTag()) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(spec.sell().tag()));
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> items.add(holder.value()));
        items.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        for (Item item : items) {
          if (explicit.contains(BuiltInRegistries.ITEM.getKey(item).toString())) continue;
          ItemStack stack = new ItemStack(item);
          stack.setCount(Math.min(spec.sell().count(), stack.getMaxStackSize()));
          add(offers, locks, spec, stack, false, previous, table.id());
        }
        continue;
      }
      ItemStack stack = stack(access, spec.sell(), table.id());
      if (stack != null) add(offers, locks, spec, stack, false, previous, table.id());
    }
    if (table.luminosity() != null) {
      ItemStack stack = stack(access, table.luminosity().sell(), table.id());
      if (stack != null) add(offers, locks, table.luminosity(), stack, true, previous, table.id());
    }
    return new Built(offers, locks);
  }

  private static void add(MerchantOffers offers, List<Lock> locks, CommerceRules.OfferSpec spec, ItemStack result,
      boolean luminosity, MerchantOffers previous, String table) {
    ItemCost price = cost(spec.price(), table);
    if (price == null) return;
    Optional<ItemCost> extra = Optional.empty();
    if (spec.extra() != null) {
      ItemCost cost = cost(spec.extra(), table);
      if (cost == null) return;
      extra = Optional.of(cost);
    }
    int index = offers.size(), demand = 0;
    if (index < previous.size() && ItemStack.isSameItem(previous.get(index).getResult(), result)) {
      MerchantOffer old = previous.get(index);
      old.updateDemand();
      demand = Math.clamp(old.getDemand(), 0, MAX_DEMAND);
    }
    offers.add(new MerchantOffer(price, extra, result, 0, spec.maxUses(), spec.xp(), spec.priceMultiplier(), demand));
    locks.add(new Lock(spec.gate(), luminosity));
  }

  private static ItemCost cost(CommerceRules.Cost cost, String table) {
    Optional<Item> item = item(cost.item(), table);
    return item.map(value -> new ItemCost(value, Math.min(cost.count(), new ItemStack(value).getMaxStackSize()))).orElse(null);
  }

  private static Optional<Item> item(String id, String table) {
    Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).filter(value -> value != Items.AIR);
    if (item.isEmpty()) warnOnce(table + "/" + id, "Solsticio table {}: item {} is not in this instance; offer skipped", table, id);
    return item;
  }

  private static void warnOnce(String key, String message, Object... args) {
    synchronized (WARNED) {
      if (WARNED.add(key)) LOGGER.info(message, args);
    }
  }

  /** The stack an item spec sells, or null when its item or an enchantment is missing. */
  static ItemStack stack(RegistryAccess access, CommerceRules.ItemSpec spec, String table) {
    Optional<Item> item = item(spec.item(), table);
    if (item.isEmpty()) return null;
    ItemStack stack = new ItemStack(item.get());
    stack.setCount(Math.min(spec.count(), stack.getMaxStackSize()));
    if (spec.components() != null) {
      var patch = DataComponentPatch.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access), spec.components());
      if (patch.error().isPresent()) {
        warnOnce(table + "/" + spec.item() + "/components", "Solsticio table {}: components of {} are invalid: {}", table,
            spec.item(), patch.error().get().message());
        return null;
      }
      stack.applyComponents(patch.getOrThrow());
    }
    if (!spec.enchantments().isEmpty()) {
      var registry = access.lookupOrThrow(Registries.ENCHANTMENT);
      for (var entry : spec.enchantments().entrySet()) {
        var holder = registry.get(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(entry.getKey())));
        if (holder.isEmpty()) {
          warnOnce(table + "/" + entry.getKey(), "Solsticio table {}: enchantment {} is not in this instance; offer skipped",
              table, entry.getKey());
          return null;
        }
        int level = entry.getValue();
        EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(holder.get(), level));
      }
    }
    if (spec.survey() != null) survey(stack, spec.survey());
    if (spec.book() != null) loreBook(stack, spec.book());
    return stack;
  }

  // ---- lore books -----------------------------------------------------------------------

  /** A written book whose pages are translation keys, so each reader sees their own language. */
  static void loreBook(ItemStack stack, CommerceRules.LoreBook book) {
    String base = "entrelumen.solsticio.lore." + book.id();
    List<Filterable<Component>> pages = IntStream.rangeClosed(1, book.pages())
        .mapToObj(page -> Filterable.<Component>passThrough(Component.translatable(base + "." + page))).toList();
    stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
        new WrittenBookContent(Filterable.passThrough("Heliodor"), "Heliodor", 0, pages, true));
    stack.set(DataComponents.CUSTOM_NAME, Component.translatable(base + ".title").withStyle(style -> style.withItalic(false)));
  }

  // ---- survey maps ----------------------------------------------------------------------

  static void survey(ItemStack stack, CommerceRules.Survey survey) {
    CompoundTag data = new CompoundTag();
    CompoundTag entry = new CompoundTag();
    entry.putString("structures", survey.structures());
    entry.putString("decoration", survey.decoration());
    entry.putString("name", survey.name());
    data.put(SURVEY, entry);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    stack.set(DataComponents.ITEM_NAME, Component.translatable("entrelumen.solsticio.survey.item",
        Component.translatable(survey.name())));
    stack.set(DataComponents.LORE, new ItemLore(List.of(
        Component.translatable("entrelumen.solsticio.survey.hint").withStyle(ChatFormatting.GRAY))));
  }

  static boolean isSurvey(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return stack.is(Items.MAP) && data != null && data.contains(SURVEY);
  }

  /** Using a blank survey map charts it instead of drawing an ordinary map. */
  static void onUseItem(PlayerInteractEvent.RightClickItem event) {
    ItemStack stack = event.getItemStack();
    if (!isSurvey(stack)) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    if (event.getEntity() instanceof ServerPlayer player) chart(player, stack, event.getHand());
  }

  /**
   * Charts a survey: the nearest unclaimed structure of its tag around the player in the
   * Overworld, as vanilla explorer maps find theirs. Returns the charted map, or empty when the
   * player is elsewhere or nothing is within reach (the blank map is kept).
   */
  static ItemStack chart(ServerPlayer player, ItemStack stack, InteractionHand hand) {
    if (!player.level().dimension().equals(Level.OVERWORLD)) {
      player.displayClientMessage(Component.translatable("entrelumen.solsticio.survey.not_here"), true);
      return ItemStack.EMPTY;
    }
    CompoundTag entry = stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound(SURVEY);
    String structures = entry.getString("structures");
    ResourceLocation tagId = ResourceLocation.tryParse(structures.startsWith("#") ? structures.substring(1) : structures);
    if (tagId == null) return ItemStack.EMPTY;
    ServerLevel level = player.serverLevel();
    TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, tagId);
    BlockPos target = level.findNearestMapStructure(tag, player.blockPosition(), SURVEY_RADIUS, true);
    if (target == null) {
      player.displayClientMessage(Component.translatable("entrelumen.solsticio.survey.none"), true);
      return ItemStack.EMPTY;
    }
    ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
    MapItem.renderBiomePreviewMap(level, map);
    ResourceLocation decorationId = ResourceLocation.tryParse(entry.getString("decoration"));
    Holder<MapDecorationType> decoration = decorationId == null ? MapDecorationTypes.RED_X
        : BuiltInRegistries.MAP_DECORATION_TYPE.getHolder(decorationId).<Holder<MapDecorationType>>map(h -> h)
            .orElse(MapDecorationTypes.RED_X);
    MapItemSavedData.addTargetDecoration(map, target, "+", decoration);
    map.set(DataComponents.ITEM_NAME, Component.translatable(entry.getString("name")));
    stack.consume(1, player);
    if (stack.isEmpty()) player.setItemInHand(hand, map);
    else if (!player.getInventory().add(map)) player.drop(map, false);
    level.playSound(null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
    return map;
  }
}
