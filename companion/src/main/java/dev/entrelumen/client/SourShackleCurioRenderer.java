package dev.entrelumen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * The cuff in third person, through Curios' own hook ({@code ICurioRenderer}, registered with
 * {@code CuriosRendererRegistry}), without linking against Curios: the companion never compiles
 * against it. {@link #bind} makes a real implementation of Curios' interface with
 * {@link LambdaMetafactory} (the interface has one abstract method), whose body is {@link #render}; the
 * slot context reaches it as an {@code Object} and its entity is read through a method handle. No
 * reflection or boxing happens per frame. Loaded only when Curios is.
 */
public final class SourShackleCurioRenderer {
  static final String RENDERER = "top.theillusivec4.curios.api.client.ICurioRenderer";
  static final String REGISTRY = "top.theillusivec4.curios.api.client.CuriosRendererRegistry";
  static final String SLOT_CONTEXT = "top.theillusivec4.curios.api.SlotContext";

  private SourShackleCurioRenderer() {}

  /** {@code SlotContext.entity()}, typed {@code (Object) -> LivingEntity}; null when Curios' API moved. */
  private static final MethodHandle ENTITY = entityHandle();

  private static MethodHandle entityHandle() {
    try {
      Class<?> context = Class.forName(SLOT_CONTEXT, false, SourShackleCurioRenderer.class.getClassLoader());
      return MethodHandles.publicLookup().findVirtual(context, "entity", MethodType.methodType(LivingEntity.class))
          .asType(MethodType.methodType(LivingEntity.class, Object.class));
    } catch (ReflectiveOperationException | LinkageError e) {
      return null;
    }
  }

  /** The erased signature of {@code ICurioRenderer.render}, with the slot context as the class Curios gives. */
  static MethodType renderType(Class<?> slotContext) {
    return MethodType.methodType(void.class, ItemStack.class, slotContext, PoseStack.class, RenderLayerParent.class,
        MultiBufferSource.class, int.class, float.class, float.class, float.class, float.class, float.class, float.class);
  }

  /** An instance of {@code rendererInterface} whose single method runs {@link #render}. */
  static Object bind(Class<?> rendererInterface, Class<?> slotContext) throws Throwable {
    var lookup = MethodHandles.lookup();
    MethodHandle impl = lookup.findStatic(SourShackleCurioRenderer.class, "render", renderType(Object.class));
    MethodType sam = renderType(slotContext);
    var site = LambdaMetafactory.metafactory(lookup, "render", MethodType.methodType(rendererInterface), sam, impl, sam);
    return site.getTarget().invoke();
  }

  /** Registers the cuff's renderer for {@code item}; logs and gives up if Curios' API is not what it expects. */
  static void register(Item item) {
    try {
      ClassLoader loader = SourShackleCurioRenderer.class.getClassLoader();
      Class<?> renderer = Class.forName(RENDERER, false, loader);
      Object instance = bind(renderer, Class.forName(SLOT_CONTEXT, false, loader));
      MethodHandle register = MethodHandles.publicLookup().findStatic(Class.forName(REGISTRY, false, loader), "register",
          MethodType.methodType(void.class, Item.class, Supplier.class));
      Supplier<Object> supplier = () -> instance;
      register.invoke(item, supplier);
    } catch (Throwable e) {
      com.mojang.logging.LogUtils.getLogger().warn("ENTRELUMEN Sour Shackle: Curios renderer unavailable: {}", e.toString());
    }
  }

  private static final Quaternionf SCRATCH = new Quaternionf();

  /** {@code ICurioRenderer.render}: the cuff on the main arm of the wearer's humanoid model, as the model is posed now. */
  @SuppressWarnings("rawtypes")
  static void render(ItemStack stack, Object slotContext, PoseStack pose, RenderLayerParent parent, MultiBufferSource buffers,
      int light, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
    if (ENTITY == null || slotContext == null || parent == null) return;
    LivingEntity entity;
    try {
      entity = (LivingEntity) ENTITY.invokeExact(slotContext);
    } catch (Throwable e) {
      return;
    }
    if (entity == null || !(parent.getModel() instanceof HumanoidModel<?> model)) return;
    HumanoidArm arm = entity.getMainArm();
    ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
    if (!part.visible) return;
    pose.pushPose();
    // ModelPart.translateAndRotate, without its per-call quaternion.
    pose.translate(part.x / 16F, part.y / 16F, part.z / 16F);
    if (part.xRot != 0F || part.yRot != 0F || part.zRot != 0F) pose.mulPose(SCRATCH.rotationZYX(part.zRot, part.yRot, part.xRot));
    if (part.xScale != 1F || part.yScale != 1F || part.zScale != 1F) pose.scale(part.xScale, part.yScale, part.zScale);
    SourShackleClient.draw(pose, buffers, light, entity, arm);
    pose.popPose();
  }
}
