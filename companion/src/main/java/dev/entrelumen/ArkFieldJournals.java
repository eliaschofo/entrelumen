package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Ark's status screen, opened from any module or the controller (Ark v2): the clicked module's
 * global effect and whether it is on, the six modules with their effects, and the activation
 * checklist with where each missing piece comes from. Read-only: it never changes a campaign.
 */
public final class ArkFieldJournals {
  /** The six modules as blocks. */
  public enum Kind {
    ENGINEERING(ArkRules.Module.ENGINEERING),
    ARCANE(ArkRules.Module.ARCANE),
    NATURE(ArkRules.Module.NATURE),
    EXPLORATION(ArkRules.Module.EXPLORATION),
    LOGISTICS(ArkRules.Module.LOGISTICS),
    HABITATION(ArkRules.Module.HABITATION);

    private final ArkRules.Module module;

    Kind(ArkRules.Module module) {
      this.module = module;
    }

    public String module() {
      return module.id;
    }

    public ArkRules.Module arkModule() {
      return module;
    }

    public static Kind of(ArkRules.Module module) {
      for (Kind kind : values()) if (kind.module == module) return kind;
      throw new IllegalArgumentException(module.name());
    }
  }

  /**
   * A module service line (Ark v1). Kept while {@code NatureRestoration} still builds one; the screen
   * no longer shows services.
   */
  public record Service(String icon, Component label, List<Component> details) {
    public Service {
      details = List.copyOf(details);
    }
  }

  /** The single word beside the title: the clicked module's effect, or the Ark's state. */
  public enum Status { ACTIVE, INACTIVE, STANDING, BUILDING, ACTIVATED }

  static final String CONTROLLER_ICON = "entrelumen:ark_controller";
  static final String FALLBACK_ICON = "minecraft:paper";

  private ArkFieldJournals() {}

  /** Opens the screen for a module or the controller the player can reach. */
  public static boolean inspect(ServerPlayer player, BlockPos pos) {
    if (player.isSpectator() || !player.canInteractWithBlock(pos, 1.0) || !player.serverLevel().hasChunkAt(pos))
      return false;
    String block = ArkState.slotOf(player.serverLevel().getBlockState(pos).getBlock());
    if (block.isEmpty()) return false;
    var ark = ArkState.ark(player);
    if (ark != null && ark.dimension.equals(player.serverLevel().dimension()))
      ArkState.rescan(player.serverLevel(), ark, ArkData.get(player.server));
    ArkRules.Module module = ArkRules.Module.byId(block);
    PacketDistributor.sendToPlayer(player, module == null ? snapshot(player, block) : snapshot(player, pos, Kind.of(module)));
    return true;
  }

  /**
   * A module's screen. The Nature module still carries its restoration line while
   * {@code NatureRestoration} exists (the altars' worker retires it).
   */
  static JournalBookNetwork.Snapshot snapshot(ServerPlayer player, BlockPos module, Kind kind) {
    var screen = snapshot(player, kind.module());
    if (kind != Kind.NATURE) return screen;
    Service service = NatureRestoration.journalService(player);
    List<JournalBookNetwork.Entry> entries = new ArrayList<>(screen.entries());
    var icon = ResourceLocation.tryParse(service.icon());
    entries.add(1, new JournalBookNetwork.Entry(JournalBookNetwork.Section.EFFECT,
        icon == null ? ResourceLocation.parse(FALLBACK_ICON) : icon, Optional.of(service.label()), 0, 0,
        service.details().subList(0, Math.min(service.details().size(), JournalBookNetwork.MAX_DETAILS))));
    return new JournalBookNetwork.Snapshot(screen.player(), screen.campaign(), screen.block(), screen.status(),
        screen.statusLine(), screen.flavor(), screen.hint(), entries);
  }

  static JournalBookNetwork.Snapshot snapshot(ServerPlayer player, String block) {
    var campaign = ArkMigration.campaign(player);
    Set<String> completed = campaign == null ? Set.of() : campaign.completed;
    var ark = ArkState.ark(player);
    Predicate<String> itemExists = id -> {
      var location = ResourceLocation.tryParse(id);
      return location != null && BuiltInRegistries.ITEM.containsKey(location);
    };
    return screen(player.getUUID(), CampaignActions.campaignId(player), block, ArkState.status(player),
        ark == null ? -1 : ark.missingCore, completed, itemExists);
  }

  /** Pure screen model. {@code missingCore} is -1 when the team has no Ark yet. */
  static JournalBookNetwork.Snapshot screen(UUID player, UUID campaign, String block, ArkRules.Status ark,
      int missingCore, Set<String> completed, Predicate<String> itemExists) {
    boolean activated = completed.contains(CampaignMilestones.LAST_HORIZON);
    ArkRules.Module clicked = ArkRules.Module.byId(block);
    Status status;
    Component statusLine;
    if (clicked != null) {
      status = ark.active(clicked) ? Status.ACTIVE : Status.INACTIVE;
      statusLine = ark.active(clicked)
          ? Component.translatable("entrelumen.ark.screen.status.active", ark.level())
          : Component.translatable("entrelumen.ark.screen.status.inactive", reason(ark, clicked, missingCore));
    } else if (activated) {
      status = Status.ACTIVATED;
      statusLine = Component.translatable("entrelumen.ark.screen.status.activated", ark.level());
    } else if (ark.standing()) {
      status = Status.STANDING;
      statusLine = Component.translatable("entrelumen.ark.screen.status.standing", ark.level());
    } else {
      status = Status.BUILDING;
      statusLine = Component.translatable("entrelumen.ark.screen.status.building", reason(ark, null, missingCore));
    }

    List<JournalBookNetwork.Entry> entries = new ArrayList<>();
    if (clicked != null) {
      entries.add(new JournalBookNetwork.Entry(JournalBookNetwork.Section.EFFECT, icon(clicked.id, itemExists),
          Optional.of(effectName(clicked)), ark.active(clicked) ? 1 : 0, 1, effectDetails(clicked, ark)));
    }
    entries.add(new JournalBookNetwork.Entry(JournalBookNetwork.Section.EFFECT, icon("minecraft:beacon", itemExists),
        Optional.of(Component.translatable("entrelumen.ark.screen.level", ark.level(), ark.beacons(),
            ArkRules.MAX_BEACONS)), 0, 0,
        List.of(Component.translatable("entrelumen.ark.screen.level.detail"))));
    for (ArkRules.Module module : ArkRules.Module.values()) {
      entries.add(new JournalBookNetwork.Entry(JournalBookNetwork.Section.MODULES, icon(module.id, itemExists),
          Optional.of(Component.translatable("entrelumen.ark.screen.module", moduleName(module), effectName(module))),
          ark.active(module) ? 1 : 0, 1, moduleDetails(module, ark, completed)));
    }
    for (ArkRules.Item item : ArkRules.checklist(ark, completed)) {
      entries.add(new JournalBookNetwork.Entry(JournalBookNetwork.Section.ACTIVATION, checkIcon(item, itemExists),
          Optional.of(checkLabel(item, missingCore)), item.done() ? 1 : 0, 1, List.of(where(item, missingCore))));
    }
    Component flavor = Component.translatable(clicked != null
        ? "entrelumen.ark.screen.flavor." + clicked.key() : "entrelumen.ark.screen.flavor.controller");
    Optional<Component> hint;
    if (activated) hint = Optional.of(Component.translatable("entrelumen.ark.screen.hint.activated"));
    else if (ArkRules.checklistDone(ark, completed))
      hint = Optional.of(Component.translatable("entrelumen.ark.screen.hint.ready"));
    else hint = Optional.of(Component.translatable("entrelumen.ark.screen.hint.guide"));
    return new JournalBookNetwork.Snapshot(player, campaign, block, status, statusLine, flavor, hint, entries);
  }

  /** Why the Ark or a module is off, first cause first. */
  static Component reason(ArkRules.Status ark, ArkRules.Module module, int missingCore) {
    if (!ark.registered()) return Component.translatable("entrelumen.ark.screen.reason.no_ark");
    if (!ark.core()) return Component.translatable("entrelumen.ark.screen.reason.core", Math.max(0, missingCore));
    if (!ark.controller()) return Component.translatable("entrelumen.ark.screen.reason.controller");
    if (module != null && !ark.modules().contains(module.id))
      return Component.translatable("entrelumen.ark.screen.reason.slot");
    return Component.translatable("entrelumen.ark.screen.reason.none");
  }

  public static Component moduleName(ArkRules.Module module) {
    return Component.translatable("block.entrelumen." + module.id);
  }

  public static Component effectName(ArkRules.Module module) {
    return Component.translatable("entrelumen.ark.effect." + module.effect);
  }

  static List<Component> effectDetails(ArkRules.Module module, ArkRules.Status ark) {
    List<Component> lines = new ArrayList<>();
    lines.add(Component.translatable("entrelumen.ark.effect." + module.effect + ".detail", roman(Math.max(1, ark.level()))));
    lines.add(Component.translatable(ark.active(module) ? "entrelumen.ark.screen.effect_on"
        : "entrelumen.ark.screen.effect_off"));
    return lines;
  }

  static List<Component> moduleDetails(ArkRules.Module module, ArkRules.Status ark, Set<String> completed) {
    List<Component> lines = new ArrayList<>();
    lines.add(Component.translatable("entrelumen.ark.effect." + module.effect + ".detail", roman(Math.max(1, ark.level()))));
    lines.add(Component.translatable("entrelumen.ark.screen.source", roman(module.act),
        Component.translatable("entrelumen.project." + module.project())));
    lines.add(Component.translatable(ark.active(module) ? "entrelumen.ark.screen.effect_on"
        : ark.registered() && ark.modules().contains(module.id) ? "entrelumen.ark.screen.placed_off"
        : completed.contains(module.project()) ? "entrelumen.ark.screen.not_placed"
        : "entrelumen.ark.screen.not_obtained"));
    return lines;
  }

  public static Component checkLabel(ArkRules.Item item, int missingCore) {
    return switch (item.check()) {
      case CORE -> Component.translatable("entrelumen.ark.check.core");
      case CONTROLLER -> Component.translatable("block.entrelumen.ark_controller");
      case MODULE -> Component.translatable("block.entrelumen." + item.id());
      case PROJECT -> Component.translatable("entrelumen.project." + item.id());
      case JOURNEY -> Component.translatable("entrelumen.project." + item.id());
    };
  }

  /** Where a checklist item comes from. */
  public static Component where(ArkRules.Item item, int missingCore) {
    return switch (item.check()) {
      case CORE -> missingCore < 0 ? Component.translatable("entrelumen.ark.check.core.where_none")
          : Component.translatable("entrelumen.ark.check.core.where", Math.max(0, missingCore));
      case CONTROLLER -> Component.translatable("entrelumen.ark.check.controller.where");
      case MODULE -> {
        ArkRules.Module module = ArkRules.Module.byId(item.id());
        yield Component.translatable("entrelumen.ark.screen.source", roman(module.act),
            Component.translatable("entrelumen.project." + module.project()));
      }
      case PROJECT -> Component.translatable("entrelumen.ark.check.project.where");
      case JOURNEY -> Component.translatable("entrelumen.ark.check.journey.where");
    };
  }

  static String roman(int act) {
    return switch (act) {
      case 1 -> "I";
      case 2 -> "II";
      case 3 -> "III";
      case 4 -> "IV";
      case 5 -> "V";
      default -> "VI";
    };
  }

  private static ResourceLocation checkIcon(ArkRules.Item item, Predicate<String> itemExists) {
    return switch (item.check()) {
      case CORE -> icon("minecraft:stone_bricks", itemExists);
      case CONTROLLER -> icon(CONTROLLER_ICON, itemExists);
      case MODULE -> icon("entrelumen:" + item.id(), itemExists);
      case PROJECT -> icon("minecraft:filled_map", itemExists);
      case JOURNEY -> icon("minecraft:end_stone", itemExists);
    };
  }

  private static ResourceLocation icon(String id, Predicate<String> itemExists) {
    String full = id.contains(":") ? id : "entrelumen:" + id;
    return ResourceLocation.parse(itemExists.test(full) ? full : FALLBACK_ICON);
  }

  static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
