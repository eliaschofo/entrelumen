package dev.entrelumen.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import net.blay09.mods.defaultoptions.DefaultOptionsContext;
import net.blay09.mods.defaultoptions.api.DefaultOptionsAPI;
import net.blay09.mods.defaultoptions.api.DefaultOptionsCategory;
import net.blay09.mods.defaultoptions.api.DefaultOptionsHandler;
import net.blay09.mods.defaultoptions.api.DefaultOptionsLoadStage;
import net.blay09.mods.defaultoptions.api.DefaultOptionsPlugin;

/** Default Options SPI runs before client options are parsed, unlike mod setup events. */
public final class ClientDefaultsPlugin implements DefaultOptionsPlugin {
  @Override
  public void initialize() {
    DefaultOptionsAPI.registerOptionsHandler(new MissingGeneralOptions());
  }

  private static final class MissingGeneralOptions implements DefaultOptionsHandler {
    @Override
    public String getId() {
      return "entrelumen-missing-client-options";
    }

    @Override
    public DefaultOptionsCategory getCategory() {
      return DefaultOptionsCategory.OPTIONS;
    }

    @Override
    public DefaultOptionsLoadStage getLoadStage() {
      return DefaultOptionsLoadStage.EARLY_LOAD;
    }

    @Override
    public boolean hasDefaults(DefaultOptionsContext context) {
      return Files.isRegularFile(context.getDefaultOptionsFile("options.txt").toPath());
    }

    @Override
    public boolean shouldLoadDefaults(DefaultOptionsContext context) {
      var options = context.getMinecraftDataDir().toPath().resolve("options.txt");
      return hasDefaults(context) && Files.isRegularFile(options) && !Files.isSymbolicLink(options);
    }

    @Override
    public void loadDefaults(DefaultOptionsContext context) {
      try {
        int added = ClientOptionsMerge.appendMissing(
            context.getMinecraftDataDir().toPath().resolve("options.txt"),
            context.getDefaultOptionsFile("options.txt").toPath());
        if (added > 0) LogUtils.getLogger().info("Added {} missing ENTRELUMEN client defaults", added);
      } catch (IOException | IllegalArgumentException error) {
        LogUtils.getLogger().warn("ENTRELUMEN client defaults skipped; existing options kept ({})",
            error.getClass().getSimpleName());
      }
    }

    @Override
    public void saveCurrentOptionsAsDefault(DefaultOptionsContext context) {
      // This handler does not export settings; native save commands keep their own behavior.
    }

    @Override
    public void saveCurrentOptions(DefaultOptionsContext context) {
      // Never export a player's current settings into the authored fragment.
    }
  }
}
