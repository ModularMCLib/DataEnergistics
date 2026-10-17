package com.fish_dan_.data_energistics.client.gui;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.client.gui.style.Blitter;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Resolves named GUI icons to complete, independently replaceable PNG textures. */
@NullMarked
public final class DataEnergisticsIcon {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final ResourceLocation ICONS_JSON = Data_Energistics.id("textures/guis/icons.json");
    private static final ResourceLocation DEFAULT_TEXTURE = Data_Energistics.id("textures/guis/states/background/square.png");
    private static final String FALLBACK_ICON = "BACKGROUND_BLOCK";
    private static final IconDef FALLBACK = new IconDef(DEFAULT_TEXTURE, 16, 16);
    private static final ConcurrentMap<String, IconDef> CACHE = new ConcurrentHashMap<>();

    private DataEnergisticsIcon() {}

    public static Blitter getBlitter(String name) {
        var icon = CACHE.computeIfAbsent(name, DataEnergisticsIcon::loadIcon);
        return Blitter.texture(icon.texture(), icon.width(), icon.height())
                .src(0, 0, icon.width(), icon.height());
    }

    private static IconDef loadIcon(String name) {
        var resourceManager = Minecraft.getInstance().getResourceManager();
        try (Reader reader = new InputStreamReader(resourceManager.getResourceOrThrow(ICONS_JSON).open(), StandardCharsets.UTF_8)) {
            JsonObject icons = GsonHelper.getAsJsonObject(GsonHelper.parse(reader), "icons");
            if (!icons.has(name)) {
                LOGGER.error("Missing GUI icon definition in {}: {}, falling back to {}", ICONS_JSON, name, FALLBACK_ICON);
                name = FALLBACK_ICON;
            }
            JsonObject icon = GsonHelper.getAsJsonObject(icons, name);
            int width = GsonHelper.getAsInt(icon, "width");
            int height = GsonHelper.getAsInt(icon, "height");
            if (width <= 0 || height <= 0) throw new JsonSyntaxException("GUI icon dimensions must be positive: " + name);
            return new IconDef(resolveTexture(GsonHelper.getAsString(icon, "texture"), DEFAULT_TEXTURE), width, height);
        } catch (IOException | RuntimeException error) {
            // Resource packs are an external boundary; a malformed definition must not abort screen rendering.
            LOGGER.error("Failed to load GUI icon {} from {}, using {}", name, ICONS_JSON, FALLBACK_ICON, error);
            return FALLBACK;
        }
    }

    static ResourceLocation resolveTexture(String texture, ResourceLocation defaultTexture) {
        if (texture.contains(":")) {
            return ResourceLocation.parse(texture);
        }
        return ResourceLocation.fromNamespaceAndPath(defaultTexture.getNamespace(), "textures/" + texture);
    }

    private record IconDef(
                           ResourceLocation texture,
                           int width,
                           int height) {}
}
