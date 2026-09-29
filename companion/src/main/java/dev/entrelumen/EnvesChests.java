package dev.entrelumen;

/**
 * The chests hook: room and vault chests are the engine's (a Lootr chest of
 * {@code entrelumen:enves/<kind>}, room chests by chance); the boss chest waits for the boss to fall
 * ({@link EnvesBoss#bossDefeated}), so nobody loots it past the Sour Light.
 */
public final class EnvesChests {
  private EnvesChests() {}

  static void place(EnvesHooks.Floor floor, EnvesHooks.WorldMarker chest) {
    if (chest.marker().head().equals("boss") && !floor.attempt().bossDefeated) return;
    EnvesPlacer.defaultChest(floor, chest);
  }
}
