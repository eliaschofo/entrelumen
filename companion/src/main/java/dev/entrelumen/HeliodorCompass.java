package dev.entrelumen;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.*;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server side of the Heliodor compass: picks the team's current objective, latches reached ones,
 * resolves where the needle points and hands out one compass per player at a ruin pedestal.
 */
public final class HeliodorCompass {
  /** A player must travel this far from an empty search before the compass searches again. */
  static final int RESEARCH_DISTANCE = 512;

  record Context(
      ServerPlayer player,
      UUID campaignId,
      @Nullable UUID founder,
      Campaigns.Campaign campaign,
      List<ServerPlayer> members) {}

  private HeliodorCompass() {}

  static Optional<Context> context(ServerPlayer player) {
    try {
      var manager = FTBTeamsAPI.api().getManager();
      if (manager == null) return Optional.empty();
      var team = manager.getTeamForPlayer(player).orElse(null);
      if (team == null) return Optional.empty();
      boolean party = team.isPartyTeam();
      List<ServerPlayer> members = new ArrayList<>(team.getOnlineMembers());
      if (!members.contains(player)) members.add(player);
      return Optional.of(new Context(player, party ? team.getId() : player.getUUID(),
          party ? team.getOwner() : null, Entrelumen.current(player), List.copyOf(members)));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  public static CompassState compute(ServerPlayer player) {
    return compute(player, CompassTargets.active());
  }

  /** Evaluates the team's progress, latches newly met objectives and resolves the current one. */
  static CompassState compute(ServerPlayer player, List<CompassTargets.Objective> objectives) {
    Context context = context(player).orElse(null);
    if (context == null) return CompassState.IDLE;
    CompassData data = CompassData.get(player.server);
    Set<String> reached = data.reached(context.campaignId(), context.founder());
    var evaluation = CompassProgress.evaluate(objectives, context.campaign().act, reached,
        condition -> satisfied(condition, context));
    if (!evaluation.newlyReached().isEmpty()) {
      reached.addAll(evaluation.newlyReached());
      data.setDirty();
      announce(context, evaluation.current(), evaluation.lockedByAct());
    }
    var objective = evaluation.current();
    if (objective == null) return CompassState.IDLE;
    int dimension = CompassState.dimensionValue(objective.target().dimension());
    int kind = objective.kind().ordinal();
    if (evaluation.lockedByAct())
      return new CompassState(Optional.empty(), CompassState.LOCKED, dimension, kind, objective.id());
    return resolve(player, objective, data, true);
  }

  /** Where the needle points for {@code objective}, queueing a bounded search when needed. */
  static CompassState resolve(ServerPlayer player, CompassTargets.Objective objective,
      CompassData data, boolean honorWorldOptions) {
    var target = objective.target();
    int dimension = CompassState.dimensionValue(target.dimension());
    int kind = objective.kind().ordinal();
    String id = objective.id();
    ResourceKey<Level> key =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(target.dimension()));
    if (!player.level().dimension().equals(key))
      return new CompassState(Optional.empty(), CompassState.ELSEWHERE, dimension, kind, id);
    BlockPos here = player.blockPosition();
    long radiusSqr = (long) target.radius() * target.radius();
    return switch (target.type()) {
      case DIMENSION ->
          new CompassState(Optional.empty(), CompassState.NOT_FOUND, dimension, kind, id);
      case POSITION -> pointing(key, target.pos(), dimension, kind, id);
      case CITY_MARKER -> {
        BlockPos marker = cityMarker(SolsticioData.get(player.server), target.value());
        yield marker == null
            ? new CompassState(Optional.empty(), CompassState.NOT_FOUND, dimension, kind, id)
            : pointing(key, marker, dimension, kind, id);
      }
      case ANCHOR -> {
        ResourceLocation anchor = ResourceLocation.parse(target.value());
        BlockPos nearest = nearest(RuinData.get(player.server).ruins().stream()
            .filter(ruin -> ruin.id().equals(anchor) && ruin.dimension().equals(key))
            .map(RuinData.Ruin::center), here, radiusSqr);
        yield nearest == null
            ? new CompassState(Optional.empty(), CompassState.NOT_FOUND, dimension, kind, id)
            : pointing(key, nearest, dimension, kind, id);
      }
      case STRUCTURE, BIOME -> {
        String siteKey = target.key();
        CompassData.Sites sites = data.sites.get(siteKey);
        BlockPos nearest = sites == null ? null : nearest(sites.found.stream(), here, radiusSqr);
        if (nearest != null) yield pointing(key, nearest, dimension, kind, id);
        boolean covered = sites != null && sites.emptyOrigins.stream()
            .anyMatch(origin -> CompassData.horizontalDistanceSqr(origin, here)
                <= (long) RESEARCH_DISTANCE * RESEARCH_DISTANCE);
        if (covered)
          yield new CompassState(Optional.empty(), CompassState.NOT_FOUND, dimension, kind, id);
        var locator = CompassLocator.of(player.server);
        String request = siteKey + "@" + (here.getX() >> 9) + "," + (here.getZ() >> 9);
        ServerLevel level = player.serverLevel();
        BlockPos origin = here.immutable();
        if (!locator.pending(request))
          locator.request(request, level, target, origin, honorWorldOptions, outcome -> {
            if (outcome.found().isPresent()) data.addFound(siteKey, outcome.found().get());
            else data.addEmpty(siteKey, origin);
          });
        yield new CompassState(Optional.empty(), CompassState.SEARCHING, dimension, kind, id);
      }
    };
  }

  /** Where the placed city put one of its markers, or null before the city is ready. */
  @Nullable
  static BlockPos cityMarker(SolsticioData data, String marker) {
    if (!data.ready()) return null;
    return marker.equals("town_hall_portal") ? data.portal : data.npcs.get(marker);
  }

  private static CompassState pointing(
      ResourceKey<Level> level, BlockPos pos, int dimension, int kind, String id) {
    return new CompassState(Optional.of(GlobalPos.of(level, pos.immutable())),
        CompassState.POINTING, dimension, kind, id);
  }

  @Nullable
  private static BlockPos nearest(Stream<BlockPos> positions, BlockPos here, long radiusSqr) {
    return positions
        .filter(pos -> CompassData.horizontalDistanceSqr(pos, here) <= radiusSqr)
        .min(Comparator.comparingLong(pos -> CompassData.horizontalDistanceSqr(pos, here)))
        .orElse(null);
  }

  static boolean satisfied(CompassTargets.Condition condition, Context context) {
    return switch (condition.type()) {
      case MILESTONE -> CampaignMilestones.isComplete(context.campaign(), condition.value());
      case ADVANCEMENT -> {
        var holder = context.player().server.getAdvancements()
            .get(ResourceLocation.parse(condition.value()));
        yield holder != null && context.members().stream()
            .anyMatch(member -> member.getAdvancements().getOrStartProgress(holder).isDone());
      }
      case ITEM -> context.members().stream().anyMatch(member -> count(member, condition) >= condition.count());
    };
  }

  static int count(ServerPlayer player, CompassTargets.Condition condition) {
    var inventory = player.getInventory();
    boolean tag = condition.value().startsWith("#");
    TagKey<net.minecraft.world.item.Item> tagKey = tag
        ? TagKey.create(Registries.ITEM, ResourceLocation.parse(condition.value().substring(1)))
        : null;
    int total = 0;
    for (var list : List.of(inventory.items, inventory.offhand, inventory.armor))
      for (ItemStack stack : list) {
        if (stack.isEmpty()) continue;
        boolean match = tag ? stack.is(tagKey)
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(condition.value());
        if (match) total += stack.getCount();
      }
    return total;
  }

  private static void announce(Context context, @Nullable CompassTargets.Objective next,
      boolean locked) {
    Component message = next == null
        ? Component.translatable("entrelumen.compass.announce.complete")
        : locked
            ? Component.translatable("entrelumen.compass.announce.locked")
            : Component.translatable("entrelumen.compass.announce.next",
                Component.translatable("entrelumen.compass.objective." + next.id()));
    for (ServerPlayer member : context.members()) member.sendSystemMessage(message);
  }

  /** Writes the computed state into {@code stack} only when it changed, avoiding resyncs. */
  public static void refresh(ServerPlayer player, ItemStack stack) {
    CompassState state = compute(player);
    if (!state.equals(stack.get(HeliodorContent.COMPASS_STATE.get())))
      stack.set(HeliodorContent.COMPASS_STATE.get(), state);
  }

  /** Atlas projection: the current objective and the lore fragments the team recovered. */
  public static AtlasNetwork.CompassView view(ServerPlayer player) {
    CompassState state = compute(player);
    var objectives = CompassTargets.active();
    Set<String> reached = context(player)
        .map(context -> CompassData.get(player.server).reached(context.campaignId(), context.founder()))
        .orElse(Set.of());
    String dimension = objectives.stream()
        .filter(objective -> objective.id().equals(state.objective()))
        .map(objective -> objective.target().dimension())
        .findFirst().orElse("");
    return new AtlasNetwork.CompassView(state.objective(), state.state(), dimension, state.kind(),
        CompassProgress.lore(objectives, reached));
  }

  /**
   * One compass per player UUID, ever, per world. Returns true when a compass was handed over.
   */
  public static boolean claim(ServerPlayer player) {
    if (player.isSpectator()) return false;
    CompassData data = CompassData.get(player.server);
    if (!data.claimed.add(player.getUUID())) {
      player.displayClientMessage(Component.translatable("entrelumen.pedestal.claimed"), true);
      return false;
    }
    data.setDirty();
    ItemStack compass = new ItemStack(HeliodorContent.COMPASS.get());
    refresh(player, compass);
    if (!player.getInventory().add(compass)) player.drop(compass, false);
    player.displayClientMessage(Component.translatable("entrelumen.pedestal.received"), true);
    player.level().playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK,
        SoundSource.PLAYERS, 1f, 1f);
    return true;
  }
}
