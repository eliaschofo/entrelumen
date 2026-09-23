package dev.entrelumen;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** A bounded, read-only view of the current team's Ark and nearby blocks. */
public final class EngineeringDiagnostics {
  public record Material(String item, int deposited, int required) {
    public int remaining() {
      return Math.max(0, required - deposited);
    }
  }

  public record CampaignView(int act, boolean archived, List<String> missingPrerequisites,
      List<String> missingProjects,
      boolean missingWorldNetwork, boolean missingEndJourney, int step, int totalSteps,
      String phase, String module, List<Material> materials, boolean ending) {
    public CampaignView {
      missingPrerequisites = List.copyOf(missingPrerequisites);
      missingProjects = List.copyOf(missingProjects);
      materials = List.copyOf(materials);
    }

    public boolean commissioned() {
      return step >= totalSteps;
    }
  }

  public enum ControllerState { FOUND, ABSENT, UNLOADED, AMBIGUOUS }

  public record PhysicalView(ControllerState state, BlockPos controller,
      Set<String> missingModules, boolean moduleAreaUnloaded) {
    public PhysicalView {
      missingModules = Set.copyOf(missingModules);
    }
  }

  private EngineeringDiagnostics() {}

  /** Uses existing SavedData without creating a party or personal campaign during inspection. */
  static Campaigns.Campaign currentReadOnly(ServerPlayer player) {
    var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
    var campaigns = CampaignData.get(player.server).campaigns;
    if (!team.isPartyTeam())
      return campaigns.personal.getOrDefault(player.getUUID(), new Campaigns.Campaign());
    var stored = campaigns.parties.get(team.getId());
    if (stored != null) return stored;
    return campaigns.personal.getOrDefault(team.getOwner(), new Campaigns.Campaign());
  }

  static CampaignView campaignView(Campaigns.Campaign campaign) {
    return campaignView(campaign, Projects.all());
  }

  static CampaignView campaignView(Campaigns.Campaign campaign,
      java.util.Map<String, Projects.Project> projects) {
    Set<String> prerequisites = new TreeSet<>();
    var network = projects.get("world_network");
    if (network != null && !campaign.completed.contains("world_network"))
      prerequisites.addAll(network.prerequisites());
    for (String id : CampaignMilestones.MODULE_IDS) {
      var project = projects.get(id);
      if (project != null && !campaign.completed.contains(id))
        prerequisites.addAll(project.prerequisites());
    }
    prerequisites.removeAll(campaign.completed);
    prerequisites.remove("world_network");
    prerequisites.remove("end_arrival");
    List<String> missing = CampaignMilestones.MODULE_IDS.stream()
        .filter(id -> !campaign.completed.contains(id)).sorted().toList();
    int total = ArkCommissioning.STEPS.size();
    int step = Math.clamp(campaign.arkPhase, 0, total);
    if (step == total)
      return new CampaignView(campaign.act, campaign.archived, List.copyOf(prerequisites), missing,
          !campaign.completed.contains("world_network"), !campaign.completed.contains("end_arrival"),
          step, total, "", "", List.of(),
          campaign.completed.contains(CampaignMilestones.LAST_HORIZON));
    var batch = ArkCommissioning.STEPS.get(step);
    List<Material> materials = batch.requirements().entrySet().stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .map(entry -> new Material(entry.getKey(),
            Math.clamp(campaign.arkDeposits.getOrDefault(entry.getKey(), 0), 0, entry.getValue()),
            entry.getValue())).toList();
    return new CampaignView(campaign.act, campaign.archived, List.copyOf(prerequisites), missing,
        !campaign.completed.contains("world_network"), !campaign.completed.contains("end_arrival"),
        step, total, batch.phase(), batch.module(), materials,
        campaign.completed.contains(CampaignMilestones.LAST_HORIZON));
  }

  /** Controller candidates are exactly those whose existing Ark volume can contain this module. */
  static PhysicalView physicalView(ServerLevel level, BlockPos module) {
    return scan(module,
        pos -> level.isOutsideBuildHeight(pos) || level.hasChunkAt(pos),
        pos -> !level.isOutsideBuildHeight(pos)
            && level.getBlockState(pos).getBlock() instanceof ArkControllerBlock,
        pos -> {
          if (level.isOutsideBuildHeight(pos)) return "";
          var key = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
          return key.getNamespace().equals("entrelumen") ? key.getPath() : "";
        });
  }

  /** Predicates are called only for inspectable positions, so callers never load missing chunks. */
  static PhysicalView scan(BlockPos module, Predicate<BlockPos> inspectable,
      Predicate<BlockPos> isController, Function<BlockPos, String> moduleAt) {
    List<BlockPos> controllers = new ArrayList<>();
    boolean unloaded = false;
    for (BlockPos candidate : BlockPos.betweenClosed(module.offset(-3, -2, -3),
        module.offset(3, 1, 3))) {
      if (!inspectable.test(candidate)) {
        unloaded = true;
        continue;
      }
      if (isController.test(candidate))
        controllers.add(candidate.immutable());
    }
    if (controllers.size() > 1)
      return new PhysicalView(ControllerState.AMBIGUOUS, null, Set.of(), unloaded);
    if (unloaded)
      return new PhysicalView(ControllerState.UNLOADED, null, Set.of(), true);
    if (controllers.isEmpty())
      return new PhysicalView(ControllerState.ABSENT, null, Set.of(), false);

    BlockPos controller = controllers.getFirst();
    Set<String> missing = new TreeSet<>(CampaignMilestones.MODULE_IDS);
    boolean moduleAreaUnloaded = false;
    for (BlockPos pos : BlockPos.betweenClosed(controller.offset(-3, -1, -3),
        controller.offset(3, 2, 3))) {
      if (!inspectable.test(pos)) {
        moduleAreaUnloaded = true;
        continue;
      }
      missing.remove(moduleAt.apply(pos));
    }
    return new PhysicalView(ControllerState.FOUND, controller, missing, moduleAreaUnloaded);
  }

  public static boolean inspect(ServerPlayer player, BlockPos module) {
    if (player.isSpectator() || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !(player.serverLevel().getBlockState(module).getBlock() instanceof EngineeringModuleBlock))
      return false;
    var campaign = campaignView(currentReadOnly(player));
    var physical = physicalView(player.serverLevel(), module);
    lines(campaign, physical).forEach(player::sendSystemMessage);
    return true;
  }

  static List<Component> lines(CampaignView campaign, PhysicalView physical) {
    List<Component> lines = new ArrayList<>();
    lines.add(Component.translatable("entrelumen.engineering.title", campaign.act()));
    if (campaign.archived()) lines.add(Component.translatable("entrelumen.engineering.archived"));
    if (campaign.missingWorldNetwork())
      lines.add(Component.translatable("entrelumen.engineering.world_network"));
    if (campaign.missingEndJourney())
      lines.add(Component.translatable("entrelumen.engineering.end_journey"));
    for (String id : campaign.missingPrerequisites())
      lines.add(Component.translatable("entrelumen.engineering.prerequisite",
          Component.translatable("entrelumen.project." + id)));
    for (String id : campaign.missingProjects())
      lines.add(Component.translatable("entrelumen.engineering.project",
          Component.translatable("entrelumen.project." + id)));
    if (campaign.missingProjects().isEmpty())
      lines.add(Component.translatable("entrelumen.engineering.projects_ready"));
    if (campaign.ending()) {
      lines.add(Component.translatable("entrelumen.engineering.ending"));
    } else if (campaign.commissioned()) {
      lines.add(Component.translatable("entrelumen.engineering.commissioned"));
    } else {
      lines.add(Component.translatable("entrelumen.engineering.batch",
          campaign.step() + 1, campaign.totalSteps(),
          Component.translatable("entrelumen.ark.stage." + campaign.phase()),
          Component.translatable("entrelumen.project." + campaign.module())));
      for (Material material : campaign.materials()) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(material.item()));
        lines.add(Component.translatable("entrelumen.ark.material", new ItemStack(item).getHoverName(),
            material.deposited(), material.required(), material.remaining()));
      }
    }
    switch (physical.state()) {
      case ABSENT -> lines.add(Component.translatable("entrelumen.engineering.no_controller"));
      case UNLOADED -> lines.add(Component.translatable("entrelumen.engineering.controller_unloaded"));
      case AMBIGUOUS -> lines.add(Component.translatable("entrelumen.engineering.ambiguous"));
      case FOUND -> {
        lines.add(Component.translatable("entrelumen.engineering.controller_at",
            physical.controller().getX(), physical.controller().getY(), physical.controller().getZ()));
        if (physical.moduleAreaUnloaded())
          lines.add(Component.translatable("entrelumen.engineering.modules_unloaded"));
        else if (physical.missingModules().isEmpty())
          lines.add(Component.translatable("entrelumen.engineering.structure_ready"));
        else for (String id : physical.missingModules().stream().sorted().toList())
          lines.add(Component.translatable("entrelumen.ark.missing_module",
              Component.translatable("entrelumen.project." + id)));
      }
    }
    return List.copyOf(lines);
  }
}
