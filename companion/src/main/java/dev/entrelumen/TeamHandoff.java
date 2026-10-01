package dev.entrelumen;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * What a party takes over from its founder, and gives back when it is disbanded (the campaign itself
 * is copied and archived by {@link Campaigns}). Founding a party moves the founder's Ark to it, copies
 * the Solsticio shops the founder knew and the garden site, re-owns the founder's Solsticio plot and
 * counts the founder's forged Light Key for the party. Disbanding hands the Ark and the plot back to the
 * owner when the owner has none of their own; otherwise the Ark record is dropped (its blocks stay and
 * may found a new Ark) and the plot is freed (its blocks stay). Known shops and forged keys stay with
 * the archived party. Envés attempts are re-keyed by the Envés code on the same events.
 */
public final class TeamHandoff {
  private TeamHandoff() {}

  /** FTB Teams created a party: called after its campaign was copied from the founder. */
  public static void onCreated(MinecraftServer server, UUID partyId, UUID founder) {
    ArkData ark = ArkData.get(server);
    if (arkToParty(ark, partyId, founder)) ark.setDirty();
    SolsticioData city = SolsticioData.get(server);
    if (solsticioToParty(city, partyId, founder)) {
      city.setDirty();
      StructureProtection.invalidate(server);
    }
    NatureRestorationData.get(server).copyIfAbsent(founder, partyId);
  }

  /** FTB Teams deleted a party: called after its campaign was archived. */
  public static void onDeleted(MinecraftServer server, UUID partyId, UUID owner) {
    ArkData ark = ArkData.get(server);
    if (arkToOwner(ark, partyId, owner)) ark.setDirty();
    SolsticioCity.releaseCampaign(server, partyId, owner);
  }

  /** Moves the founder's Ark to the party (unless it has one) and copies the shops the founder knew. */
  static boolean arkToParty(ArkData data, UUID partyId, UUID founder) {
    boolean changed = false;
    ArkData.Ark ark = data.arks.remove(founder);
    if (ark != null) {
      changed = true;
      if (!data.arks.containsKey(partyId)) data.arks.put(partyId, ark);
    }
    Set<String> known = data.knownShops.get(founder);
    if (known != null && !known.isEmpty())
      changed |= data.knownShops.computeIfAbsent(partyId, id -> new TreeSet<>()).addAll(known);
    return changed;
  }

  /** Counts the founder's forged key for the party and re-owns the founder's plot. */
  static boolean solsticioToParty(SolsticioData data, UUID partyId, UUID founder) {
    boolean changed = data.forgedKeys.contains(founder) && data.forgedKeys.add(partyId);
    for (SolsticioData.Plot plot : data.plots)
      if (founder.equals(plot.owner)) {
        plot.owner = partyId;
        changed = true;
      }
    return changed;
  }

  /** Hands the party's Ark to the owner when the owner has no personal Ark; drops it otherwise. */
  static boolean arkToOwner(ArkData data, UUID partyId, UUID owner) {
    ArkData.Ark ark = data.arks.remove(partyId);
    if (ark == null) return false;
    if (owner != null && !data.arks.containsKey(owner)) data.arks.put(owner, ark);
    return true;
  }
}
