package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Portable, freely tradable components shared by the pack's static recipes. */
public final class IntegrationItems {
  public static final List<String> IDS =
      List.of(
          "calibration_frame",
          "energy_coupler",
          "living_matrix",
          "ration_bundle",
          "routing_matrix",
          "propagation_core",
          "power_regulator",
          "inventory_sensor",
          "handling_core",
          "spectral_lens",
          "horizon_chart",
          "ecosystem_capsule",
          "containment_seal",
          "ark_bus",
          "renewal_engine",
          "habitation_contract");

  private IntegrationItems() {}

  /** Since 24 September 2026 the frame has no crafting recipe; its tooltip says how to copy it. */
  public static final String FRAME = "calibration_frame";

  public static void register(DeferredRegister.Items items) {
    for (String id : IDS) {
      if (id.equals(FRAME)) items.register(id, () -> new Frame(new Item.Properties()));
      else items.registerSimpleItem(id);
    }
  }

  /** The calibration frame: a plain component with one line on how the infuser copies it. */
  public static final class Frame extends Item {
    public Frame(Properties properties) {
      super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
      tooltip.add(Component.translatable("entrelumen.calibration_frame.tooltip").withStyle(ChatFormatting.GRAY));
    }
  }
}
