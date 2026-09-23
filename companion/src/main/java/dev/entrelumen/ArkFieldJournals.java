package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Read-only, team-specific field notes for four Ark disciplines. */
public final class ArkFieldJournals {
  public enum Kind {
    ARCANE("arcane_module", 1,
        List.of("spectral_archive", "sealed_memory", "atlas_voices"), List.of()),
    NATURE("nature_module", 2,
        List.of("nursery_protocol", "pollinator_treaty", "renewal_engine"), List.of()),
    EXPLORATION("exploration_module", 5,
        List.of("horizon_survey"),
        List.of("aether_arrival", "twilight_arrival", "bumblezone_arrival", "end_arrival")),
    HABITATION("habitation_module", 4,
        List.of("travellers_table", "travelling_pantry", "settlement_supply"), List.of());

    private final String module;
    private final int step;
    private final List<String> projects;
    private final List<String> journeys;

    Kind(String module, int step, List<String> projects, List<String> journeys) {
      this.module = module;
      this.step = step;
      this.projects = List.copyOf(projects);
      this.journeys = List.copyOf(journeys);
    }

    public String module() { return module; }
    public int step() { return step; }
    public List<String> projects() { return projects; }
    public List<String> journeys() { return journeys; }
  }

  public record Evidence(String id, boolean recorded) {}
  public enum BatchState { UPCOMING, CURRENT, COMPLETE }
  public enum Narrative { EMPTY, PARTIAL, RECORDED }

  public record View(Kind kind, List<Evidence> projects, List<Evidence> journeys,
      boolean moduleProjectRecorded, Narrative narrative, BatchState batchState,
      int batchNumber, int totalBatches, List<EngineeringDiagnostics.Material> materials,
      boolean batchEligible, boolean archived, boolean ending) {
    public View {
      projects = List.copyOf(projects);
      journeys = List.copyOf(journeys);
      materials = List.copyOf(materials);
    }
  }

  private ArkFieldJournals() {}

  static View view(Campaigns.Campaign campaign, Kind kind) {
    List<Evidence> projects = kind.projects().stream()
        .map(id -> new Evidence(id, campaign.completed.contains(id))).toList();
    List<Evidence> journeys = kind.journeys().stream()
        .map(id -> new Evidence(id, campaign.completed.contains(id))).toList();
    long recorded = java.util.stream.Stream.concat(projects.stream(), journeys.stream())
        .filter(Evidence::recorded).count();
    int totalEvidence = projects.size() + journeys.size();
    Narrative narrative = recorded == 0 ? Narrative.EMPTY
        : recorded == totalEvidence ? Narrative.RECORDED : Narrative.PARTIAL;
    int phase = Math.clamp(campaign.arkPhase, 0, ArkCommissioning.STEPS.size());
    BatchState batch = phase < kind.step() ? BatchState.UPCOMING
        : phase == kind.step() ? BatchState.CURRENT : BatchState.COMPLETE;
    var step = ArkCommissioning.STEPS.get(kind.step());
    List<EngineeringDiagnostics.Material> materials = batch == BatchState.COMPLETE ? List.of()
        : step.requirements().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .map(entry -> new EngineeringDiagnostics.Material(entry.getKey(),
                batch == BatchState.CURRENT
                    ? Math.clamp(campaign.arkDeposits.getOrDefault(entry.getKey(), 0), 0, entry.getValue())
                    : 0,
                entry.getValue())).toList();
    return new View(kind, projects, journeys, campaign.completed.contains(kind.module()),
        narrative, batch, kind.step() + 1, ArkCommissioning.STEPS.size(), materials,
        ArkCommissioning.eligible(campaign), campaign.archived,
        campaign.completed.contains(CampaignMilestones.LAST_HORIZON));
  }

  public static boolean inspect(ServerPlayer player, BlockPos module) {
    if (player.isSpectator() || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)) return false;
    if (!(player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block))
      return false;
    var campaign = EngineeringDiagnostics.currentReadOnly(player);
    var physical = EngineeringDiagnostics.physicalView(player.serverLevel(), module);
    lines(view(campaign, block.kind()), physical).forEach(player::sendSystemMessage);
    return true;
  }

  static List<Component> lines(View view, EngineeringDiagnostics.PhysicalView physical) {
    List<Component> lines = new ArrayList<>();
    String prefix = "entrelumen.journal." + view.kind().name().toLowerCase(java.util.Locale.ROOT);
    lines.add(Component.translatable(prefix + ".title"));
    lines.add(Component.translatable(prefix + "." + view.narrative().name().toLowerCase(java.util.Locale.ROOT)));
    for (Evidence evidence : view.projects())
      lines.add(Component.translatable(evidence.recorded()
              ? "entrelumen.journal.project_recorded" : "entrelumen.journal.project_pending",
          Component.translatable("entrelumen.project." + evidence.id())));
    for (Evidence evidence : view.journeys())
      lines.add(Component.translatable(evidence.recorded()
              ? "entrelumen.journal.journey_recorded" : "entrelumen.journal.journey_pending",
          Component.translatable("entrelumen.project." + evidence.id())));
    lines.add(Component.translatable(view.moduleProjectRecorded()
            ? "entrelumen.journal.module_recorded" : "entrelumen.journal.module_pending",
        Component.translatable("entrelumen.project." + view.kind().module())));
    switch (view.batchState()) {
      case UPCOMING -> lines.add(Component.translatable("entrelumen.journal.batch_upcoming",
          view.batchNumber(), view.totalBatches()));
      case CURRENT -> lines.add(Component.translatable("entrelumen.journal.batch_current",
          view.batchNumber(), view.totalBatches()));
      case COMPLETE -> lines.add(Component.translatable("entrelumen.journal.batch_complete"));
    }
    for (var material : view.materials()) {
      var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(material.item()));
      lines.add(view.batchState() == BatchState.CURRENT
          ? Component.translatable("entrelumen.ark.material", new ItemStack(item).getHoverName(),
              material.deposited(), material.required(), material.remaining())
          : Component.translatable("entrelumen.journal.material_upcoming",
              new ItemStack(item).getHoverName(), material.required()));
    }
    if (view.batchState() == BatchState.CURRENT && !view.batchEligible())
      lines.add(Component.translatable("entrelumen.journal.batch_blocked"));
    if (view.archived()) lines.add(Component.translatable("entrelumen.engineering.archived"));
    if (view.ending()) lines.add(Component.translatable("entrelumen.journal.ending"));
    switch (physical.state()) {
      case ABSENT -> lines.add(Component.translatable("entrelumen.journal.no_controller"));
      case UNLOADED -> lines.add(Component.translatable("entrelumen.engineering.controller_unloaded"));
      case AMBIGUOUS -> lines.add(Component.translatable("entrelumen.engineering.ambiguous"));
      case FOUND -> {
        lines.add(Component.translatable("entrelumen.engineering.controller_at",
            physical.controller().getX(), physical.controller().getY(), physical.controller().getZ()));
        if (physical.moduleAreaUnloaded())
          lines.add(Component.translatable("entrelumen.engineering.modules_unloaded"));
        else if (!physical.missingModules().isEmpty())
          for (String id : physical.missingModules().stream().sorted().toList())
            lines.add(Component.translatable("entrelumen.ark.missing_module",
                Component.translatable("entrelumen.project." + id)));
      }
    }
    return List.copyOf(lines);
  }
}
