package dev.entrelumen;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.task.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** A read-only projection: no click or possession can grant a campaign milestone. */
public final class CampaignTask extends Task {
  private static TaskType TYPE;
  private String milestone = "";
  private static ServerQuestFile cachedFile;
  private static java.util.List<CampaignTask> cachedTasks = java.util.List.of();

  public CampaignTask(long id, Quest quest) {
    super(id, quest);
  }

  public static void register() {
    TYPE =
        TaskTypes.register(
            ResourceLocation.fromNamespaceAndPath("entrelumen", "campaign"),
            CampaignTask::new,
            () -> ItemIcon.getItemIcon(Items.BOOK));
  }

  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 != 0) return;
    ServerQuestFile.getInstance()
        .ifPresent(
            file -> {
              if (cachedFile != file || event.getServer().getTickCount() % 100 == 0) {
                cachedFile = file;
                cachedTasks =
                    file.getAllTasks().stream()
                        .filter(CampaignTask.class::isInstance)
                        .map(CampaignTask.class::cast)
                        .toList();
              }
              java.util.Set<java.util.UUID> visited = new java.util.HashSet<>();
              for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                TeamData data = file.getOrCreateTeamData(player);
                if (visited.add(data.getTeamId()))
                  for (CampaignTask campaign : cachedTasks)
                    campaign.submitTask(data, player, ItemStack.EMPTY);
              }
            });
  }

  @Override
  public TaskType getType() {
    return TYPE;
  }

  @Override
  public void onButtonClicked(dev.ftb.mods.ftblibrary.ui.Button button, boolean canSubmit) {
    // Opening a read-only snapshot is also useful for completed or blocked milestones.
    button.playClickSound();
    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
        AtlasNetwork.OpenRequest.INSTANCE);
  }

  @Override
  public net.minecraft.network.chat.MutableComponent getButtonText() {
    return net.minecraft.network.chat.Component.translatable("entrelumen.atlas.open");
  }

  @Override
  public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList tooltip, TeamData data) {
    super.addMouseOverText(tooltip, data);
    tooltip.add(net.minecraft.network.chat.Component.translatable("entrelumen.atlas.open_hint"));
  }

  @Override
  public int autoSubmitOnPlayerTick() {
    return 0;
  }

  @Override
  public boolean checkOnLogin() {
    return true;
  }

  @Override
  public void submitTask(TeamData data, ServerPlayer player, ItemStack crafted) {
    if (Entrelumen.current(player).completed.contains(milestone)) {
      if (data.getProgress(this) != 1) data.setProgress(this, 1);
    } else if (data.getProgress(this) != 0
        || data.getCompletedTime(id).isPresent()
        || data.getCompletedTime(getQuest().id).isPresent()) {
      data.setProgress(this, 0);
      data.setCompleted(getQuest().id, null);
      data.setCompleted(getQuestChapter().id, null);
      data.setCompleted(getQuestFile().id, null);
    }
  }

  @Override
  public void writeData(CompoundTag tag, HolderLookup.Provider lookup) {
    super.writeData(tag, lookup);
    tag.putString("milestone", milestone);
  }

  @Override
  public void readData(CompoundTag tag, HolderLookup.Provider lookup) {
    super.readData(tag, lookup);
    milestone = tag.getString("milestone");
  }

  @Override
  public void writeNetData(RegistryFriendlyByteBuf buf) {
    super.writeNetData(buf);
    buf.writeUtf(milestone);
  }

  @Override
  public void readNetData(RegistryFriendlyByteBuf buf) {
    super.readNetData(buf);
    milestone = buf.readUtf();
  }
}
