package dev.entrelumen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The gate's price (Elias, 27/9): one Nether star per attempt, or a number of sour light shards in its
 * place. {@code data/entrelumen/enves/config.json} lists the alternatives under {@code offering}, in
 * the order the gate shows them; the count of shards is calibrated so that a full descent drops more
 * than the door asks for (docs/design/dungeon-enves.md, «La ofrenda»). The payer picks which one to
 * pay; the server checks it again.
 */
public final class EnvesOffering {
  private EnvesOffering() {}

  /** One way to pay: {@code count} of {@code item}. */
  public record Offer(String item, int count) {
    public Offer {
      if (ResourceLocation.tryParse(item) == null) throw new IllegalArgumentException("Bad offering item " + item);
      if (count < 1 || count > 64) throw new IllegalArgumentException("Offering count must be 1..64: " + count);
    }
  }

  public static final int MAX_OFFERS = 4;

  /**
   * Parses {@code offering}: a list of {@code {item, count}}, or a single one (the engine's first
   * schema). Every item must exist, at most {@link #MAX_OFFERS}, none twice.
   */
  public static List<Offer> parse(JsonElement element, Predicate<String> itemExists) {
    List<Offer> out = new ArrayList<>();
    if (element.isJsonArray()) {
      for (JsonElement e : element.getAsJsonArray()) out.add(offer(e.getAsJsonObject(), itemExists));
    } else {
      out.add(offer(element.getAsJsonObject(), itemExists));
    }
    if (out.isEmpty() || out.size() > MAX_OFFERS) throw new IllegalArgumentException("offering needs 1.." + MAX_OFFERS + " alternatives");
    Set<String> items = new HashSet<>();
    for (Offer offer : out) if (!items.add(offer.item())) throw new IllegalArgumentException("offering lists " + offer.item() + " twice");
    return List.copyOf(out);
  }

  private static Offer offer(JsonObject o, Predicate<String> itemExists) {
    String item = o.get("item").getAsString();
    int count = o.has("count") ? o.get("count").getAsInt() : 1;
    Offer offer = new Offer(item, count);
    if (!itemExists.test(item)) throw new IllegalArgumentException("Unknown offering item " + item);
    return offer;
  }

  /** Which offer a payer's choice means: the one asked for if affordable, else the first they can pay, else -1. */
  public static int choose(List<Boolean> affordable, int wanted) {
    if (wanted >= 0 && wanted < affordable.size() && affordable.get(wanted)) return wanted;
    if (wanted >= 0 && wanted < affordable.size()) return -1;
    for (int i = 0; i < affordable.size(); i++) if (affordable.get(i)) return i;
    return -1;
  }

  // ---- The player's side --------------------------------------------------------------------

  static Item item(Offer offer) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse(offer.item()));
  }

  static int count(ServerPlayer player, Offer offer) {
    Item item = item(offer);
    int count = 0;
    for (ItemStack stack : Entrelumen.deliveryStacks(player)) if (stack.is(item)) count += stack.getCount();
    return count;
  }

  /** For each configured offer, whether the player carries it. */
  public static List<Boolean> affordable(ServerPlayer player) {
    List<Boolean> out = new ArrayList<>();
    for (Offer offer : EnvesConfig.settings().offerings()) out.add(count(player, offer) >= offer.count());
    return out;
  }

  /** Takes offer {@code index} from the player; false (and nothing taken) if they lack it. */
  static boolean take(ServerPlayer player, int index) {
    var offers = EnvesConfig.settings().offerings();
    if (index < 0 || index >= offers.size()) return false;
    Offer offer = offers.get(index);
    if (count(player, offer) < offer.count()) return false;
    Item item = item(offer);
    int need = offer.count();
    for (ItemStack stack : Entrelumen.deliveryStacks(player)) {
      if (need <= 0) break;
      if (!stack.is(item)) continue;
      int take = Math.min(need, stack.getCount());
      stack.shrink(take);
      need -= take;
    }
    player.getInventory().setChanged();
    player.containerMenu.broadcastChanges();
    return true;
  }
}
