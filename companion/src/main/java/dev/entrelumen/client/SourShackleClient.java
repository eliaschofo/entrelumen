package dev.entrelumen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.entrelumen.CuriosCompat;
import dev.entrelumen.SourShackle;
import dev.entrelumen.SourShackleRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MapItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Quaternionf;

/**
 * The Sour Shackle's cuff on the client: an ivory band round the wrist of the main arm with five marks
 * floating in a small circle over its front, lit one per mark (full-bright), all five for half a second
 * when they burst, then all dark with the band dimmed during the cooldown
 * ({@code art/authoring/draw_enves_curio.py} draws the texture, one texel per unit).
 *
 * <ul>
 *   <li><b>First person, empty hand</b> ({@link RenderArmEvent}): on the arm vanilla is about to draw,
 *       in the pose {@code PlayerRenderer.renderHand} gives it (pivot 5 units out and 2 down, rolled
 *       by the idle bob of an age-0 {@code setupAnim}, no pitch).</li>
 *   <li><b>First person, holding something</b> ({@link RenderHandEvent}): vanilla draws no arm, and the
 *       real wrist would sit below the screen's edge. The cuff stays where the empty hand's wrist is, so
 *       the marks are always read in the same place, and moves with the held item (equip and swing, from
 *       {@code ItemInHandRenderer}), pivoting on the item's anchor.</li>
 *   <li><b>Third person</b>: Curios' renderer ({@link SourShackleCurioRenderer}), on the arm part of the
 *       wearer's model, so other players see it and Curios' visibility toggle hides it.</li>
 * </ul>
 * Nothing is allocated per frame by this class: the model is baked once, rotations reuse fields, and
 * the marks come from the entity's {@link SourShackle#VIEW} attachment, updated only by packets.
 */
public final class SourShackleClient {
  static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath("entrelumen", "textures/entity/sour_shackle/cuff.png");

  private SourShackleClient() {}

  // ---- The model: baked once ----------------------------------------------------------------

  private static ModelPart band, bandCool;
  /** The marks by position on their circle and by kind. */
  private static final ModelPart[] MARK_LIT = new ModelPart[SourShackleRules.MARKS],
      MARK_UNLIT = new ModelPart[SourShackleRules.MARKS], MARK_COOL = new ModelPart[SourShackleRules.MARKS];
  /** The circle of marks: radius, centre on the wrist (y), and how far out of the band's front they float. */
  static final float CIRCLE = 1.2F, CIRCLE_Y = 7.5F, FLOAT = 0.25F, MARK = 0.5F;

  /**
   * The band 5 x 3 x 5 round an arm centred on x = 0 (wrist at y 6..9), and the five marks: 0.5-unit cubes on a
   * circle of radius {@link #CIRCLE} over the band's front (-z, the face first person shows even behind a held
   * sword), floating {@link #FLOAT} off it, the first at the top and the rest going round toward the outside of
   * the right arm.
   */
  static LayerDefinition layer() {
    var mesh = new MeshDefinition();
    var root = mesh.getRoot();
    root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, 6F, -2.5F, 5, 3, 5), PartPose.ZERO);
    root.addOrReplaceChild("band_cool", CubeListBuilder.create().texOffs(0, 8).addBox(-2.5F, 6F, -2.5F, 5, 3, 5), PartPose.ZERO);
    String[] kinds = {"lit", "unlit", "cool"};
    int[] u = {24, 28, 32};
    for (int i = 0; i < SourShackleRules.MARKS; i++) {
      double angle = 2 * Math.PI * i / SourShackleRules.MARKS;
      float x = (float) (-CIRCLE * Math.sin(angle)), y = (float) (CIRCLE_Y - CIRCLE * Math.cos(angle));
      for (int k = 0; k < 3; k++)
        root.addOrReplaceChild("mark_" + kinds[k] + "_" + i, CubeListBuilder.create().texOffs(u[k], 0)
            .addBox(x - MARK / 2, y - MARK / 2, -2.5F - FLOAT - MARK, MARK, MARK, MARK), PartPose.ZERO);
    }
    return LayerDefinition.create(mesh, 64, 16);
  }

  private static void bake() {
    if (band != null) return;
    ModelPart root = layer().bakeRoot();
    for (int i = 0; i < SourShackleRules.MARKS; i++) {
      MARK_LIT[i] = root.getChild("mark_lit_" + i);
      MARK_UNLIT[i] = root.getChild("mark_unlit_" + i);
      MARK_COOL[i] = root.getChild("mark_cool_" + i);
    }
    bandCool = root.getChild("band_cool");
    band = root.getChild("band");
  }

  /** The arm box's centre on x, in units: wide arms are 4 wide, slim ones 3, each from its own inner edge. */
  static float armCentre(LivingEntity entity, HumanoidArm arm) {
    boolean slim = entity instanceof AbstractClientPlayer player && player.getSkin().model() == PlayerSkin.Model.SLIM;
    float centre = slim ? 0.5F : 1.0F;
    return arm == HumanoidArm.RIGHT ? -centre : centre;
  }

  /** The marks a client knows for {@code entity}. */
  static SourShackle.View view(LivingEntity entity) {
    return entity.getData(SourShackle.VIEW.get());
  }

  /**
   * Draws the cuff in arm-part space (the pose at the arm's pivot): the band, then the circle of marks, the
   * first {@code marks} lit. On the left arm the circle is walked the other way, so it mirrors the right.
   */
  static void draw(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidArm arm) {
    bake();
    SourShackle.View view = view(entity);
    long now = entity.level().getGameTime();
    boolean flash = view.flashing(now);
    boolean cooling = !flash && view.cooling(now);
    int lit = flash ? SourShackleRules.MARKS : view.marks();
    VertexConsumer out = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
    pose.translate(armCentre(entity, arm) / 16F, 0F, 0F);
    (cooling ? bandCool : band).render(pose, out, light, OverlayTexture.NO_OVERLAY);
    boolean right = arm == HumanoidArm.RIGHT;
    for (int i = 0; i < SourShackleRules.MARKS; i++) {
      int at = right ? i : (SourShackleRules.MARKS - i) % SourShackleRules.MARKS;
      if (cooling) MARK_COOL[at].render(pose, out, light, OverlayTexture.NO_OVERLAY);
      else if (i < lit) MARK_LIT[at].render(pose, out, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
      else MARK_UNLIT[at].render(pose, out, light, OverlayTexture.NO_OVERLAY);
    }
  }

  // ---- First person -------------------------------------------------------------------------

  private static final Quaternionf SCRATCH = new Quaternionf();
  private static final float DEG = Mth.DEG_TO_RAD;

  /** The arm part's first-person pose: {@code renderHand} sets up the model at age 0, so the idle bob rolls it 0.1 rad. */
  static void armPartPose(PoseStack pose, int side) {
    pose.translate(-side * 5F / 16F, 2F / 16F, 0F);
    pose.mulPose(SCRATCH.rotationZ(side * 0.1F));
  }

  /** {@code ItemInHandRenderer.renderPlayerArm} at rest (no swing, fully equipped), up to where it draws the arm. */
  static void emptyArmRest(PoseStack pose, int side) {
    pose.translate(side * 0.64000005F, -0.6F, -0.71999997F);
    pose.mulPose(SCRATCH.rotationY(side * 45F * DEG));
    pose.translate(side * -1.0F, 3.6F, 3.5F);
    pose.mulPose(SCRATCH.rotationZ(side * 120F * DEG));
    pose.mulPose(SCRATCH.rotationX(200F * DEG));
    pose.mulPose(SCRATCH.rotationY(side * -135F * DEG));
    pose.translate(side * 5.6F, 0.0F, 0.0F);
  }

  @EventBusSubscriber(modid = "entrelumen", value = Dist.CLIENT)
  public static final class FirstPerson {
    private FirstPerson() {}

    /** The empty main hand (and the hands that hold a map): vanilla draws the arm, the cuff goes round its wrist. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void arm(RenderArmEvent event) {
      AbstractClientPlayer player = event.getPlayer();
      if (event.getArm() != player.getMainArm() || !view(player).worn()) return;
      int side = event.getArm() == HumanoidArm.RIGHT ? 1 : -1;
      PoseStack pose = event.getPoseStack();
      pose.pushPose();
      armPartPose(pose, side);
      draw(pose, event.getMultiBufferSource(), event.getPackedLight(), player, event.getArm());
      pose.popPose();
    }

    /** Anything but a map in the main hand: no arm is drawn, so the cuff rides with the item under its grip. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hand(RenderHandEvent event) {
      if (event.getHand() != InteractionHand.MAIN_HAND || event.getItemStack().isEmpty()
          || event.getItemStack().getItem() instanceof MapItem) return;
      var player = Minecraft.getInstance().player;
      if (player == null || player.isScoping() || player.isInvisible() || !view(player).worn()) return;
      HumanoidArm arm = player.getMainArm();
      int side = arm == HumanoidArm.RIGHT ? 1 : -1;
      PoseStack pose = event.getPoseStack();
      pose.pushPose();
      // ItemInHandRenderer.applyItemArmTransform and applyItemArmAttackTransform, then back off the item's anchor,
      // so at rest this is the empty arm's pose and in motion it swings and lowers with the item.
      float equip = event.getEquipProgress(), swing = event.getSwingProgress();
      pose.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
      float f = Mth.sin(swing * swing * Mth.PI);
      pose.mulPose(SCRATCH.rotationY(side * (45F + f * -20F) * DEG));
      float f1 = Mth.sin(Mth.sqrt(swing) * Mth.PI);
      pose.mulPose(SCRATCH.rotationZ(side * f1 * -20F * DEG));
      pose.mulPose(SCRATCH.rotationX(f1 * -80F * DEG));
      pose.mulPose(SCRATCH.rotationY(side * -45F * DEG));
      pose.translate(-side * 0.56F, 0.52F, 0.72F);
      emptyArmRest(pose, side);
      armPartPose(pose, side);
      draw(pose, event.getMultiBufferSource(), event.getPackedLight(), player, arm);
      pose.popPose();
    }
  }

  // ---- Setup --------------------------------------------------------------------------------

  @EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
  public static final class Setup {
    private Setup() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> SourShackle.clientReceiver = marks -> {
        var level = Minecraft.getInstance().level;
        if (level != null) SourShackle.receive(level, marks);
      });
    }

    /** Curios instantiates its renderers in its own {@code AddLayers} listener; this one runs in an earlier phase. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void layers(EntityRenderersEvent.AddLayers event) {
      if (CuriosCompat.loaded()) SourShackleCurioRenderer.register(SourShackle.ITEM.get());
    }
  }
}
