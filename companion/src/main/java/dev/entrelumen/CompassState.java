package dev.entrelumen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Heliodor compass shows, written by the server into the stack's
 * {@code entrelumen:compass_state} component. Item property values are stable; the controller maps
 * them to textures.
 *
 * @param target where the needle points, only in the {@link #POINTING} state
 * @param state {@link #POINTING}..{@link #COMPLETE}
 * @param dimension target dimension: 0 overworld, 1 nether, 2 end, 3 aether, 4 twilight, 5 other
 * @param kind objective kind: 0 structure, 1 boss, 2 artifact
 * @param objective objective ID, empty when there is none
 */
public record CompassState(
    Optional<GlobalPos> target, int state, int dimension, int kind, String objective) {
  /** Needle points at a known site in the holder's dimension. */
  public static final int POINTING = 0;
  /** The objective lies in another dimension; the needle rests. */
  public static final int ELSEWHERE = 1;
  /** A bounded search is running; the needle spins. The only spinning state. */
  public static final int SEARCHING = 2;
  /** Nothing within the objective radius of here; the needle rests instead of spinning. */
  public static final int NOT_FOUND = 3;
  /** The next objective belongs to a later act; the needle rests. */
  public static final int LOCKED = 4;
  /** Every listed objective is reached, or the holder has no campaign; the needle rests. */
  public static final int COMPLETE = 5;

  public static final CompassState IDLE = new CompassState(Optional.empty(), COMPLETE, 0, 0, "");

  public static final Codec<CompassState> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      GlobalPos.CODEC.optionalFieldOf("target").forGetter(CompassState::target),
                      Codec.INT.fieldOf("state").forGetter(CompassState::state),
                      Codec.INT.fieldOf("dimension").forGetter(CompassState::dimension),
                      Codec.INT.fieldOf("kind").forGetter(CompassState::kind),
                      Codec.STRING.optionalFieldOf("objective", "").forGetter(CompassState::objective))
                  .apply(instance, CompassState::new));

  public static final StreamCodec<ByteBuf, CompassState> STREAM_CODEC =
      StreamCodec.composite(
          ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), CompassState::target,
          ByteBufCodecs.VAR_INT, CompassState::state,
          ByteBufCodecs.VAR_INT, CompassState::dimension,
          ByteBufCodecs.VAR_INT, CompassState::kind,
          ByteBufCodecs.stringUtf8(64), CompassState::objective,
          CompassState::new);

  public boolean spinning() {
    return state == SEARCHING;
  }

  public static int dimensionValue(String dimension) {
    return switch (dimension) {
      case "minecraft:overworld" -> 0;
      case "minecraft:the_nether" -> 1;
      case "minecraft:the_end" -> 2;
      case "aether:the_aether" -> 3;
      case "twilightforest:twilight_forest" -> 4;
      default -> 5;
    };
  }
}
