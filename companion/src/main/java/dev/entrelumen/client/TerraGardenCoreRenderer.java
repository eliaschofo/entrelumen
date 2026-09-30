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
 * Draws the crop growing in the formed engine's pot: the seed's own crop block, small, standing in the soil
 * at the pot's centre, running through its ages about four times a second so the heart looks alive. No seed,
 * or an unformed engine, no crop.
 */
@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TerraGardenCoreRenderer implements BlockEntityRenderer<TerraGardenCoreEntity> {
  /** The crop's size against a whole block: about 9 units tall in the 10-unit pot. */
  private static final float SCALE = 0.56f;
  /** The soil's height in the formed engine model (the pot sits on the engine block). */
  private static final float SOIL = 24f / 16f;

  public TerraGardenCoreRenderer(BlockEntityRendererProvider.Context context) {}

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(TerraGarden.CORE_ENTITY.get(), TerraGardenCoreRenderer::new);
  }

  /**
   * The pot's centre in the core's cell, in blocks: on the middle column's axis and on the line between the
   * engine's front and back rows (for a garden facing south, z = 0), turned with the engine.
   */
  static float[] centre(Direction facing) {
    return switch (facing) {
      case WEST -> new float[] {1f, 0.5f};
      case NORTH -> new float[] {0.5f, 1f};
      case EAST -> new float[] {0f, 0.5f};
      default -> new float[] {0.5f, 0f};
    };
  }

  @Override
  public void render(TerraGardenCoreEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers,
      int light, int overlay) {
    CropBlock crop = TerraGarden.crop(core.seed());
    if (crop == null) return;
    BlockState state = core.getBlockState();
    // only the formed engine has a pot: unformed, the core is a plain block
    if (state.getValue(TerraGardenCoreBlock.GARDEN) != TerraGardenCoreBlock.Garden.GROWING) return;
    int max = crop.getMaxAge();
    long time = core.getLevel() == null ? 0 : core.getLevel().getGameTime();
    int age = (int) ((time / 5) % (max + 1));
    float[] at = centre(state.getValue(TerraGardenCoreBlock.FACING));
    pose.pushPose();
    pose.translate(at[0], SOIL, at[1]);
    pose.scale(SCALE, SCALE, SCALE);
    pose.translate(-0.5f, 0f, -0.5f);
    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(crop.getStateForAge(age), pose, buffers, light, overlay);
    pose.popPose();
  }

  /** The crop stands above the core's own cell, on the engine. */
  @Override
  public net.minecraft.world.phys.AABB getRenderBoundingBox(TerraGardenCoreEntity core) {
    return new net.minecraft.world.phys.AABB(core.getBlockPos()).inflate(1, 2, 1);
  }
}
