package dev.entrelumen.client.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Adds the discriminator expected by EMI to JEED's effect ingredient JSON. */
public final class JeedEffectJson {
  private JeedEffectJson() {}

  public static void addTypeIfMissing(JsonElement serializedEffect) {
    if (serializedEffect != null && serializedEffect.isJsonObject()) {
      JsonObject effect = serializedEffect.getAsJsonObject();
      if (!effect.has("type")) {
        effect.addProperty("type", "mob_effect");
      }
    }
  }
}
