package dev.entrelumen;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Leaves nothing behind from the first version of the Ark (services and batches, removed on 25
 * September 2026):
 *
 * <ul>
 *   <li>Supplies deposited into an unfinished batch ({@link Campaigns.Campaign#refunds}) go to the
 *       first member of the team online: into the inventory, the rest at their feet.
 *   <li>A player who had checked in at the Habitation module's lodging gets the home spawn they had
 *       before back, if the lodging is still their spawn; the lodging record is cleared.
 *   <li>The Logistics module's kit profile is cleared. The depot's stock is dropped by
 *       {@link LogisticsStock} next to its module the first time the module is loaded.
 * </ul>
 */
public final class ArkMigration {
  static final String LODGING = "entrelumen:habitation", KIT = "entrelumen:logistics_kit";

  private ArkMigration() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
      if (event.getEntity() instanceof ServerPlayer player) {
        retireLodging(player);
        deliverRefunds(player);
      }
    });
  }

  /** The player's campaign as saved, without creating one. */
  static Campaigns.Campaign campaign(ServerPlayer player) {
    var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
    var campaigns = CampaignData.get(player.server).campaigns;
    if (team == null || !team.isPartyTeam()) return campaigns.personal.get(player.getUUID());
    return campaigns.parties.get(team.getId());
  }

  /** Pays a team's refund to this player; true when something was paid. */
  public static boolean deliverRefunds(ServerPlayer player) {
    if (!player.isAlive() || player.isSpectator()) return false;
    Campaigns.Campaign campaign = campaign(player);
    if (campaign == null || campaign.archived || campaign.refunds.isEmpty()) return false;
    MutableComponent list = Component.empty();
    boolean first = true;
    for (Map.Entry<String, Integer> entry : campaign.refunds.entrySet()) {
      var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
      if (item == Items.AIR) continue;
      int left = entry.getValue();
      ItemStack sample = new ItemStack(item);
      while (left > 0) {
        int count = Math.min(left, sample.getMaxStackSize());
        ItemStack stack = sample.copyWithCount(count);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        left -= count;
      }
      if (!first) list.append(", ");
      list.append(Component.translatable("entrelumen.delivery.reward_count", entry.getValue(), sample.getHoverName()));
      first = false;
    }
    campaign.refunds.clear();
    CampaignData.get(player.server).setDirty();
    player.getInventory().setChanged();
    player.sendSystemMessage(Component.translatable("entrelumen.ark.refund", list));
    return true;
  }

  /** Restores the home spawn a lodging replaced, then forgets the lodging and the kit profile. */
  public static boolean retireLodging(ServerPlayer player) {
    CompoundTag data = player.getPersistentData();
    if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) return false;
    CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
    persisted.remove(KIT);
    if (!persisted.contains(LODGING, Tag.TAG_COMPOUND)) return false;
    CompoundTag lodging = persisted.getCompound(LODGING);
    persisted.remove(LODGING);
    Spawn reserved = spawn(lodging.getCompound("reserved"));
    Spawn home = spawn(lodging.getCompound("home"));
    if (reserved == null || home == null) return false;
    boolean atLodging = player.getRespawnDimension().equals(reserved.dimension())
        && reserved.position() != null && reserved.position().equals(player.getRespawnPosition());
    boolean pending = player.getRespawnPosition() == null
        && (lodging.getBoolean("pending_restore") || lodging.getBoolean("pending_clone"));
    if (!atLodging && !pending) return false;
    player.setRespawnPosition(home.dimension(), home.position(), home.angle(), home.forced(), false);
    player.sendSystemMessage(Component.translatable("entrelumen.ark.lodging_retired"));
    return true;
  }

  private record Spawn(ResourceKey<Level> dimension, BlockPos position, float angle, boolean forced) {}

  private static Spawn spawn(CompoundTag tag) {
    ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
    if (dimension == null) return null;
    BlockPos position = null;
    if (tag.getBoolean("has_position")) {
      int[] xyz = tag.getIntArray("position");
      if (xyz.length != 3) return null;
      position = new BlockPos(xyz[0], xyz[1], xyz[2]);
    }
    return new Spawn(ResourceKey.create(Registries.DIMENSION, dimension), position, tag.getFloat("angle"),
        tag.getBoolean("forced"));
  }

  /** For tests: the refund lines of a campaign, as item ID and count. */
  static List<String> refundLines(Campaigns.Campaign campaign) {
    List<String> lines = new ArrayList<>();
    campaign.refunds.forEach((item, count) -> lines.add(item + "x" + count));
    return lines;
  }
}
