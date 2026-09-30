package dev.entrelumen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.entrelumen.TerraGarden;
import dev.entrelumen.TerraGardenCoreBlock;
import dev.entrelumen.TerraGardenCoreEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws the crop growing in the core's pot: the seed's own crop block, small, standing in the soil at the
 * pot's centre (on the engine's mirror plane). While the garden grows the crop runs through its ages,
 * about four a second, so the heart looks alive; otherwise it stands half grown. No seed, no crop.
 */
@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TerraGardenCoreRenderer implements BlockEntityRenderer<TerraGardenCoreEntity> {
  /** The crop's size against a whole block: about 9 units tall in the 8-unit pot. */
  private static final float SCALE = 0.56f;
  /** The soil's height in the pot model. */
  private static final float SOIL = 6f / 16f;

  public TerraGardenCoreRenderer(BlockEntityRendererProvider.Context context) {}

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(TerraGarden.CORE_ENTITY.get(), TerraGardenCoreRenderer::new);
  }

  /** The pot's centre in the block, in blocks: half a block to the side the facing puts the mirror plane on. */
  static float[] centre(Direction facing) {
    return switch (facing) {
      case WEST -> new float[] {0.5f, 0f};
      case NORTH -> new float[] {1f, 0.5f};
      case EAST -> new float[] {0.5f, 1f};
      default -> new float[] {0f, 0.5f};
    };
  }

  @Override
  public void render(TerraGardenCoreEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers,
      int light, int overlay) {
    CropBlock crop = TerraGarden.crop(core.seed());
    if (crop == null) return;
    BlockState state = core.getBlockState();
    boolean growing = state.getValue(TerraGardenCoreBlock.GARDEN) == TerraGardenCoreBlock.Garden.GROWING;
    int max = crop.getMaxAge();
    long time = core.getLevel() == null ? 0 : core.getLevel().getGameTime();
    int age = growing ? (int) ((time / 5) % (max + 1)) : max / 2;
    float[] at = centre(state.getValue(TerraGardenCoreBlock.FACING));
    pose.pushPose();
    pose.translate(at[0], SOIL, at[1]);
    pose.scale(SCALE, SCALE, SCALE);
    pose.translate(-0.5f, 0f, -0.5f);
    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(crop.getStateForAge(age), pose, buffers, light, overlay);
    pose.popPose();
  }

  /** The crop may stand outside the core's own cell (the pot sits on the block's edge). */
  @Override
  public net.minecraft.world.phys.AABB getRenderBoundingBox(TerraGardenCoreEntity core) {
    return new net.minecraft.world.phys.AABB(core.getBlockPos()).inflate(1);
  }
}
