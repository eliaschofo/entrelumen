package dev.entrelumen.client;

import dev.entrelumen.benchmark.CaptureSession;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "entrelumen", value = Dist.CLIENT)
public final class BenchmarkClient {
  private static volatile CaptureSession session;
  private static volatile net.minecraft.server.MinecraftServer server;
  private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;

  @SubscribeEvent
  public static void commands(RegisterClientCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("entrelumen_capture")
                .then(Commands.literal("start").executes(context -> start()))
                .then(
                    Commands.literal("stop")
                        .executes(
                            context -> {
                              stop(false);
                              return 1;
                            })));
  }

  private static int start() {
    var mc = Minecraft.getInstance();
    if (session != null) {
      message("active");
      return 0;
    }
    if (mc.getSingleplayerServer() == null || mc.level == null || mc.player == null) {
      message("integrated_only");
      return 0;
    }
    try {
      var root = mc.gameDirectory.toPath().resolve("entrelumen-captures");
      Files.createDirectories(root);
      server = mc.getSingleplayerServer();
      dimension = mc.level.dimension();
      session = new CaptureSession(root, 32768);
      var current = session;
      var startingPlayer = mc.player;
      current
          .completion()
          .thenAccept(
              result ->
                  mc.execute(
                      () -> {
                        if (session != current) return;
                        session = null;
                        server = null;
                        if (mc.player != startingPlayer) return;
                        if (result.successful()) message("stopped", current.directory.toString());
                        else message("error", current.directory + ": " + result.error());
                      }));
      message("started", session.directory.toString());
      return 1;
    } catch (java.io.IOException error) {
      message("error", error.getMessage());
      return 0;
    }
  }

  private static void message(String key, Object... args) {
    var player = Minecraft.getInstance().player;
    if (player != null)
      player.sendSystemMessage(Component.translatable("entrelumen.capture." + key, args));
  }

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Pre event) {
    var current = session;
    if (current == null || current.isClosed()) return;
    var mc = Minecraft.getInstance();
    boolean loaded = mc.level != null && mc.player != null;
    boolean sameDimension = loaded && mc.level.dimension().equals(dimension);
    boolean valid =
        loaded
            && sameDimension
            && mc.isWindowActive()
            && !mc.isPaused()
            && mc.getOverlay() == null
            && !mc.noRender;
    current.frame(
        System.nanoTime(),
        valid,
        valid
            ? "valid"
            : "focus="
                + mc.isWindowActive()
                + ";pause="
                + mc.isPaused()
                + ";loaded="
                + loaded
                + ";same_dimension="
                + sameDimension);
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    var current = session;
    if (current == null || current.isClosed() || event.getServer() != server) return;
    var s = event.getServer();
    current.tick(
        System.nanoTime(),
        s.getTickCount(),
        s.getTickTimesNanos()[s.getTickCount() % 100],
        !s.isPaused()
            && !s.tickRateManager().isFrozen()
            && s.tickRateManager().tickrate() == 20
            && !s.tickRateManager().isSprinting());
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    stop(true);
  }

  @SubscribeEvent
  public static void clientTick(ClientTickEvent.Post event) {
    var current = session;
    if (current == null || current.isClosed()) return;
    var mc = Minecraft.getInstance();
    if (mc.noRender || mc.level == null || mc.player == null)
      current.frame(System.nanoTime(), false, "render_unavailable");
  }

  @SubscribeEvent
  public static void serverStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
    if (event.getServer() != server) return;
    var current = session;
    if (current != null) current.close("server_stopping");
  }

  private static void stop(boolean disconnected) {
    var current = session;
    if (current != null) current.close(disconnected ? "disconnected" : null);
  }
}
