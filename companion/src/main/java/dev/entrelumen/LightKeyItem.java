package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The Light Key and its broken return form. Holding use channels a temporary passage for
 * {@link LightKeyRules#CHANNEL_TICKS}; releasing earlier cancels without any change. The server
 * validates again when the channel completes, travels first and only then breaks the key, so a
 * refused or failed crossing never consumes it.
 */
public final class LightKeyItem extends Item {
  private final boolean broken;

  public LightKeyItem(boolean broken, Properties properties) {
    super(properties);
    this.broken = broken;
  }

  public boolean broken() {
    return broken;
  }

  @Override
  public int getUseDuration(ItemStack stack, LivingEntity entity) {
    return LightKeyRules.CHANNEL_TICKS;
  }

  @Override
  public UseAnim getUseAnimation(ItemStack stack) {
    return UseAnim.BOW;
  }

  @Override
  public boolean isFoil(ItemStack stack) {
    return !broken;
  }

  LightKeyRules.Outcome outcome(ServerPlayer player, ItemStack stack) {
    var owner = stack.get(Solsticio.KEY_OWNER);
    boolean arkActivated = Solsticio.GATE.satisfiedBy(StructureProtection.actor(player));
    boolean inSolsticio = SolsticioTravel.inSolsticio(player);
    boolean ready = SolsticioData.get(player.server).ready();
    return LightKeyRules.decide(broken, owner == null ? null : owner.id(), player.getUUID(), inSolsticio,
        arkActivated, ready);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (player instanceof ServerPlayer serverPlayer) {
      var outcome = outcome(serverPlayer, stack);
      if (outcome == LightKeyRules.Outcome.CITY_FORMING) SolsticioCity.ensure(serverPlayer.server);
      if (!outcome.travels) {
        refuse(serverPlayer, outcome);
        return InteractionResultHolder.fail(stack);
      }
    }
    player.startUsingItem(hand);
    return InteractionResultHolder.consume(stack);
  }

  @Override
  public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
    if (!level.isClientSide) return;
    // The passage gathers as a narrowing ring of light while the key is held.
    double progress = 1.0 - remaining / (double) LightKeyRules.CHANNEL_TICKS;
    double radius = 1.4 - progress;
    for (int i = 0; i < 2; i++) {
      double angle = (remaining * 0.6) + i * Math.PI;
      level.addParticle(ParticleTypes.END_ROD, entity.getX() + Math.cos(angle) * radius,
          entity.getY() + 0.2 + progress * 1.6, entity.getZ() + Math.sin(angle) * radius, 0, 0.01, 0);
    }
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
    if (!(entity instanceof ServerPlayer player)) return stack;
    return cross(player, stack);
  }

  /** The server side of a completed channel; returns what the hand holds afterwards. */
  public static ItemStack cross(ServerPlayer player, ItemStack stack) {
    if (!(stack.getItem() instanceof LightKeyItem key)) return stack;
    var outcome = key.outcome(player, stack);
    switch (outcome) {
      case CROSS_AND_BREAK -> {
        if (!SolsticioTravel.toSolsticio(player)) return stack;
        ItemStack returnKey = new ItemStack(Solsticio.LIGHT_KEY_BROKEN.get());
        returnKey.set(Solsticio.KEY_OWNER, new Solsticio.KeyOwner(player.getUUID(), player.getGameProfile().getName()));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.sendSystemMessage(Component.translatable("entrelumen.light_key.broke"));
        if (stack.getCount() <= 1) return returnKey;
        stack.shrink(1);
        Solsticio.give(player, returnKey);
        return stack;
      }
      case TO_SOLSTICIO -> SolsticioTravel.toSolsticio(player);
      case HOME -> SolsticioTravel.home(player);
      default -> refuse(player, outcome);
    }
    return stack;
  }

  private static void refuse(ServerPlayer player, LightKeyRules.Outcome outcome) {
    String key = switch (outcome) {
      case NOT_OWNER -> "entrelumen.light_key.not_owner";
      case LOCKED -> "entrelumen.light_key.locked";
      case ALREADY_INSIDE -> "entrelumen.light_key.inside";
      default -> "entrelumen.solsticio.forming";
    };
    player.displayClientMessage(Component.translatable(key), true);
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    if (broken) {
      var owner = stack.get(Solsticio.KEY_OWNER);
      tooltip.add(owner == null
          ? Component.translatable("entrelumen.light_key.unbound").withStyle(ChatFormatting.GRAY)
          : Component.translatable("entrelumen.light_key.owner", owner.name()).withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("entrelumen.light_key_broken.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    } else {
      tooltip.add(Component.translatable("entrelumen.light_key.tooltip").withStyle(ChatFormatting.GRAY));
    }
  }
}
