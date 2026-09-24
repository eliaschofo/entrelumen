package dev.entrelumen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * "Inicio sin bloat" for first-join gifts that no config can turn off (see
 * docs/design/heliodor-compass.md, section 1). Everything a pack config disables stays in
 * {@code pack/config}; this only covers gifts a mod hands out from remote or hard-coded state.
 *
 * <p>Ars Nouveau 5.13.1 gives every player a Starbuncle plush, plus a store link in chat, once per
 * player while its remote {@code starbuncle_plush.json} campaign is on ({@code
 * EventHandler.playerLogin}, gated only by the persisted flag {@code an_plush}). Marking that flag
 * before Ars's own login handler runs keeps the gift and the advert away without touching the JAR.
 * The plush stays craftable.
 */
public final class FirstJoinGifts {
  /** Ars Nouveau's one-time plush flag inside {@link Player#PERSISTED_NBT_TAG}. */
  static final String ARS_PLUSH_FLAG = "an_plush";

  private FirstJoinGifts() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, FirstJoinGifts::onLogin);
  }

  static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
    Player player = event.getEntity();
    if (player.level().isClientSide || !ModList.get().isLoaded("ars_nouveau")) return;
    CompoundTag data = player.getPersistentData();
    CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
    if (persisted.getBoolean(ARS_PLUSH_FLAG)) return;
    persisted.putBoolean(ARS_PLUSH_FLAG, true);
    data.put(Player.PERSISTED_NBT_TAG, persisted);
  }
}
