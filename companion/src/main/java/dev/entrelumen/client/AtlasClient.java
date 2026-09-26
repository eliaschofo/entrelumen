package dev.entrelumen.client;

import dev.entrelumen.AtlasNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AtlasClient {
  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> {
      AtlasNetwork.clientReceiver = AtlasScreen::receive;
      AtlasNetwork.catalogReceiver = ArkCatalogScreen::receive;
    });
  }
}
