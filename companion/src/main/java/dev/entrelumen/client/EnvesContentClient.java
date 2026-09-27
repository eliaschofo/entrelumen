package dev.entrelumen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.entrelumen.EnvesContent;
import dev.entrelumen.WhiteWither;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The White Wither on the client: the vanilla Wither's model and scale with the ivory texture painted
 * on its UV ({@code art/authoring/draw_white_wither.py}) and its gold glow drawn full-bright; the glow
 * throbs while it winds up a charge. Its skulls wear the same ivory.
 */
public final class EnvesContentClient {
  static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath("entrelumen", "textures/entity/white_wither/white_wither.png");
  static final ResourceLocation GLOW =
      ResourceLocation.fromNamespaceAndPath("entrelumen", "textures/entity/white_wither/white_wither_glow.png");

  private EnvesContentClient() {}

  @EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
  public static final class Registration {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(EnvesContent.WHITE_WITHER.get(), WhiteWitherRenderer::new);
      event.registerEntityRenderer(EnvesContent.SOUR_SKULL.get(), SkullRenderer::new);
    }
  }

  public static final class WhiteWitherRenderer extends MobRenderer<WhiteWither, WitherBossModel<WhiteWither>> {
    public WhiteWitherRenderer(EntityRendererProvider.Context context) {
      super(context, new WitherBossModel<>(context.bakeLayer(ModelLayers.WITHER)), 1.0F);
      addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WhiteWither entity) {
      return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(WhiteWither entity, BlockPos pos) {
      return 15;
    }

    @Override
    protected void scale(WhiteWither entity, PoseStack poseStack, float partialTick) {
      poseStack.scale(2.0F, 2.0F, 2.0F);
    }
  }

  /** The gold of the eyes, teeth and ribs, full-bright; it throbs during a charge's warning. */
  static final class Glow extends RenderLayer<WhiteWither, WitherBossModel<WhiteWither>> {
    Glow(RenderLayerParent<WhiteWither, WitherBossModel<WhiteWither>> parent) {
      super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, WhiteWither entity, float limbSwing,
        float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
      float pulse = entity.phase() == WhiteWither.Phase.TELEGRAPH ? 0.55f + 0.45f * Mth.sin(ageInTicks * 1.2f) : 1f;
      int color = FastColor.ARGB32.colorFromFloat(1f, pulse, pulse, pulse);
      getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, color);
    }
  }

  /** The White Wither's skull, in its ivory. */
  public static final class SkullRenderer extends WitherSkullRenderer {
    public SkullRenderer(EntityRendererProvider.Context context) {
      super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WitherSkull entity) {
      return TEXTURE;
    }
  }
}
