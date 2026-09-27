package dev.entrelumen.client;

import dev.entrelumen.Enves;
import dev.entrelumen.EnvesNetwork;
import net.minecraft.client.Minecraft;

/** What the client knows of its Envés attempt: the HUD numbers and the fog map of its floor. */
public final class EnvesClientState {
  private static EnvesNetwork.Hud hud;
  private static EnvesNetwork.MapView map;

  private EnvesClientState() {}

  static void receive(EnvesNetwork.Hud update) {
    if (!update.inside()) {
      clear();
      return;
    }
    hud = update;
  }

  static void receive(EnvesNetwork.MapView update) {
    map = update;
  }

  static void clear() {
    hud = null;
    map = null;
  }

  public static EnvesNetwork.Hud hud() {
    return hud;
  }

  public static EnvesNetwork.MapView map() {
    return map;
  }

  /** Inside an attempt, in the Envés dimension, with a map to draw. */
  public static boolean inside() {
    var minecraft = Minecraft.getInstance();
    return hud != null && minecraft.level != null && minecraft.level.dimension().equals(Enves.LEVEL);
  }
}
