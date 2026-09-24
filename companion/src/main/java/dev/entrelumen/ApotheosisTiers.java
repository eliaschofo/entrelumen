package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Projects the team campaign onto Apotheosis World Tiers: the story sets the difficulty.
 *
 * <p>The pack overrides {@code apotheosis:progression/*} so Frontier to Pinnacle carry only an
 * impossible criterion (Haven keeps a tick criterion). {@link #sync} first grants the remaining
 * criteria of every tier the player's current campaign has reached, through the vanilla
 * advancement API, and never revokes them. It then records the highest tier the story ever gave
 * the player (the {@code entrelumen:story_tier} attachment, kept through death and relogs) and
 * moves the active World Tier to it through Apotheosis's own {@code WorldTier.setTier}. The tier
 * only rises: leaving a team or a lower campaign never lowers it. Players never pick a tier; the
 * pack also disables Apotheosis's manual selection ({@code Enable Manual World Tier Changes =
 * false}). Apotheosis is reached by reflection only: without it, or if its API moved, the tier
 * step is skipped and nothing fails.
 */
public final class ApotheosisTiers {
  private static final Logger LOGGER = LogUtils.getLogger();

  public enum Tier {
    HAVEN("haven"),
    FRONTIER("frontier"),
    ASCENT("ascent"),
    SUMMIT("summit"),
    PINNACLE("pinnacle");

    public final ResourceLocation advancement;
    /** Apotheosis's own name key for the tier. */
    public final String nameKey;

    Tier(String name) {
      advancement = ResourceLocation.fromNamespaceAndPath("apotheosis", "progression/" + name);
      nameKey = "text.apotheosis.world_tier." + name;
    }
  }

  /** Campaign act reached after completing Act II, III and V respectively. */
  public static final int FRONTIER_ACT = 3, ASCENT_ACT = 4, SUMMIT_ACT = 6;

  static final int SYNC_INTERVAL = 20;

  /** Reads and writes a player's active World Tier. */
  interface WorldTierAccess {
    Tier get(ServerPlayer player);

    void set(ServerPlayer player, Tier tier);
  }

  private static WorldTierAccess access;
  private static boolean resolved;

  private ApotheosisTiers() {}

  /** Pure rule. Haven is always open; later tiers follow the campaign and the Ark activation. */
  public static List<Tier> reached(Campaigns.Campaign campaign) {
    List<Tier> tiers = new ArrayList<>(List.of(Tier.HAVEN));
    if (campaign == null || campaign.archived) return List.copyOf(tiers);
    if (campaign.act >= FRONTIER_ACT) tiers.add(Tier.FRONTIER);
    if (campaign.act >= ASCENT_ACT) tiers.add(Tier.ASCENT);
    if (campaign.act >= SUMMIT_ACT) tiers.add(Tier.SUMMIT);
    if (campaign.completed.contains(CampaignMilestones.LAST_HORIZON)) tiers.add(Tier.PINNACLE);
    return List.copyOf(tiers);
  }

  /**
   * Pure rule: the tier a campaign reaches for this player, the highest reached tier whose unlock
   * the player holds (a tier removed by a datapack is skipped). {@code null} outside any active
   * campaign or when no tier is unlocked; {@link #story} then keeps the recorded tier.
   */
  public static Tier target(Campaigns.Campaign campaign, Predicate<Tier> unlocked) {
    if (campaign == null || campaign.archived) return null;
    Tier target = null;
    for (Tier tier : reached(campaign)) if (unlocked.test(tier)) target = tier;
    return target;
  }

  /**
   * Pure rule: the tier to hold, the higher of the recorded story tier and the current
   * campaign's tier. It never goes down; {@code null} only when neither exists.
   */
  public static Tier story(Tier recorded, Tier campaign) {
    if (recorded == null) return campaign;
    if (campaign == null) return recorded;
    return campaign.ordinal() > recorded.ordinal() ? campaign : recorded;
  }

  static Tier recorded(ServerPlayer player) {
    String name = player.getData(ApotheosisContent.STORY_TIER);
    for (Tier tier : Tier.values()) if (tier.name().equals(name)) return tier;
    return null;
  }

  /**
   * Read-only lookup of the campaign the player plays in: never creates a campaign or marks
   * SavedData dirty. A team without a stored campaign yet reads as the one it would get (a
   * party copies its owner's personal campaign; a personal one starts at Act I).
   */
  static Campaigns.Campaign campaignOf(ServerPlayer player) {
    var api = FTBTeamsAPI.api();
    if (!api.isManagerLoaded()) return null;
    var team = api.getManager().getTeamForPlayer(player);
    if (team.isEmpty()) return null;
    var campaigns = CampaignData.get(player.server).campaigns;
    Campaigns.Campaign campaign = team.get().isPartyTeam()
        ? campaigns.parties.getOrDefault(team.get().getId(), campaigns.personal.get(team.get().getOwner()))
        : campaigns.personal.get(player.getUUID());
    return campaign != null ? campaign : new Campaigns.Campaign();
  }

  /**
   * Grants every missing criterion of each reached tier, records the story tier and moves the
   * active World Tier to it when it differs. Returns the number of criteria granted.
   */
  public static int sync(ServerPlayer player) {
    var campaign = campaignOf(player);
    int granted = grant(player, reached(campaign));
    enforce(player, campaign);
    return granted;
  }

  static boolean unlocked(ServerPlayer player, Tier tier) {
    var holder = player.server.getAdvancements().get(tier.advancement);
    return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
  }

  /** Records and applies the story tier; returns true when the active World Tier changed. */
  static boolean enforce(ServerPlayer player, Campaigns.Campaign campaign) {
    Tier recorded = recorded(player);
    Tier target = story(recorded, target(campaign, tier -> unlocked(player, tier)));
    if (target == null) return false;
    if (target != recorded) player.setData(ApotheosisContent.STORY_TIER, target.name());
    var tiers = access();
    if (tiers == null) return false;
    try {
      if (tiers.get(player) == target) return false;
      tiers.set(player, target);
      if (tiers.get(player) != target) return false;
    } catch (RuntimeException e) {
      // One broken call disables the tier step instead of failing every server tick.
      LOGGER.error("Apotheosis World Tier call failed; story tiers disabled until restart", e);
      synchronized (ApotheosisTiers.class) {
        if (access == tiers) access = null;
      }
      return false;
    }
    player.sendSystemMessage(Component.translatable("entrelumen.apotheosis.tier.set",
        Component.translatable(target.nameKey)));
    return true;
  }

  static int grant(ServerPlayer player, List<Tier> tiers) {
    int granted = 0;
    var advancements = player.server.getAdvancements();
    for (Tier tier : tiers) {
      var holder = advancements.get(tier.advancement);
      if (holder == null) continue;
      var progress = player.getAdvancements().getOrStartProgress(holder);
      if (progress.isDone()) continue;
      List<String> remaining = new ArrayList<>();
      progress.getRemainingCriteria().forEach(remaining::add);
      for (String criterion : remaining)
        if (player.getAdvancements().award(holder, criterion)) granted++;
    }
    return granted;
  }

  public static void tick(ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % SYNC_INTERVAL != 7) return;
    for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) sync(player);
  }

  /** Apotheosis World Tier access, resolved once; {@code null} without Apotheosis. */
  static synchronized WorldTierAccess access() {
    if (!resolved) {
      resolved = true;
      access = ModList.get().isLoaded("apotheosis") ? Reflective.resolve() : null;
    }
    return access;
  }

  /** Test hook: replaces the World Tier access and returns the previous one. */
  static synchronized WorldTierAccess swapAccess(WorldTierAccess replacement) {
    var previous = access();
    access = replacement;
    return previous;
  }

  /** {@code dev.shadowsoffire.apotheosis.tiers.WorldTier#getTier/setTier}, public in 8.7.0. */
  private record Reflective(Method getTier, Method setTier, Map<Tier, Object> constants)
      implements WorldTierAccess {
    static WorldTierAccess resolve() {
      try {
        Class<?> type = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
        Map<Tier, Object> constants = new EnumMap<>(Tier.class);
        for (Object constant : type.getEnumConstants())
          constants.put(Tier.valueOf(((Enum<?>) constant).name()), constant);
        if (constants.size() != Tier.values().length)
          throw new IllegalStateException("World Tier constants differ: " + constants.keySet());
        return new Reflective(type.getMethod("getTier", Player.class),
            type.getMethod("setTier", Player.class, type), Map.copyOf(constants));
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
        LOGGER.error("Apotheosis World Tier API not found; story tiers will not be applied", e);
        return null;
      }
    }

    @Override
    public Tier get(ServerPlayer player) {
      try {
        Object tier = getTier.invoke(null, player);
        return tier == null ? null : Tier.valueOf(((Enum<?>) tier).name());
      } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
        throw new IllegalStateException("Apotheosis WorldTier.getTier failed", e);
      }
    }

    @Override
    public void set(ServerPlayer player, Tier tier) {
      try {
        setTier.invoke(null, player, constants.get(tier));
      } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
        throw new IllegalStateException("Apotheosis WorldTier.setTier failed", e);
      }
    }
  }
}
