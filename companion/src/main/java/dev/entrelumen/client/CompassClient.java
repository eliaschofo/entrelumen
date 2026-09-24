package dev.entrelumen.client;

import dev.entrelumen.CompassState;
import dev.entrelumen.HeliodorContent;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Stable item properties of {@code entrelumen:heliodor_compass}; models map them to textures.
 * {@code angle} 0..1 like the vanilla compass, {@code dimension} 0..5, {@code kind} 0..2,
 * {@code spinning} 0/1 and {@code state} 0..5 (see {@link CompassState}).
 */
@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CompassClient {
  private CompassClient() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(CompassClient::registerProperties);
  }

  static void registerProperties() {
    var compass = HeliodorContent.COMPASS.get();
    var needle = new CompassItemPropertyFunction((level, stack, entity) -> {
      CompassState state = state(stack);
      return state == null ? null : state.target().orElse(null);
    });
    ItemProperties.register(compass, id("angle"),
        (ClampedItemPropertyFunction) (stack, level, entity, seed) -> {
          CompassState state = state(stack);
          // Only a pointing or searching compass moves; every other state rests at north.
          if (state == null || state.state() == CompassState.POINTING
              || state.state() == CompassState.SEARCHING)
            return needle.unclampedCall(stack, level, entity, seed);
          return 0f;
        });
    // Unclamped: these carry integer codes above 1.
    ItemProperties.register(compass, id("dimension"),
        (ItemPropertyFunction) (stack, level, entity, seed) -> value(stack, 0));
    ItemProperties.register(compass, id("kind"),
        (ItemPropertyFunction) (stack, level, entity, seed) -> value(stack, 1));
    ItemProperties.register(compass, id("spinning"),
        (ItemPropertyFunction) (stack, level, entity, seed) -> value(stack, 2));
    ItemProperties.register(compass, id("state"),
        (ItemPropertyFunction) (stack, level, entity, seed) -> value(stack, 3));
  }

  private static CompassState state(ItemStack stack) {
    return stack.get(HeliodorContent.COMPASS_STATE.get());
  }

  private static float value(ItemStack stack, int which) {
    CompassState state = state(stack);
    if (state == null) return which == 2 ? 1f : which == 3 ? CompassState.SEARCHING : 0f;
    return switch (which) {
      case 0 -> state.dimension();
      case 1 -> state.kind();
      case 2 -> state.spinning() ? 1f : 0f;
      default -> state.state();
    };
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("entrelumen", path);
  }
}
