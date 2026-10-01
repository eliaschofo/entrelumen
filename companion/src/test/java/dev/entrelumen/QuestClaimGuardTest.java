package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class QuestClaimGuardTest {
  @Test
  void partyEntryTimesRoundTripAndDefaultToZero() {
    var since = new QuestClaimGuard.PartySince();
    UUID player = UUID.randomUUID(), other = UUID.randomUUID();
    assertEquals(0L, since.get(player), "An unrecorded player must read as joined at 0 (strict side)");
    since.put(player, 1_727_000_000_000L);
    assertTrue(since.isDirty());

    CompoundTag saved = since.save(new CompoundTag(), null);
    saved.putLong("not-a-uuid", 5L);
    var loaded = QuestClaimGuard.PartySince.load(saved, null);
    assertEquals(1_727_000_000_000L, loaded.get(player));
    assertEquals(0L, loaded.get(other));

    loaded.remove(player);
    assertEquals(0L, loaded.get(player));
    assertTrue(loaded.save(new CompoundTag(), null).isEmpty(), "The malformed key and the removed entry must not persist");
  }
}
