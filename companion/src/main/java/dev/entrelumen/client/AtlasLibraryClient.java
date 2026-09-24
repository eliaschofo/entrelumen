package dev.entrelumen.client;

import dev.entrelumen.ApotheosisContent;
import dev.entrelumen.AtlasLibraryMenu;
import dev.entrelumen.AtlasLibraryNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AtlasLibraryClient {
  private AtlasLibraryClient() {}

  @SubscribeEvent
  public static void screens(RegisterMenuScreensEvent event) {
    event.register(ApotheosisContent.ATLAS_LIBRARY_MENU.get(), AtlasLibraryScreen::new);
  }

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> AtlasLibraryNetwork.clientReceiver = snapshot -> {
      var player = Minecraft.getInstance().player;
      if (player != null && player.containerMenu instanceof AtlasLibraryMenu menu
          && menu.containerId == snapshot.containerId()) {
        menu.snapshot = snapshot;
        menu.onSnapshot.run();
      }
    });
  }
}
