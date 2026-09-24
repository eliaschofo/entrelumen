package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ENTRELUMEN bookshelf. With Apothic Enchanting its Eterna, Quanta and Arcana come from
 * {@code data/entrelumen/enchanting_stats}; without it the table counts {@link #power} like a
 * vanilla bookshelf.
 */
public final class EnchantingShelfBlock extends Block {
  private final float power;

  public EnchantingShelfBlock(float power, Properties properties) {
    super(properties);
    this.power = power;
  }

  @Override
  public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
    return power;
  }
}
