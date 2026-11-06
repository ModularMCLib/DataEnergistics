package com.fish_dan_.data_energistics.core.config;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** UTF-8 properties persistence for startup configuration. */
public final class EarlyConfigProperties {

    private EarlyConfigProperties() {}

    public static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    public static void write(Path path, Object2ObjectMap<String, Option> options) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder text = new StringBuilder("# Data Energistics early Mixin settings. Changes require a full restart.\n")
                .append("# Package rules disable all descendants. Missing dependencies cannot be enabled here.\n# Available settings:\n");
        for (Option option : options.values()) {
            if (option.isHidden()) continue;
            text.append("#\n");
            for (String comment : option.getComment()) text.append("# ").append(comment).append('\n');
            text.append("# ").append(option.getName()).append('=').append(option.isDefaultEnabled()).append(" (default)\n");
        }
        text.append("#\n# User overrides:\n");
        for (Option option : options.values()) {
            if (!option.isHidden() && option.isUserDefined()) {
                text.append(option.getName()).append('=').append(option.isUserEnabled()).append('\n');
            }
        }
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }
}
