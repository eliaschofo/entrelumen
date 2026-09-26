package dev.entrelumen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.entrelumen.Enves;
import dev.entrelumen.EnvesLayout;
import dev.entrelumen.EnvesNetwork;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * The Envés on the client: its dark, roofed sky, the fog minimap in the corner (floor, seals and
 * falls under it), and JourneyMap switched off while inside. JourneyMap 6.0.6 runs only on the
 * client here, so its own server permissions never reach it; its API implementation
 * ({@code journeymap.api.client.impl.ClientAPI.INSTANCE}: {@code disableFeature} for every map type
 * and {@code toggleMinimap}) is reached by reflection and restored on the way out
 * ({@code FeatureManager.reset()}). FTB Chunks is switched off from the server (its map stage).
 */
public final class EnvesClient {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final ResourceLocation EFFECTS = ResourceLocation.fromNamespaceAndPath("entrelumen", "enves");
  static final ResourceLocation HUD = ResourceLocation.fromNamespaceAndPath("entrelumen", "enves_map");
  static final int HUD_CELL = 7, HUD_BORDER = 4, MARGIN = 4;

  private EnvesClient() {}

  /** No sky, no clouds, no rain, dark fog. */
  static final class Effects extends DimensionSpecialEffects {
    Effects() {
      super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
      return fogColor.scale(0.3);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
      return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
      return null;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture,
        double camX, double camY, double camZ) {
      return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
      return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX,
        double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
      return true;
    }

  }

  @EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
  public static final class Registration {
    @SubscribeEvent
    public static void effects(RegisterDimensionSpecialEffectsEvent event) {
      event.register(EFFECTS, new Effects());
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent event) {
      event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, HUD, EnvesClient::renderHud);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
        EnvesNetwork.hudReceiver = EnvesClientState::receive;
        EnvesNetwork.mapReceiver = EnvesClientState::receive;
        EnvesNetwork.gateReceiver = EnvesGateScreen::receive;
      });
    }
  }

  static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
    var minecraft = Minecraft.getInstance();
    if (!EnvesClientState.inside() || minecraft.getDebugOverlay().showDebugScreen() || minecraft.screen instanceof EnvesMapScreen)
      return;
    var hud = EnvesClientState.hud();
    int size = EnvesLayout.W * HUD_CELL + 2 * HUD_BORDER;
    if (EnvesClientState.map() != null) EnvesMapRenderer.draw(graphics, MARGIN, MARGIN, HUD_CELL, HUD_BORDER, false);
    int y = MARGIN + size + 3;
    var font = minecraft.font;
    for (Component line : lines(hud)) {
      graphics.drawString(font, line, MARGIN + 1, y, 0xFFF1E3C1, true);
      y += font.lineHeight + 1;
    }
  }

  /** Floor, seals and falls, as the HUD and the big map write them. */
  static java.util.List<Component> lines(EnvesNetwork.Hud hud) {
    java.util.List<Component> out = new java.util.ArrayList<>();
    out.add(Component.translatable("entrelumen.enves.floor." + hud.depth()));
    if (hud.depth() >= 1 && hud.depth() < EnvesLayout.BOSS_DEPTH && hud.sealsTotal() > 0)
      out.add(Component.translatable(hud.stairOpen() ? "entrelumen.enves.hud.stair_open" : "entrelumen.enves.hud.seals",
          hud.sealsLit(), hud.sealsTotal()));
    out.add(Component.translatable("entrelumen.enves.hud.falls", hud.poolLeft(), hud.poolTotal()));
    return out;
  }

  // ---- JourneyMap ---------------------------------------------------------------------------

  @EventBusSubscriber(modid = "entrelumen", value = Dist.CLIENT)
  public static final class Ticks {
    private static boolean inside;
    private static int counter;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
      var minecraft = Minecraft.getInstance();
      boolean now = minecraft.level != null && minecraft.level.dimension().equals(Enves.LEVEL);
      if (!now && minecraft.level != null && EnvesClientState.hud() != null) EnvesClientState.clear();
      if (now && (!inside || ++counter % 40 == 0)) JourneyMap.off();
      if (!now && inside) JourneyMap.restore();
      inside = now;
    }
  }

  /** JourneyMap's client API by reflection; any failure leaves JourneyMap as it is. */
  static final class JourneyMap {
    private static Object api;
    private static Method disable, toggleMinimap, minimapEnabled, reset;
    private static Object[] mapTypes;
    private static Object featureManager;
    private static boolean resolved, off;
    private static Boolean minimapBefore;

    private static boolean resolve() {
      if (resolved) return api != null;
      resolved = true;
      if (!ModList.get().isLoaded("journeymap")) return false;
      try {
        Class<?> clientApi = Class.forName("journeymap.api.client.impl.ClientAPI");
        api = clientApi.getField("INSTANCE").get(null);
        Class<?> mapType = Class.forName("journeymap.api.v2.client.display.Context$MapType");
        mapTypes = mapType.getEnumConstants();
        disable = clientApi.getMethod("disableFeature", net.minecraft.resources.ResourceKey.class, mapType, boolean.class);
        toggleMinimap = clientApi.getMethod("toggleMinimap", boolean.class);
        minimapEnabled = clientApi.getMethod("minimapEnabled");
        Class<?> features = Class.forName("journeymap.client.feature.FeatureManager");
        featureManager = features.getMethod("getInstance").invoke(null);
        reset = features.getMethod("reset");
        return true;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
        LOGGER.warn("JourneyMap is loaded but its client API was not found; it is not switched off in the Envés", e);
        api = null;
        return false;
      }
    }

    static void off() {
      if (!resolve()) return;
      try {
        if (!off) minimapBefore = (Boolean) minimapEnabled.invoke(api);
        for (Object type : mapTypes) disable.invoke(api, Enves.LEVEL, type, false);
        toggleMinimap.invoke(api, false);
        off = true;
      } catch (ReflectiveOperationException | RuntimeException e) {
        LOGGER.debug("Could not switch JourneyMap off", e);
      }
    }

    static void restore() {
      if (!off || api == null) return;
      off = false;
      try {
        reset.invoke(featureManager);
        toggleMinimap.invoke(api, minimapBefore == null || minimapBefore);
      } catch (ReflectiveOperationException | RuntimeException e) {
        LOGGER.debug("Could not restore JourneyMap", e);
      }
    }
  }
}
