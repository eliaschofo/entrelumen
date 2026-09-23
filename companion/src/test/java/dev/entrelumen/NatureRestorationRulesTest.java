package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NatureRestorationRulesTest {
  private static final ResourceKey<net.minecraft.world.level.Level> OVERWORLD =
      ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

  @Test
  void discIsBoundedNearestFirstAndStable() {
    var columns = NatureRestorationRules.columns();
    assertEquals(197, columns.size());
    assertEquals(new NatureRestorationRules.Column(0, 0), columns.getFirst());
    assertEquals(columns.size(), new HashSet<>(columns).size());
    int radius = NatureRestorationRules.RADIUS;
    for (int i = 0; i < columns.size(); i++) {
      var column = columns.get(i);
      assertTrue(column.distanceSquared() <= radius * radius);
      if (i > 0) {
        var previous = columns.get(i - 1);
        assertTrue(previous.distanceSquared() < column.distanceSquared()
            || previous.distanceSquared() == column.distanceSquared()
                && (previous.dx() < column.dx() || previous.dx() == column.dx() && previous.dz() < column.dz()));
      }
    }
    assertEquals(columns, NatureRestorationRules.columns(radius));
    assertFalse(columns.contains(new NatureRestorationRules.Column(radius, 1)));
  }

  @Test
  void siteDistanceRoundsUpAndRefusesBeyondTheLimit() {
    assertEquals(0, NatureRestorationRules.horizontalDistance(5, 5, 5, 5));
    assertEquals(128, NatureRestorationRules.horizontalDistance(0, 0, 128, 0));
    assertTrue(NatureRestorationRules.withinReach(0, 0, 128, 0));
    assertFalse(NatureRestorationRules.withinReach(0, 0, 91, 91));
    assertFalse(NatureRestorationRules.withinReach(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0));
    assertTrue(NatureRestorationRules.withinWindow(64 + 12, 64));
    assertFalse(NatureRestorationRules.withinWindow(64 - 13, 64));
  }

  @Test
  void loadedAreaCoversEveryWriteAndItsNeighbours() {
    int reach = NatureRestorationRules.writeReach();
    assertEquals(NatureRestorationRules.RADIUS + NatureRestorationRules.TREE_MARGIN, reach);
    for (int center : new int[] {0, 15, 16, -1, -17, 1000}) {
      int[] bounds = NatureRestorationRules.chunkBounds(center, -center);
      assertTrue(bounds[0] * 16 <= center - reach - 1 && (bounds[2] + 1) * 16 - 1 >= center + reach + 1);
      assertTrue(bounds[1] * 16 <= -center - reach - 1 && (bounds[3] + 1) * 16 - 1 >= -center + reach + 1);
      assertTrue(bounds[2] - bounds[0] <= 3 && bounds[3] - bounds[1] <= 3);
    }
  }

  @Test
  void columnChoiceIsDeterministicPerWorldAndPurpose() {
    long seed = 12345L;
    int chosen = 0;
    for (int x = -64; x < 64; x++)
      for (int z = -64; z < 64; z++) {
        double unit = NatureRestorationRules.unit(seed, x, z, NatureRestorationRules.FLORA_SALT);
        assertTrue(unit >= 0 && unit < 1);
        assertEquals(unit, NatureRestorationRules.unit(seed, x, z, NatureRestorationRules.FLORA_SALT));
        if (NatureRestorationRules.selected(seed, x, z, NatureRestorationRules.FLORA_SALT,
            NatureRestorationRules.FLORA_DENSITY)) chosen++;
      }
    double share = chosen / (128.0 * 128.0);
    assertTrue(Math.abs(share - NatureRestorationRules.FLORA_DENSITY) < 0.02, "share " + share);
    assertNotEquals(NatureRestorationRules.unit(seed, 3, 4, NatureRestorationRules.TREE_SALT),
        NatureRestorationRules.unit(seed, 3, 4, NatureRestorationRules.FLORA_SALT));
    assertNotEquals(NatureRestorationRules.unit(seed, 3, 4, NatureRestorationRules.TREE_SALT),
        NatureRestorationRules.unit(seed + 1, 3, 4, NatureRestorationRules.TREE_SALT));
    assertFalse(NatureRestorationRules.selected(seed, 3, 4, NatureRestorationRules.TREE_SALT, 0));
  }

  @Test
  void treeDensityFollowsTheBiomeCountPerChunk() {
    assertEquals(0, NatureRestorationRules.treeProbability(0));
    assertEquals(0, NatureRestorationRules.treeProbability(Double.NaN));
    assertEquals(10 / 256.0, NatureRestorationRules.treeProbability(10), 1e-12);
    assertEquals(1, NatureRestorationRules.treeProbability(400));
    assertEquals(-1, NatureRestorationRules.weightedIndex(new double[] {0, 0}, 0.5));
    assertEquals(0, NatureRestorationRules.weightedIndex(new double[] {3, 1}, 0.0));
    assertEquals(0, NatureRestorationRules.weightedIndex(new double[] {3, 1}, 0.74));
    assertEquals(1, NatureRestorationRules.weightedIndex(new double[] {3, 1}, 0.76));
    assertEquals(1, NatureRestorationRules.weightedIndex(new double[] {0, 1}, 0.0));
    assertEquals(1, NatureRestorationRules.weightedIndex(new double[] {3, 1}, 0.9999999));
  }

  @Test
  void dominantCoverPrefersGrassOnTiesAndBareSites() {
    assertEquals(0, NatureRestorationRules.dominantCover(0, 0, 0));
    assertEquals(0, NatureRestorationRules.dominantCover(4, 4, 4));
    assertEquals(1, NatureRestorationRules.dominantCover(2, 5, 5));
    assertEquals(2, NatureRestorationRules.dominantCover(2, 3, 5));
    assertEquals(0, NatureRestorationRules.dominantCover(9, 3, 5));
  }

  @Test
  void sitesSurviveSaveLoadAndRepeatedMarksStayClean() {
    var data = new NatureRestorationData();
    UUID party = UUID.randomUUID(), personal = UUID.randomUUID();
    var site = GlobalPos.of(OVERWORLD, new BlockPos(10, 64, -20));
    assertTrue(data.mark(party, site));
    assertTrue(data.isDirty());
    data.setDirty(false);
    assertFalse(data.mark(party, GlobalPos.of(OVERWORLD, new BlockPos(10, 64, -20))));
    assertFalse(data.isDirty());
    assertTrue(data.mark(personal, GlobalPos.of(OVERWORLD, new BlockPos(-5, 70, 3))));
    var loaded = NatureRestorationData.load(data.save(new CompoundTag(), null), null);
    assertEquals(site, loaded.site(party));
    assertEquals(new BlockPos(-5, 70, 3), loaded.site(personal).pos());
    assertNull(loaded.site(UUID.randomUUID()));
    assertEquals(2, loaded.size());
  }

  @Test
  void malformedEntriesAreSkippedAndFutureSchemasRefused() {
    var tag = new CompoundTag();
    tag.putInt("version", NatureRestorationData.VERSION);
    var list = new ListTag();
    var noTeam = new CompoundTag();
    noTeam.putString("dimension", "minecraft:overworld");
    list.add(noTeam);
    var badDimension = new CompoundTag();
    badDimension.putUUID("campaign", UUID.randomUUID());
    badDimension.putString("dimension", "Not A Key");
    badDimension.putInt("x", 1);
    badDimension.putInt("y", 2);
    badDimension.putInt("z", 3);
    list.add(badDimension);
    var missingY = new CompoundTag();
    missingY.putUUID("campaign", UUID.randomUUID());
    missingY.putString("dimension", "minecraft:overworld");
    missingY.putInt("x", 1);
    missingY.putInt("z", 3);
    list.add(missingY);
    tag.put("sites", list);
    assertEquals(0, NatureRestorationData.load(tag, null).size());
    tag.putInt("version", NatureRestorationData.VERSION + 1);
    assertThrows(IllegalStateException.class, () -> NatureRestorationData.load(tag, null));
  }
}
