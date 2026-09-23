package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;

/** Packs the existing journal lines without dropping text at a vanilla book page boundary. */
public final class JournalBookPagination {
  private JournalBookPagination() {}

  public static List<Component> pages(List<Component> lines, int maxRows,
      ToIntFunction<Component> rows, Function<Component, List<Component>> splitLongLine) {
    if (maxRows < 1) throw new IllegalArgumentException("Book page has no text rows");
    List<Component> pages = new ArrayList<>();
    Component page = Component.empty();
    boolean hasLine = false;
    for (Component original : lines) {
      List<Component> pieces = rows.applyAsInt(original) <= maxRows
          ? List.of(original) : splitLongLine.apply(original);
      for (Component piece : pieces) {
        if (rows.applyAsInt(piece) > maxRows)
          throw new IllegalArgumentException("Journal line cannot fit a book page");
        Component candidate = hasLine ? join(page, piece) : piece;
        if (hasLine && rows.applyAsInt(candidate) > maxRows) {
          pages.add(page);
          page = piece;
        } else {
          page = candidate;
        }
        hasLine = true;
      }
    }
    if (hasLine) pages.add(page);
    return List.copyOf(pages);
  }

  private static Component join(Component first, Component second) {
    return Component.empty().append(first).append("\n").append(second);
  }
}
