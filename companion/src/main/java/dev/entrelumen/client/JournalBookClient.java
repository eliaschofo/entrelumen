package dev.entrelumen.client;

import dev.entrelumen.JournalBookNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Opens the read-only module status screen; no client-side campaign state is kept. */
@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JournalBookClient {
  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> JournalBookNetwork.clientReceiver = JournalBookClient::receive);
  }

  private static void receive(JournalBookNetwork.Snapshot snapshot) {
    var client = Minecraft.getInstance();
    if (client.player == null || !client.player.getUUID().equals(snapshot.player())) return;
    client.setScreen(new ArkModuleScreen(snapshot));
  }
}
