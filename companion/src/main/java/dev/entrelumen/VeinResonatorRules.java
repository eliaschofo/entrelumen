package dev.entrelumen;

/** Pure rules of the vein resonators, without Minecraft types, so JUnit can check them. */
public final class VeinResonatorRules {
  /** Four tiers since Elias's nerf of 25 September 2026 (six before). */
  public static final int TIERS = 4;
  /** Blocks per Ultimine use, by tier; index 0 is no resonator. Each tier doubles the last. */
  private static final int[] REACH = {0, 8, 16, 32, 64};
  /** ID of the resonators' modifier on FTB Ultimine's per-player block limit. */
  public static final String MODIFIER_ID = "entrelumen:vein_resonator";
  /** The attribute FTB Ultimine 2101.1.15 adds to its configured {@code max_blocks}. */
  public static final String ULTIMINE_ATTRIBUTE_ID = "ftbultimine:max_blocks_modifier";

  private VeinResonatorRules() {}

  public static String id(int tier) {
    if (tier < 1 || tier > TIERS) throw new IllegalArgumentException("No vein resonator tier " + tier);
    return "vein_resonator_" + tier;
  }

  /** Blocks one Ultimine use may break with this tier worn: 8, 16, 32 or 64; 0 for tier 0 (no resonator). */
  public static int reach(int tier) {
    if (tier < 0 || tier > TIERS) throw new IllegalArgumentException("No vein resonator tier " + tier);
    return REACH[tier];
  }

  /**
   * The modifier amount that makes Ultimine's limit, {@code max(0, max_blocks + modifier)}, equal the
   * tier's reach whatever {@code max_blocks} a server configured: zero without a resonator.
   */
  public static int modifierAmount(int tier, int configuredMaxBlocks) {
    return reach(tier) - configuredMaxBlocks;
  }
}
