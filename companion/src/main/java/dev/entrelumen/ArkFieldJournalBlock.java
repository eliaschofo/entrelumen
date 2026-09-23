package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Four installed disciplines share one read-only native interaction. */
public final class ArkFieldJournalBlock extends Block {
  private final ArkFieldJournals.Kind kind;

  public ArkFieldJournalBlock(ArkFieldJournals.Kind kind, Properties properties) {
    super(properties);
    this.kind = kind;
  }

  public ArkFieldJournals.Kind kind() {
    return kind;
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer) ArkFieldJournals.inspect(serverPlayer, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
