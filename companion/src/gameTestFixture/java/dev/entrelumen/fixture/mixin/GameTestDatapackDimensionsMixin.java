package dev.entrelumen.fixture.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla's GameTest server bakes its world with an empty dimension registry, so datapack
 * dimensions such as {@code entrelumen:solsticio} never load in isolated GameTests. This fixture
 * passes the datapack dimensions exactly as a normal server does. Test runs only; never shipped.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestDatapackDimensionsMixin {
  @WrapOperation(method = "*", require = 1, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
  private static WorldDimensions.Complete entrelumen$withDatapackDimensions(WorldDimensions dimensions,
      Registry<LevelStem> empty, Operation<WorldDimensions.Complete> original,
      @Local(argsOnly = true) WorldLoader.DataLoadContext context) {
    return original.call(dimensions, context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
  }
}
