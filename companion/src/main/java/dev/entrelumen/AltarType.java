package dev.entrelumen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * One kind of Ark altar: its ID, the item tag it runs on and how long one unit keeps it active.
 * Every altar covers a square centred on itself. New altars (ward, growth, slow motion…) add a
 * constant here and extend {@link AltarBlock} and {@link AltarBlockEntity}.
 */
public record AltarType(String id, TagKey<Item> fuel, int ticksPerFuel) {
  /** Fertilizer: each unit also adds work charge, see {@link AltarRules#CHARGE_PER_FERTILIZER}. */
  public static final AltarType RENEWAL = new AltarType("renewal_altar", tag("renewal_fertilizers"), 200);
  /** Coal, charcoal or redstone: one minute of levelling per unit. */
  public static final AltarType TERRAFORM = new AltarType("terraform_altar", tag("terraform_fuels"), 1200);

  private static TagKey<Item> tag(String path) {
    return ItemTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", path));
  }
}
