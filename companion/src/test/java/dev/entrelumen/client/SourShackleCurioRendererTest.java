package dev.entrelumen.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

/**
 * The third-person cuff binds to Curios' real {@code ICurioRenderer} without linking against it. Runs
 * against the pinned Curios JAR when build.gradle found it through catalog/local-paths.json (it is then
 * on the test runtime classpath only); skipped elsewhere.
 */
class SourShackleCurioRendererTest {
  @Test
  void bindsToTheRealCurioRenderer() throws Throwable {
    Class<?> renderer, context;
    try {
      renderer = Class.forName(SourShackleCurioRenderer.RENDERER);
      context = Class.forName(SourShackleCurioRenderer.SLOT_CONTEXT);
    } catch (ClassNotFoundException absent) {
      assumeTrue(false, "the pinned Curios JAR is not on this machine");
      return;
    }
    Object instance = SourShackleCurioRenderer.bind(renderer, context);
    assertTrue(renderer.isInstance(instance));
    // Curios' abstract method is the one bound, with the signature expected.
    var render = renderer.getMethod("render", SourShackleCurioRenderer.renderType(context).parameterArray());
    assertEquals(void.class, render.getReturnType());
    // Called with nothing to draw, it returns without touching the client.
    var handle = MethodHandles.publicLookup().unreflect(render);
    handle.invoke(instance, null, null, null, null, null, 0, 0F, 0F, 0F, 0F, 0F, 0F);
    // And the registry has the entry point it calls.
    var registry = Class.forName(SourShackleCurioRenderer.REGISTRY);
    assertNotNull(MethodHandles.publicLookup().findStatic(registry, "register",
        MethodType.methodType(void.class, net.minecraft.world.item.Item.class, java.util.function.Supplier.class)));
  }
}
