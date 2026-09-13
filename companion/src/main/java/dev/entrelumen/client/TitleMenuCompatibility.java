package dev.entrelumen.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Stable optional FancyMenu bindings; native actions, labels and rendering stay intact. */
@EventBusSubscriber(modid = "entrelumen", value = Dist.CLIENT)
public final class TitleMenuCompatibility {
    private static boolean resolved;
    private static Method identifierSetter;

    private TitleMenuCompatibility() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        Method setter = resolveSetter();
        if (setter == null) return;
        for (var child : event.getScreen().children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            String identifier = identifierFor(widget);
            if (identifier == null) continue;
            try {
                setter.invoke(widget, identifier);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException error) {
                LogUtils.getLogger().warn("Cannot bind ENTRELUMEN title widget {}", identifier, error);
            }
        }
    }

    private static String identifierFor(AbstractWidget widget) {
        if (widget.getMessage().getContents() instanceof TranslatableContents text) {
            if (text.getKey().equals("options.language")) return "entrelumen_title_language";
            if (text.getKey().equals("options.accessibility")) return "entrelumen_title_accessibility";
        }
        return switch (widget.getClass().getName()) {
            case "com.simibubi.create.infrastructure.gui.OpenCreateMenuButton" -> "entrelumen_title_create";
            case "net.mehvahdjukaar.supplementaries.client.screens.ConfigButton" -> "entrelumen_title_supplementaries";
            default -> null;
        };
    }

    private static Method resolveSetter() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("fancymenu")) {
                try {
                    // FancyMenu 3.9.12 UniqueWidget is mixed into AbstractWidget. Reflection
                    // keeps this optional UI compatibility out of server/runtime dependencies.
                    identifierSetter = Class.forName(
                            "de.keksuccino.fancymenu.util.rendering.ui.widget.UniqueWidget")
                            .getMethod("setWidgetIdentifierFancyMenu", String.class);
                } catch (ReflectiveOperationException error) {
                    LogUtils.getLogger().warn("FancyMenu stable widget binding API unavailable", error);
                }
            }
        }
        return identifierSetter;
    }
}
