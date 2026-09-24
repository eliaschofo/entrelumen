package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-computed view of an open Atlas Library; the client only displays it. */
public final class AtlasLibraryNetwork {
  private AtlasLibraryNetwork() {}

  /**
   * One offer. {@code next}/{@code max} are 0 when nothing can be bought; costs are the exact
   * amounts the server will charge from the output book's current level.
   */
  public record Offer(int enchantment, int current, int cap, int next, long nextCost, int max,
      long maxCost) {}

  public record Snapshot(int containerId, long pool, float eterna, boolean apothic,
      List<Offer> offers) implements CustomPacketPayload {
    public static final int LIMIT = 4096;
    public static final Type<Snapshot> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("entrelumen", "atlas_library_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.ofMember(Snapshot::write, Snapshot::read);

    @Override
    public Type<Snapshot> type() {
      return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeVarInt(containerId);
      buf.writeVarLong(pool);
      buf.writeFloat(eterna);
      buf.writeBoolean(apothic);
      buf.writeVarInt(offers.size());
      for (Offer offer : offers) {
        buf.writeVarInt(offer.enchantment());
        buf.writeVarInt(offer.current());
        buf.writeVarInt(offer.cap());
        buf.writeVarInt(offer.next());
        buf.writeVarLong(offer.nextCost());
        buf.writeVarInt(offer.max());
        buf.writeVarLong(offer.maxCost());
      }
    }

    private static Snapshot read(RegistryFriendlyByteBuf buf) {
      int containerId = buf.readVarInt();
      long pool = buf.readVarLong();
      float eterna = buf.readFloat();
      boolean apothic = buf.readBoolean();
      int count = buf.readVarInt();
      if (count < 0 || count > LIMIT) throw new IllegalArgumentException("Atlas Library snapshot exceeds limit");
      List<Offer> offers = new ArrayList<>(count);
      for (int i = 0; i < count; i++)
        offers.add(new Offer(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarLong(), buf.readVarInt(), buf.readVarLong()));
      return new Snapshot(containerId, pool, eterna, apothic, List.copyOf(offers));
    }
  }

  /** Assigned only by the client-side subscriber; dedicated servers never load a Screen. */
  public static Consumer<Snapshot> clientReceiver = snapshot -> {};

  public static void register(RegisterPayloadHandlersEvent event) {
    event.registrar("1").playToClient(Snapshot.TYPE, Snapshot.CODEC,
        (snapshot, context) -> context.enqueueWork(() -> clientReceiver.accept(snapshot)));
  }
}
