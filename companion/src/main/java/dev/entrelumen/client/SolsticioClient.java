package dev.entrelumen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.entrelumen.Solsticio;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Solsticio's minimal presentation: a white-gold sky (from the biome colours) without clouds,
 * rain or dusk, and optional drifting motes of light around the player. Nothing here changes
 * gameplay; a server without the client still has the same dimension.
 */
public final class SolsticioClient {
  public static final ResourceLocation EFFECTS = ResourceLocation.fromNamespaceAndPath("entrelumen", "solsticio");
  /** Pale gold, the colour of the Heliodor luminous ingot. */
  private static final DustParticleOptions MOTE = new DustParticleOptions(new Vector3f(1.0F, 0.93F, 0.66F), 0.55F);

  private SolsticioClient() {}

  /** Eternal noon: no clouds, no rain, no sunrise tint, fog keeps its colour. */
  static final class Effects extends DimensionSpecialEffects {
    Effects() {
      super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
      return fogColor;
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
  }

  @EventBusSubscriber(modid = "entrelumen", value = Dist.CLIENT)
  public static final class Motes {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || client.player == null || client.isPaused()
          || !level.dimension().equals(Solsticio.LEVEL) || !Solsticio.CLIENT_SPEC.isLoaded()
          || !Solsticio.AMBIENT_MOTES.get()) return;
      ParticleStatus status = client.options.particles().get();
      if (status == ParticleStatus.MINIMAL) return;
      int count = Solsticio.MOTE_DENSITY.get();
      if (status == ParticleStatus.DECREASED) count = (count + 1) / 2;
      RandomSource random = level.random;
      Vec3 eye = client.player.getEyePosition();
      for (int i = 0; i < count; i++) {
        double x = eye.x + (random.nextDouble() - 0.5) * 24.0;
        double y = eye.y + (random.nextDouble() - 0.4) * 12.0;
        double z = eye.z + (random.nextDouble() - 0.5) * 24.0;
        if (random.nextInt(6) == 0)
          level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.004, 0.0);
        else
          level.addParticle(MOTE, x, y, z, 0.0, 0.01, 0.0);
      }
    }
  }
}
