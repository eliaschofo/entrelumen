package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One bounded, read-only server snapshot per journal interaction. */
public final class JournalBookNetwork {
  private static final int MAX_LINES = 32;
  private static final int MAX_BYTES = 32 * 1024;

  public record Snapshot(UUID player, UUID campaign, ArkFieldJournals.Kind kind,
      List<Component> lines) implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("entrelumen", "journal_book"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.ofMember(Snapshot::write, Snapshot::read);

    public Snapshot {
      Objects.requireNonNull(player);
      Objects.requireNonNull(campaign);
      Objects.requireNonNull(kind);
      lines = List.copyOf(lines);
      if (lines.isEmpty() || lines.size() > MAX_LINES)
        throw new IllegalArgumentException("Journal line count exceeds limit");
    }

    @Override
    public Type<Snapshot> type() { return TYPE; }

    private void write(RegistryFriendlyByteBuf buf) {
      int start = buf.writerIndex();
      buf.writeUUID(player);
      buf.writeUUID(campaign);
      buf.writeEnum(kind);
      buf.writeVarInt(lines.size());
      for (Component line : lines) {
        ComponentSerialization.STREAM_CODEC.encode(buf, line);
        if (buf.writerIndex() - start > MAX_BYTES)
          throw new IllegalArgumentException("Journal payload exceeds limit");
      }
    }

    private static Snapshot read(RegistryFriendlyByteBuf buf) {
      if (buf.readableBytes() > MAX_BYTES)
        throw new IllegalArgumentException("Journal payload exceeds limit");
      int start = buf.readerIndex();
      UUID player = buf.readUUID();
      UUID campaign = buf.readUUID();
      var kind = buf.readEnum(ArkFieldJournals.Kind.class);
      int count = buf.readVarInt();
      if (count < 1 || count > MAX_LINES)
        throw new IllegalArgumentException("Journal line count exceeds limit");
      List<Component> lines = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        lines.add(ComponentSerialization.STREAM_CODEC.decode(buf));
        if (buf.readerIndex() - start > MAX_BYTES)
          throw new IllegalArgumentException("Journal payload exceeds limit");
      }
      return new Snapshot(player, campaign, kind, lines);
    }
  }

  /** Client subscriber assigns this without loading client classes on a dedicated server. */
  public static Consumer<Snapshot> clientReceiver = snapshot -> {};

  private JournalBookNetwork() {}

  public static void register(RegisterPayloadHandlersEvent event) {
    event.registrar("1").playToClient(Snapshot.TYPE, Snapshot.CODEC,
        (snapshot, context) -> context.enqueueWork(() -> clientReceiver.accept(snapshot)));
  }
}
