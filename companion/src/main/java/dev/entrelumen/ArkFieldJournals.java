package dev.entrelumen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only, team-specific status of four Ark disciplines, shown on the module screen. */
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
  public enum DepositState { FUTURE, CURRENT, COMPLETE, BLOCKED }
  public enum Narrative { EMPTY, PARTIAL, RECORDED }

  /** The single status word beside the batch number. */
  public enum Status { WAITING, DELIVERING, BLOCKED, DELIVERED, ARCHIVED }

  /** One module service: a short state line and the full how-to shown on hover. */
  public record Service(String icon, Component label, List<Component> details) {
    public Service {
      details = List.copyOf(details);
    }
  }

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

  /**
   * Each witnessed journey is drawn with the icon its own mod gives the dimension: the Aether's
   * "The Aether" advancement, the Twilight Forest and Bumblezone root advancements and vanilla's
   * End root (also the pack's own End quest). A vanilla stand-in covers a missing mod.
   */
  static final Map<String, List<String>> JOURNEY_ICONS = Map.of(
      "aether_arrival", List.of("aether:aether_portal_frame", "minecraft:glowstone"),
      "twilight_arrival", List.of("twilightforest:twilight_portal_miniature_structure",
          "minecraft:oak_sapling"),
      "bumblezone_arrival", List.of("the_bumblezone:honeycomb_brood_block",
          "minecraft:honeycomb_block"),
      "end_arrival", List.of("minecraft:end_stone"));
  static final String CONTROLLER_ICON = "entrelumen:ark_controller";
  static final String FALLBACK_ICON = "minecraft:paper";

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
    // A delivered batch reads as full; an upcoming one shows its static cost with nothing stored.
    List<EngineeringDiagnostics.Material> materials = step.requirements().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> new EngineeringDiagnostics.Material(entry.getKey(), switch (batch) {
              case UPCOMING -> 0;
              case CURRENT -> Math.clamp(campaign.arkDeposits.getOrDefault(entry.getKey(), 0), 0,
                  entry.getValue());
              case COMPLETE -> entry.getValue();
            }, entry.getValue())).toList();
    return new View(kind, projects, journeys, campaign.completed.contains(kind.module()),
        narrative, batch, kind.step() + 1, ArkCommissioning.STEPS.size(), materials,
        ArkCommissioning.eligible(campaign), campaign.archived,
        campaign.completed.contains(CampaignMilestones.LAST_HORIZON));
  }

  static Status status(View view) {
    if (view.archived()) return Status.ARCHIVED;
    return switch (view.batchState()) {
      case UPCOMING -> Status.WAITING;
      case CURRENT -> view.batchEligible() ? Status.DELIVERING : Status.BLOCKED;
      case COMPLETE -> Status.DELIVERED;
    };
  }

  public static boolean inspect(ServerPlayer player, BlockPos module) {
    if (player.isSpectator() || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)) return false;
    if (!(player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block))
      return false;
    PacketDistributor.sendToPlayer(player, snapshot(player, module, block.kind()));
    return true;
  }

  static DepositState depositState(Campaigns.Campaign campaign, Kind kind) {
    if (campaign.arkPhase < kind.step()) return DepositState.FUTURE;
    if (campaign.arkPhase > kind.step()) return DepositState.COMPLETE;
    return ArkCommissioning.eligible(campaign) ? DepositState.CURRENT : DepositState.BLOCKED;
  }

  public static boolean deposit(ServerPlayer player, BlockPos module) {
    if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()
        || player.isSpectator() || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !(player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block))
      return false;
    var kind = block.kind();
    switch (depositState(EngineeringDiagnostics.currentReadOnly(player), kind)) {
      case FUTURE -> {
        player.sendSystemMessage(Component.translatable("entrelumen.journal.deposit_future",
            kind.step() + 1, ArkCommissioning.STEPS.size()));
        return false;
      }
      case COMPLETE -> {
        player.sendSystemMessage(Component.translatable("entrelumen.journal.deposit_complete"));
        return false;
      }
      case BLOCKED -> {
        player.sendSystemMessage(Component.translatable("entrelumen.journal.deposit_blocked"));
        return false;
      }
      case CURRENT -> {
        return ArkActions.depositFromJournal(player, CampaignActions.campaignId(player), module, kind);
      }
    }
    throw new IllegalStateException("Unknown journal deposit state");
  }

  static JournalBookNetwork.Snapshot snapshot(ServerPlayer player, BlockPos module, Kind kind) {
    var campaign = EngineeringDiagnostics.currentReadOnly(player);
    var physical = EngineeringDiagnostics.physicalView(player.serverLevel(), module);
    Service service = switch (kind) {
      case ARCANE -> new Service("minecraft:anvil",
          Component.translatable("entrelumen.journal.service.arcane"),
          List.of(Component.translatable("entrelumen.arcane.tooltip")));
      case NATURE -> NatureRestoration.journalService(player);
      case EXPLORATION -> ArkCharts.journalService();
      case HABITATION -> ArkHabitation.journalService(player);
    };
    Predicate<String> itemExists = id -> {
      var location = ResourceLocation.tryParse(id);
      return location != null && BuiltInRegistries.ITEM.containsKey(location);
    };
    return screen(player.getUUID(), CampaignActions.campaignId(player), view(campaign, kind),
        physical, id -> projectIcon(id, Projects.all()), itemExists, Optional.of(service));
  }

  /** The item a project is known by: the module it unlocks, else the first own item it takes. */
  static String projectIcon(String id, Map<String, Projects.Project> projects) {
    var project = projects.get(id);
    if (project == null) return FALLBACK_ICON;
    if (CampaignMilestones.MODULE_IDS.contains(id) && !project.reward().isEmpty())
      return project.reward();
    return project.items().keySet().stream().filter(item -> item.startsWith("entrelumen:"))
        .findFirst().orElseGet(() -> project.items().keySet().stream().findFirst()
            .orElse(FALLBACK_ICON));
  }

  /** Pure screen model: one status line, the batch, projects, journeys, the Ark and the service. */
  static JournalBookNetwork.Snapshot screen(UUID player, UUID campaign, View view,
      EngineeringDiagnostics.PhysicalView physical, Function<String, String> projectIcon,
      Predicate<String> itemExists, Optional<Service> service) {
    Status status = status(view);
    Component statusLine = status == Status.ARCHIVED
        ? Component.translatable("entrelumen.journal.status.archived")
        : Component.translatable("entrelumen.journal.status." + name(status),
            view.batchNumber(), view.totalBatches());
    Component flavor = Component.translatable("entrelumen.journal." + name(view.kind()) + "."
        + name(view.narrative()));
    Optional<Component> hint = switch (status) {
      case DELIVERING -> Optional.of(Component.translatable("entrelumen.journal.hint.deliver"));
      case WAITING -> Optional.of(Component.translatable("entrelumen.journal.hint.waiting",
          view.batchNumber() - 1));
      case BLOCKED -> Optional.of(Component.translatable("entrelumen.journal.batch_blocked"));
      case ARCHIVED -> Optional.of(Component.translatable("entrelumen.engineering.archived"));
      case DELIVERED -> Optional.empty();
    };

    List<JournalBookNetwork.Entry> entries = new ArrayList<>();
    for (var material : view.materials())
      entries.add(new JournalBookNetwork.Entry(JournalBookNetwork.Section.BATCH,
          icon(material.item(), itemExists), Optional.empty(), material.deposited(),
          material.required(), List.of()));

    List<JournalBookNetwork.Entry> projects = new ArrayList<>();
    for (Evidence evidence : view.projects())
      projects.add(check(JournalBookNetwork.Section.PROJECTS,
          icon(projectIcon.apply(evidence.id()), itemExists), evidence.id(), evidence.recorded()));
    projects.add(check(JournalBookNetwork.Section.PROJECTS,
        icon(projectIcon.apply(view.kind().module()), itemExists), view.kind().module(),
        view.moduleProjectRecorded()));
    // What is still missing reads first; the stable sort keeps campaign order inside each group.
    projects.sort(Comparator.comparingInt(JournalBookNetwork.Entry::done));
    entries.addAll(projects);

    for (Evidence journey : view.journeys()) {
      String icon = JOURNEY_ICONS.getOrDefault(journey.id(), List.of()).stream()
          .filter(itemExists).findFirst().orElse(FALLBACK_ICON);
      entries.add(check(JournalBookNetwork.Section.JOURNEYS, icon(icon, itemExists), journey.id(),
          journey.recorded()));
    }

    entries.add(ark(view, physical, itemExists));
    service.ifPresent(value -> entries.add(new JournalBookNetwork.Entry(
        JournalBookNetwork.Section.SERVICE, icon(value.icon(), itemExists),
        Optional.of(value.label()), 0, 0, value.details())));
    return new JournalBookNetwork.Snapshot(player, campaign, view.kind(), status, statusLine,
        flavor, hint, entries);
  }

  private static JournalBookNetwork.Entry check(JournalBookNetwork.Section section,
      ResourceLocation icon, String id, boolean recorded) {
    return new JournalBookNetwork.Entry(section, icon,
        Optional.of(Component.translatable("entrelumen.project." + id)), recorded ? 1 : 0, 1,
        List.of());
  }

  private static JournalBookNetwork.Entry ark(View view,
      EngineeringDiagnostics.PhysicalView physical, Predicate<String> itemExists) {
    var icon = icon(CONTROLLER_ICON, itemExists);
    if (view.ending())
      return arkEntry(icon, "active", true, List.of(Component.translatable(
          "entrelumen.journal.ark.active_detail")));
    return switch (physical.state()) {
      case ABSENT -> arkEntry(icon, "absent", false,
          List.of(Component.translatable("entrelumen.journal.no_controller")));
      case UNLOADED -> arkEntry(icon, "unloaded", false,
          List.of(Component.translatable("entrelumen.engineering.controller_unloaded")));
      case AMBIGUOUS -> arkEntry(icon, "ambiguous", false,
          List.of(Component.translatable("entrelumen.engineering.ambiguous")));
      case FOUND -> {
        List<Component> details = new ArrayList<>();
        details.add(Component.translatable("entrelumen.engineering.controller_at",
            physical.controller().getX(), physical.controller().getY(),
            physical.controller().getZ()));
        if (physical.moduleAreaUnloaded()) {
          details.add(Component.translatable("entrelumen.engineering.modules_unloaded"));
          yield arkEntry(icon, "unloaded", false, details);
        }
        if (physical.missingModules().isEmpty()) yield arkEntry(icon, "ready", true, details);
        for (String id : physical.missingModules().stream().sorted().toList())
          details.add(Component.translatable("entrelumen.ark.missing_module",
              Component.translatable("entrelumen.project." + id)));
        yield new JournalBookNetwork.Entry(JournalBookNetwork.Section.ARK, icon,
            Optional.of(Component.translatable("entrelumen.journal.ark.missing",
                physical.missingModules().size())), 0, 1, details);
      }
    };
  }

  private static JournalBookNetwork.Entry arkEntry(ResourceLocation icon, String state,
      boolean ready, List<Component> details) {
    return new JournalBookNetwork.Entry(JournalBookNetwork.Section.ARK, icon,
        Optional.of(Component.translatable("entrelumen.journal.ark." + state)), ready ? 1 : 0, 1,
        details);
  }

  private static ResourceLocation icon(String id, Predicate<String> itemExists) {
    return ResourceLocation.parse(itemExists.test(id) ? id : FALLBACK_ICON);
  }

  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
