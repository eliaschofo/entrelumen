package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;

/**
 * The Ark's Nature Module and the team's garden site. Restoring land by hand moved to the Altar of
 * Renewal (vegetation) and the Altar of Levelling (terrain) on 25 September 2026. The module keeps
 * one job: a bookmarked compass marks the team's garden site, and an Altar of Renewal within
 * {@link AltarRules#ARK_SITE_REACH} blocks of it grows a larger garden. Bone meal on the module now
 * only points at the altar and uses nothing. The natural-land rules the altars share live here too.
 */
public final class NatureRestoration {
  public enum Status {
    MARKED, ALREADY_MARKED, MOVED, UNAVAILABLE, UNBOUND_COMPASS, OTHER_DIMENSION, TOO_FAR, STRUCTURE
  }

  public record Outcome(Status status) {
    static Outcome of(Status status) {
      return new Outcome(status);
    }

    public boolean success() {
      return status == Status.MARKED || status == Status.ALREADY_MARKED || status == Status.MOVED;
    }
  }

  private static final Set<Block> NATURAL_BLOCKS = Set.of(Blocks.GRAVEL, Blocks.CLAY,
      Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE, Blocks.PACKED_ICE,
      Blocks.BLUE_ICE, Blocks.CALCITE, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
      Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM,
      Blocks.MOSS_CARPET, Blocks.POINTED_DRIPSTONE, Blocks.DRIPSTONE_BLOCK);

  private NatureRestoration() {}

  /** A bookmarked compass marks the garden site; bone meal points at the Altar of Renewal. */
  public static Outcome use(ServerPlayer player, BlockPos module, InteractionHand hand) {
    ItemStack held = player.getItemInHand(hand);
    if (held.is(Items.COMPASS)) return mark(player, module, hand);
    if (held.is(Items.BONE_MEAL)) return moved(player, module, hand);
    return Outcome.of(Status.UNAVAILABLE);
  }

  public static Outcome mark(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (!reachable(player, module, hand) || !player.getItemInHand(hand).is(Items.COMPASS))
      return Outcome.of(Status.UNAVAILABLE);
    var tracker = player.getItemInHand(hand).get(DataComponents.LODESTONE_TRACKER);
    if (tracker == null || tracker.target().isEmpty())
      return refuse(player, Status.UNBOUND_COMPASS, Component.translatable("entrelumen.nature.unbound"));
    if (!assembled(player, module))
      return refuse(player, Status.STRUCTURE, Component.translatable("entrelumen.nature.structure"));
    GlobalPos target = tracker.target().get();
    Outcome refused = siteRefusal(player, module, target);
    if (refused != null) return refused;
    boolean changed = NatureRestorationData.get(player.server)
        .mark(CampaignActions.campaignId(player), target);
    BlockPos pos = target.pos();
    player.sendSystemMessage(Component.translatable(
        changed ? "entrelumen.nature.marked" : "entrelumen.nature.already_marked",
        pos.getX(), pos.getY(), pos.getZ(), AltarRules.ARK_SITE_REACH, 2 * AltarRules.RENEWAL_ARK_RADIUS + 1));
    return Outcome.of(changed ? Status.MARKED : Status.ALREADY_MARKED);
  }

  /** Bone meal no longer restores land here: it says where that went and uses nothing. */
  public static Outcome moved(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (!reachable(player, module, hand) || !player.getItemInHand(hand).is(Items.BONE_MEAL))
      return Outcome.of(Status.UNAVAILABLE);
    player.sendSystemMessage(Component.translatable("entrelumen.nature.moved"));
    return Outcome.of(Status.MOVED);
  }

  /** The module screen's garden row for the reader's team. */
  public static ArkFieldJournals.Service journalService(ServerPlayer player) {
    GlobalPos site = NatureRestorationData.get(player.server).site(CampaignActions.campaignId(player));
    List<Component> lines = new ArrayList<>();
    if (site == null) lines.add(Component.translatable("entrelumen.nature.journal.none"));
    else lines.add(Component.translatable("entrelumen.nature.journal.site",
        site.dimension().location().toString(), site.pos().getX(), site.pos().getY(), site.pos().getZ()));
    lines.add(Component.translatable("entrelumen.nature.journal.instructions",
        AltarRules.ARK_SITE_REACH, 2 * AltarRules.RENEWAL_ARK_RADIUS + 1));
    return new ArkFieldJournals.Service("entrelumen:renewal_altar", Component.translatable(
        site == null ? "entrelumen.journal.service.nature.none" : "entrelumen.journal.service.nature.site"),
        lines);
  }

  private static boolean reachable(ServerPlayer player, BlockPos module, InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND && !player.isSpectator()
        && player.canInteractWithBlock(module, 1.0)
        && player.serverLevel().hasChunkAt(module)
        && player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block
        && block.kind() == ArkFieldJournals.Kind.NATURE;
  }

  private static boolean assembled(ServerPlayer player, BlockPos module) {
    return LogisticsModuleActions.controllerForDeposit(
        EngineeringDiagnostics.physicalView(player.serverLevel(), module)) != null;
  }

  private static Outcome siteRefusal(ServerPlayer player, BlockPos module, GlobalPos site) {
    if (!site.dimension().equals(player.serverLevel().dimension()))
      return refuse(player, Status.OTHER_DIMENSION, Component.translatable("entrelumen.nature.other_dimension"));
    if (!NatureRestorationRules.withinReach(module.getX(), module.getZ(), site.pos().getX(), site.pos().getZ()))
      return refuse(player, Status.TOO_FAR, Component.translatable("entrelumen.nature.too_far",
          NatureRestorationRules.horizontalDistance(module.getX(), module.getZ(),
              site.pos().getX(), site.pos().getZ()),
          NatureRestorationRules.MAX_DISTANCE));
    return null;
  }

  private static Outcome refuse(ServerPlayer player, Status status, Component message) {
    player.sendSystemMessage(message);
    return Outcome.of(status);
  }

  /**
   * Terrain and wild vegetation. Any block entity, persistent (placed) leaves, stripped wood,
   * farmland, crops, paths and every other block counts as built and is protected with a margin.
   */
  static boolean natural(BlockState state) {
    if (state.isAir()) return true;
    if (state.hasBlockEntity()) return false;
    if (state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT))
      return false;
    if (state.is(Tags.Blocks.STRIPPED_LOGS) || state.is(Tags.Blocks.STRIPPED_WOODS)) return false;
    if (state.getBlock() instanceof LiquidBlock) return true;
    return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
        || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Tags.Blocks.ORES) || state.is(BlockTags.LOGS)
        || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
        || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.REPLACEABLE)
        || NATURAL_BLOCKS.contains(state.getBlock());
  }

  /** Decorations, vehicles and other non-living entities belong to someone's build. */
  static boolean built(Entity entity) {
    if (entity instanceof ArmorStand) return true;
    return !(entity instanceof LivingEntity || entity instanceof ItemEntity
        || entity instanceof ExperienceOrb || entity instanceof Projectile);
  }
}
