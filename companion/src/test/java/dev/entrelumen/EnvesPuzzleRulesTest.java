package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import dev.entrelumen.EnvesPuzzleRules.Blessing;
import dev.entrelumen.EnvesPuzzleRules.SealVariant;
import dev.entrelumen.EnvesPuzzleRules.VaultPuzzle;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The seals', vaults' and shrines' rules: variants, and every puzzle solvable, never pre-solved. */
class EnvesPuzzleRulesTest {
  @Test
  void aFloorsSealsNeverShareAVariantAndThreeShowAllThree() {
    for (long seed = 0; seed < 300; seed++) {
      var two = EnvesPuzzleRules.sealVariants(seed, 1, List.of(10, 20));
      assertNotEquals(two.get(10), two.get(20), "seed " + seed);
      var three = EnvesPuzzleRules.sealVariants(seed, 3, List.of(1, 2, 3));
      assertEquals(EnumSet.allOf(SealVariant.class), EnumSet.copyOf(three.values()), "seed " + seed);
      assertEquals(three, EnvesPuzzleRules.sealVariants(seed, 3, List.of(1, 2, 3)), "a restart rebuilds the same");
    }
  }

  @Test
  void everyVaultLockAndBlessingTurnsUp() {
    Set<VaultPuzzle> vaults = EnumSet.noneOf(VaultPuzzle.class);
    Set<Blessing> blessings = EnumSet.noneOf(Blessing.class);
    Set<EnvesPuzzleRules.SealPuzzle> seals = EnumSet.noneOf(EnvesPuzzleRules.SealPuzzle.class);
    int offerings = 0;
    for (long seed = 0; seed < 400; seed++) {
      VaultPuzzle kind = EnvesPuzzleRules.vaultPuzzle(seed, 2, 30);
      vaults.add(kind);
      if (kind == VaultPuzzle.OFFERING) offerings++;
      blessings.add(EnvesPuzzleRules.blessing(seed, 2));
      seals.add(EnvesPuzzleRules.sealPuzzle(seed, 2, 40));
    }
    assertEquals(EnumSet.allOf(VaultPuzzle.class), vaults);
    assertEquals(EnumSet.allOf(Blessing.class), blessings);
    assertEquals(EnumSet.allOf(EnvesPuzzleRules.SealPuzzle.class), seals);
    assertTrue(offerings < 80, "the shard toll is the rare lock: " + offerings + " of 400");
    Set<String> ids = new HashSet<>();
    for (Blessing blessing : Blessing.values()) {
      assertTrue(ids.add(blessing.id()));
      assertEquals(blessing, Blessing.byId(blessing.id()));
    }
  }

  @Test
  void theBraziersEchoRepeatsNothingTwiceRunningAndAMissStartsOver() {
    Random random = new Random(3);
    for (int length = 3; length <= 6; length++)
      for (int i = 0; i < 50; i++) {
        int[] echo = EnvesPuzzleRules.echo(random, 3, length);
        assertEquals(length, echo.length);
        for (int k = 1; k < length; k++) assertNotEquals(echo[k - 1], echo[k]);
      }
    int[] sequence = {2, 0, 1, 0};
    int p = 0;
    for (int brazier : sequence) p = EnvesPuzzleRules.echoStep(sequence, p, brazier);
    assertEquals(4, p, "the right order solves it");
    assertEquals(3, EnvesPuzzleRules.echoStep(sequence, 2, 1), "the third right one");
    assertEquals(0, EnvesPuzzleRules.echoStep(sequence, 1, 1), "a miss drops the progress");
    assertEquals(1, EnvesPuzzleRules.echoStep(sequence, 3, 2), "a miss that is the first brazier counts as a new start");
  }

  @Test
  void glyphStonesOpenOnlyWhenTheyReadTheClueBackwards() {
    Random random = new Random(5);
    for (int i = 0; i < 500; i++) {
      int[] code = EnvesPuzzleRules.glyphCode(random);
      assertNotEquals(code[0], code[2], "never a palindrome: backwards must differ");
      int[] start = EnvesPuzzleRules.glyphStart(random, code);
      assertFalse(EnvesPuzzleRules.glyphsOpen(start, code));
      assertFalse(EnvesPuzzleRules.glyphsLiteral(start, code));
      assertFalse(EnvesPuzzleRules.glyphsOpen(code, code), "copying the clue as seen does not open it");
      int[] reversed = {code[2], code[1], code[0]};
      assertTrue(EnvesPuzzleRules.glyphsOpen(reversed, code));
    }
  }

  @Test
  void leverBoardsNeedTwoPullsAtLeastAndCanAlwaysBeSolved() {
    Random random = new Random(9);
    for (int[] shape : new int[][] {{6, 3}, {4, 4}, {3, 3}})
      for (int i = 0; i < 200; i++) {
        var board = EnvesPuzzleRules.levers(random, shape[0], shape[1]);
        assertEquals(shape[0], board.masks().length);
        for (int mask : board.masks()) assertTrue(mask != 0 && Integer.bitCount(mask) < shape[1]);
        assertNotEquals(board.full(), board.start());
        int fewest = board.fewest(board.start());
        assertTrue(fewest >= 2, "one pull must not solve it: " + fewest);
      }
  }

  @Test
  void theSourLightReachesTheSealOnlyWhenTheMirrorsAreSet() {
    Random random = new Random(13);
    for (int turns = 1; turns <= 3; turns++)
      for (int i = 0; i < 300; i++) {
        var puzzle = EnvesPuzzleRules.mirrors(random, turns);
        assertTrue(EnvesPuzzleRules.trace(puzzle, puzzle.solution()).reached(), "the answer lights the seal");
        assertFalse(EnvesPuzzleRules.trace(puzzle, puzzle.start()).reached(), "it never starts solved");
        int onPath = 0;
        for (boolean b : puzzle.onPath()) if (b) onPath++;
        assertEquals(turns, onPath, "exactly the turns it asks for");
        assertTrue(puzzle.mirrors().size() >= turns + 2, "and some mirrors that lead nowhere");
        assertTrue(EnvesPuzzleRules.latticePoint(puzzle.font()[0], puzzle.font()[1]));
        Set<Long> seen = new HashSet<>();
        for (int[] m : puzzle.mirrors()) {
          assertTrue(EnvesPuzzleRules.latticePoint(m[0], m[1]), Arrays.toString(m));
          assertTrue(seen.add(((long) m[0] << 32) ^ (m[1] & 0xFFFFFFFFL)), "two mirrors on one spot");
          assertFalse(m[0] == puzzle.font()[0] && m[1] == puzzle.font()[1], "a mirror on the font");
        }
        for (int[] cell : EnvesPuzzleRules.trace(puzzle, puzzle.solution()).cells())
          assertTrue(EnvesPuzzleRules.openFloor(cell[0], cell[1]), "the light leaves the room's floor");
      }
  }

  @Test
  void mirrorsTurnTheLightNinetyDegrees() {
    assertArrayEquals(new int[] {0, -1}, EnvesPuzzleRules.reflect(new int[] {1, 0}, 0), "'/' sends east light north");
    assertArrayEquals(new int[] {0, 1}, EnvesPuzzleRules.reflect(new int[] {1, 0}, 1), "'\\' sends east light south");
    assertArrayEquals(new int[] {1, 0}, EnvesPuzzleRules.reflect(new int[] {0, -1}, 0), "'/' sends north light east");
    assertArrayEquals(new int[] {-1, 0}, EnvesPuzzleRules.reflect(new int[] {0, -1}, 1), "'\\' sends north light west");
    assertFalse(EnvesPuzzleRules.latticePoint(6, 6), "the corners are outside the cross");
    assertFalse(EnvesPuzzleRules.latticePoint(0, 0), "the seal is no lattice point");
    assertTrue(EnvesPuzzleRules.openFloor(8, 3) && !EnvesPuzzleRules.openFloor(8, 4) && !EnvesPuzzleRules.openFloor(9, 0));
  }

  @Test
  void offeringChoicesAreHonouredOrRefused() {
    assertEquals(1, EnvesOffering.choose(List.of(true, true), 1), "the one asked for");
    assertEquals(-1, EnvesOffering.choose(List.of(true, false), 1), "never another than the one asked for");
    assertEquals(1, EnvesOffering.choose(List.of(false, true), -1), "no preference: the first carried");
    assertEquals(-1, EnvesOffering.choose(List.of(false, false), -1));
  }
}
