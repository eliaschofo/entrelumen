package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One bounded, read-only server snapshot of the Ark's status screen per interaction. */
public final class JournalBookNetwork {
  static final int MAX_ENTRIES = 24;
  static final int MAX_DETAILS = 8;
  private static final int MAX_BYTES = 32 * 1024;

  /** Where a row goes on the Ark screen. */
  public enum Section { EFFECT, MODULES, ACTIVATION }

  /**
   * One row: an item icon, an optional label (a batch material is named by its item), progress as
   * {@code done / total} (total 0 means no check) and the lines shown on hover.
   */
  public record Entry(Section section, ResourceLocation icon, Optional<Component> label, int done,
      int total, List<Component> details) {
    public Entry {
      Objects.requireNonNull(section);
      Objects.requireNonNull(icon);
      Objects.requireNonNull(label);
      details = List.copyOf(details);
      if (done < 0 || total < 0 || done > total)
        throw new IllegalArgumentException("Journal entry progress out of range");
      if (details.size() > MAX_DETAILS)
        throw new IllegalArgumentException("Journal entry detail count exceeds limit");
    }

    public boolean complete() {
      return total > 0 && done >= total;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeEnum(section);
      buf.writeResourceLocation(icon);
      buf.writeBoolean(label.isPresent());
      label.ifPresent(text -> ComponentSerialization.STREAM_CODEC.encode(buf, text));
      buf.writeVarInt(done);
      buf.writeVarInt(total);
      buf.writeVarInt(details.size());
      for (Component line : details) ComponentSerialization.STREAM_CODEC.encode(buf, line);
    }

    private static Entry read(RegistryFriendlyByteBuf buf) {
      var section = buf.readEnum(Section.class);
      var icon = buf.readResourceLocation();
      Optional<Component> label = buf.readBoolean()
          ? Optional.of(ComponentSerialization.STREAM_CODEC.decode(buf)) : Optional.empty();
      int done = buf.readVarInt();
      int total = buf.readVarInt();
      int count = buf.readVarInt();
      if (count < 0 || count > MAX_DETAILS)
        throw new IllegalArgumentException("Journal entry detail count exceeds limit");
      List<Component> details = new ArrayList<>(count);
      for (int i = 0; i < count; i++) details.add(ComponentSerialization.STREAM_CODEC.decode(buf));
      return new Entry(section, icon, label, done, total, details);
    }
  }

  /** {@code block} is the clicked module's ID or {@code ark_controller}. */
  public record Snapshot(UUID player, UUID campaign, String block,
      ArkFieldJournals.Status status, Component statusLine, Component flavor,
      Optional<Component> hint, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("entrelumen", "journal_book"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.ofMember(Snapshot::write, Snapshot::read);

    public Snapshot {
      Objects.requireNonNull(player);
      Objects.requireNonNull(campaign);
      Objects.requireNonNull(block);
      Objects.requireNonNull(status);
      Objects.requireNonNull(statusLine);
      Objects.requireNonNull(flavor);
      Objects.requireNonNull(hint);
      entries = List.copyOf(entries);
      if (entries.isEmpty() || entries.size() > MAX_ENTRIES)
        throw new IllegalArgumentException("Journal entry count exceeds limit");
    }

    public List<Entry> entries(Section section) {
      return entries.stream().filter(entry -> entry.section() == section).toList();
    }

    /** Every text the screen can show, including hover lines; material names come from items. */
    public List<Component> texts() {
      return Stream.of(Stream.of(statusLine, flavor), hint.stream(),
              entries.stream().flatMap(entry -> Stream.concat(entry.label().stream(),
                  entry.details().stream())))
          .flatMap(stream -> stream).toList();
    }

    @Override
    public Type<Snapshot> type() { return TYPE; }

    private void write(RegistryFriendlyByteBuf buf) {
      int start = buf.writerIndex();
      buf.writeUUID(player);
      buf.writeUUID(campaign);
      buf.writeUtf(block, 64);
      buf.writeEnum(status);
      ComponentSerialization.STREAM_CODEC.encode(buf, statusLine);
      ComponentSerialization.STREAM_CODEC.encode(buf, flavor);
      buf.writeBoolean(hint.isPresent());
      hint.ifPresent(text -> ComponentSerialization.STREAM_CODEC.encode(buf, text));
      checkWritten(buf, start);
      buf.writeVarInt(entries.size());
      for (Entry entry : entries) {
        entry.write(buf);
        checkWritten(buf, start);
      }
    }

    private static void checkWritten(RegistryFriendlyByteBuf buf, int start) {
      if (buf.writerIndex() - start > MAX_BYTES)
        throw new IllegalArgumentException("Journal payload exceeds limit");
    }

    private static Snapshot read(RegistryFriendlyByteBuf buf) {
      if (buf.readableBytes() > MAX_BYTES)
        throw new IllegalArgumentException("Journal payload exceeds limit");
      UUID player = buf.readUUID();
      UUID campaign = buf.readUUID();
      String block = buf.readUtf(64);
      var status = buf.readEnum(ArkFieldJournals.Status.class);
      Component statusLine = ComponentSerialization.STREAM_CODEC.decode(buf);
      Component flavor = ComponentSerialization.STREAM_CODEC.decode(buf);
      Optional<Component> hint = buf.readBoolean()
          ? Optional.of(ComponentSerialization.STREAM_CODEC.decode(buf)) : Optional.empty();
      int count = buf.readVarInt();
      if (count < 1 || count > MAX_ENTRIES)
        throw new IllegalArgumentException("Journal entry count exceeds limit");
      List<Entry> entries = new ArrayList<>(count);
      for (int i = 0; i < count; i++) entries.add(Entry.read(buf));
      return new Snapshot(player, campaign, block, status, statusLine, flavor, hint, entries);
    }
  }

  /** Client subscriber assigns this without loading client classes on a dedicated server. */
  public static Consumer<Snapshot> clientReceiver = snapshot -> {};

  private JournalBookNetwork() {}

  public static void register(RegisterPayloadHandlersEvent event) {
    // Version 3 (Ark v2): the clicked block by ID, effect/modules/activation sections.
    event.registrar("3").playToClient(Snapshot.TYPE, Snapshot.CODEC,
        (snapshot, context) -> context.enqueueWork(() -> clientReceiver.accept(snapshot)));
  }
}
