package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Atlas Library block. Its drop always carries the pool (as the Apothic libraries do); the
 * block item restores it through the vanilla {@code block_entity_data} component on placement.
 */
public final class AtlasLibraryBlock extends Block implements EntityBlock {
  public AtlasLibraryBlock(Properties properties) {
    super(properties);
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new AtlasLibraryBlockEntity(pos, state);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (level.isClientSide) return InteractionResult.SUCCESS;
    if (player instanceof ServerPlayer serverPlayer
        && level.getBlockEntity(pos) instanceof AtlasLibraryBlockEntity) {
      serverPlayer.openMenu(new SimpleMenuProvider(
          (id, inventory, ignored) -> new AtlasLibraryMenu(id, inventory, pos),
          Component.translatable("block.entrelumen.atlas_library")), buf -> buf.writeBlockPos(pos));
    }
    return InteractionResult.CONSUME;
  }

  @Override
  protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
    ItemStack drop = new ItemStack(this);
    if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AtlasLibraryBlockEntity library
        && library.pool() > 0)
      library.saveToItem(drop, params.getLevel().registryAccess());
    return List.of(drop);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
      TooltipFlag flag) {
    tooltip.add(Component.translatable("block.entrelumen.atlas_library.hint").withStyle(ChatFormatting.GRAY));
    CustomData data = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
    long pool = data.isEmpty() ? 0 : Math.max(0L, data.copyTag().getLong(AtlasLibraryBlockEntity.POOL_TAG));
    if (pool > 0)
      tooltip.add(Component.translatable("block.entrelumen.atlas_library.stored", pool)
          .withStyle(ChatFormatting.GOLD));
  }
}
