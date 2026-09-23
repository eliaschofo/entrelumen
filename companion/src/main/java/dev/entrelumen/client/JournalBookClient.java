package dev.entrelumen.client;

import dev.entrelumen.JournalBookNetwork;
import dev.entrelumen.JournalBookPagination;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Native read-only book surface; no editable book item or client-side campaign state. */
@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JournalBookClient {
  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> JournalBookNetwork.clientReceiver = JournalBookClient::receive);
  }

  private static void receive(JournalBookNetwork.Snapshot snapshot) {
    var client = Minecraft.getInstance();
    if (client.player == null || !client.player.getUUID().equals(snapshot.player())) return;
    var font = client.font;
    // BookViewScreen renders at most fourteen 9-pixel rows inside its 114-pixel text width.
    int rowsPerPage = Math.min(14, 128 / Math.max(9, font.lineHeight));
    var pages = JournalBookPagination.pages(snapshot.lines(), rowsPerPage,
        line -> Math.max(1, font.split(line, 114).size()),
        line -> font.getSplitter().splitLines(line, 114, Style.EMPTY).stream()
            .map(part -> (Component) Component.literal(part.getString())).toList());
    client.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
  }
}
