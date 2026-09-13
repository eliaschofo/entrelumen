package dev.entrelumen;

import java.util.List;
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

  public static void register(DeferredRegister.Items items) {
    IDS.forEach(items::registerSimpleItem);
  }
}
