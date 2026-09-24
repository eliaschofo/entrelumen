package dev.entrelumen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * One kind of Ark altar: its ID, the item tag it runs on and how long one unit keeps it active.
 * Every altar covers a square centred on itself. New altars add a constant here and extend
 * {@link AltarBlock} and {@link AltarBlockEntity}.
 */
public record AltarType(String id, TagKey<Item> fuel, int ticksPerFuel) {
  /** Fertilizer: each unit also adds work charge, see {@link AltarRules#CHARGE_PER_FERTILIZER}. */
  public static final AltarType RENEWAL = new AltarType("renewal_altar", tag("renewal_fertilizers"), 200);
  /** Coal, charcoal or redstone: one minute of levelling per unit. */
  public static final AltarType TERRAFORM = new AltarType("terraform_altar", tag("terraform_fuels"), 1200);
  /** Candles, a vigil light: ten minutes of warding per candle. */
  public static final AltarType PEACE = new AltarType("peace_altar", tag("peace_altar_fuels"), 12_000);
  /** Bone blocks, nine bone meal pressed together: three minutes of growth per block. */
  public static final AltarType GROWTH = new AltarType("growth_altar", tag("growth_altar_fuels"), 3_600);
  /** Amethyst shards, the crystal that keeps time: two minutes of slow motion per shard. */
  public static final AltarType TIME = new AltarType("time_altar", tag("time_altar_fuels"), 2_400);
  /** Bottles o' Enchanting, Mending's own food: one minute of repair per bottle, paid only while repairing. */
  public static final AltarType REPOSE = new AltarType("repose_altar", tag("repose_altar_fuels"), 1_200);

  private static TagKey<Item> tag(String path) {
    return ItemTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", path));
  }
}
