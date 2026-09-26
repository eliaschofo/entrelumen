package dev.entrelumen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The ten key pieces, one per Plan v2 ruin: no recipe, a stack of one, a line of lore. The pedestal
 * binds each copy to the campaign it was given to and to a generation; a lost piece is replaced by
 * a new generation, and older copies fade the next time they are carried. Only a team's current
 * copy counts towards its project.
 */
public final class KeyPieces {
  private KeyPieces() {}

  /** Item id to rarity: the landmarks' pieces are epic, the medium ruins' rare. */
  public static final Map<String, Rarity> PIECES;

  static {
    Map<String, Rarity> pieces = new LinkedHashMap<>();
    pieces.put("signal_ember", Rarity.EPIC);
    pieces.put("terra_blueprint", Rarity.EPIC);
    pieces.put("route_seal", Rarity.EPIC);
    pieces.put("mother_seed", Rarity.RARE);
    pieces.put("heliodor_crucible", Rarity.RARE);
    pieces.put("voices_eyepiece", Rarity.EPIC);
    pieces.put("forest_testimony", Rarity.RARE);
    pieces.put("sun_key", Rarity.RARE);
    pieces.put("sacred_flame", Rarity.EPIC);
    pieces.put("star_chart", Rarity.RARE);
    PIECES = Collections.unmodifiableMap(pieces);
  }

  /** Who a copy belongs to: the campaign, the ruin that gave it and its generation there. */
  public record Binding(UUID campaign, String ruin, int generation) {
    public static final Codec<Binding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("campaign").forGetter(Binding::campaign),
        Codec.STRING.fieldOf("ruin").forGetter(Binding::ruin),
        Codec.INT.fieldOf("generation").forGetter(Binding::generation)).apply(instance, Binding::new));
    public static final StreamCodec<ByteBuf, Binding> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, Binding::campaign, ByteBufCodecs.STRING_UTF8, Binding::ruin,
        ByteBufCodecs.VAR_INT, Binding::generation, Binding::new);
  }

  public static boolean isPiece(ItemStack stack) {
    return stack.getItem() instanceof Piece;
  }

  @Nullable
  public static Binding binding(ItemStack stack) {
    return stack.get(RuinContent.BINDING.get());
  }

  /**
   * The stacks that count for {@code player}'s campaign: everything but key pieces of another team
   * and copies the pedestal has replaced. Unbound pieces (commands, creative) count.
   */
  public static List<ItemStack> deliverable(ServerPlayer player, List<ItemStack> stacks) {
    if (stacks.stream().noneMatch(stack -> isPiece(stack) && binding(stack) != null)) return stacks;
    var context = HeliodorCompass.context(player).orElse(null);
    var team = context == null ? null : RuinProgress.get(player.server).team(context.campaignId(), context.founder());
    List<ItemStack> result = new ArrayList<>(stacks.size());
    for (ItemStack stack : stacks) {
      Binding binding = isPiece(stack) ? binding(stack) : null;
      if (binding == null || (team != null && RuinRules.valid(binding.campaign(), binding.generation(),
          context.campaignId(), context.founder(), team.piece(binding.ruin())))) result.add(stack);
    }
    return result;
  }

  /** A new bound copy of {@code item}. */
  public static ItemStack bound(Item item, UUID campaign, String ruin, int generation) {
    ItemStack stack = new ItemStack(item);
    stack.set(RuinContent.BINDING.get(), new Binding(campaign, ruin, generation));
    return stack;
  }

  /** Whether any online member of the team carries its current copy of the ruin's piece. */
  static boolean held(List<ServerPlayer> members, Item item, UUID campaign, String ruin, int generation) {
    for (ServerPlayer member : members) {
      var inventory = member.getInventory();
      for (var list : List.of(inventory.items, inventory.offhand, inventory.armor))
        for (ItemStack stack : list) if (matches(stack, item, campaign, ruin, generation)) return true;
      for (ItemStack stack : member.getEnderChestInventory().getItems())
        if (matches(stack, item, campaign, ruin, generation)) return true;
      if (matches(member.containerMenu.getCarried(), item, campaign, ruin, generation)) return true;
    }
    return false;
  }

  private static boolean matches(ItemStack stack, Item item, UUID campaign, String ruin, int generation) {
    if (!stack.is(item)) return false;
    Binding binding = binding(stack);
    return binding != null && binding.campaign().equals(campaign) && binding.ruin().equals(ruin)
        && binding.generation() == generation;
  }

  /** One key piece. */
  public static final class Piece extends Item {
    public Piece(Properties properties) {
      super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    /** A copy the pedestal has replaced fades once carried (checked once a second). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      if (!(entity instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
      Binding binding = binding(stack);
      if (binding == null) return;
      var team = RuinProgress.get(player.server).peek(binding.campaign());
      if (team == null || team.piece(binding.ruin()) <= binding.generation()) return;
      Component name = stack.getHoverName();
      stack.setCount(0);
      player.displayClientMessage(Component.translatable("entrelumen.ruin.piece.faded", name), true);
      player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
          0.8f, 1.2f);
    }
  }

  public static String id(Item item) {
    return BuiltInRegistries.ITEM.getKey(item).toString();
  }
}
