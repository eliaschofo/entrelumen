package dev.entrelumen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * Asks Curios whether an entity wears an item without linking against Curios. Two public API methods
 * are resolved once: {@code CuriosApi.getCuriosInventory(LivingEntity)} and
 * {@code ICuriosItemHandler.isEquipped(Item)}, which only looks at the functional stacks of active slots
 * (cosmetic slots do not count). Without Curios, or if its API moved, every query answers {@code false}.
 */
public final class CuriosCompat {
  public static final String MOD_ID = "curios";

  private CuriosCompat() {}

  private record Handles(MethodHandle inventory, MethodHandle equipped) {
    static final Handles INSTANCE = resolve();

    private static Handles resolve() {
      if (!loaded()) return null;
      try {
        var lookup = MethodHandles.publicLookup();
        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
        MethodHandle inventory = lookup.findStatic(api, "getCuriosInventory",
            MethodType.methodType(Optional.class, LivingEntity.class));
        MethodHandle equipped = lookup.findVirtual(handler, "isEquipped",
            MethodType.methodType(boolean.class, Item.class));
        return new Handles(inventory, equipped);
      } catch (Throwable unavailable) {
        com.mojang.logging.LogUtils.getLogger().warn("ENTRELUMEN Curios API unavailable: {}", unavailable.toString());
        return null;
      }
    }
  }

  /** Curios is installed; says nothing about whether its API could be reached. */
  public static boolean loaded() {
    var mods = ModList.get();
    return mods != null && mods.isLoaded(MOD_ID);
  }

  /** Curios is installed and both API methods were found. */
  public static boolean available() {
    return Handles.INSTANCE != null;
  }

  /**
   * {@code ICuriosItemHandler.getEquippedCurios()}: the functional stacks of every active slot, as a
   * NeoForge item handler whose stacks are the live ones (the Ark's wireless charging fills them in
   * place). Resolved once, apart from the two handles above.
   */
  private record Equipped(MethodHandle handler) {
    static final Equipped INSTANCE = resolve();

    private static Equipped resolve() {
      if (Handles.INSTANCE == null) return null;
      try {
        Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
        return new Equipped(MethodHandles.publicLookup().findVirtual(handler, "getEquippedCurios",
            MethodType.methodType(net.neoforged.neoforge.items.IItemHandlerModifiable.class)));
      } catch (Throwable unavailable) {
        com.mojang.logging.LogUtils.getLogger().warn("ENTRELUMEN Curios equipped items unavailable: {}", unavailable.toString());
        return null;
      }
    }
  }

  /** The stacks in {@code entity}'s active Curios slots; empty without Curios or on any failure. */
  public static java.util.List<net.minecraft.world.item.ItemStack> equippedStacks(LivingEntity entity) {
    var handles = Handles.INSTANCE;
    var equipped = Equipped.INSTANCE;
    if (handles == null || equipped == null || entity == null) return java.util.List.of();
    try {
      Optional<?> inventory = (Optional<?>) handles.inventory.invoke(entity);
      if (inventory.isEmpty()) return java.util.List.of();
      var items = (net.neoforged.neoforge.items.IItemHandlerModifiable) equipped.handler.invoke(inventory.get());
      java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>(items.getSlots());
      for (int slot = 0; slot < items.getSlots(); slot++) stacks.add(items.getStackInSlot(slot));
      return stacks;
    } catch (Throwable error) {
      return java.util.List.of();
    }
  }

  /** True while {@code item} sits in an active, functional Curios slot of {@code entity}. */
  public static boolean equipped(LivingEntity entity, Item item) {
    var handles = Handles.INSTANCE;
    if (handles == null || entity == null) return false;
    try {
      Optional<?> inventory = (Optional<?>) handles.inventory.invoke(entity);
      if (inventory.isEmpty()) return false;
      return (boolean) handles.equipped.invoke(inventory.get(), item);
    } catch (Throwable error) {
      return false;
    }
  }
}
