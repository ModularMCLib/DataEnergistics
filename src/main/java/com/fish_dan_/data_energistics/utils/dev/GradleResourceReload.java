package com.fish_dan_.data_energistics.utils.dev;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Runs development resource builds without blocking the client thread. State belongs to that thread. */
@NullMarked
public final class GradleResourceReload {

    private static final @Nullable Path PROJECT_ROOT = findProjectRoot();
    private static @Nullable CompletableFuture<Void> pendingReload;

    private GradleResourceReload() {}

    /**
     * Called by the development F3+T hook on the client thread. Shares an in-flight build and reload,
     * inherits the launch environment (including GRADLE_USER_HOME), and propagates reload failures.
     * Build failures are reported before reloading the previously available resources.
     */
    public static CompletableFuture<Void> rebuildAndReload(Minecraft minecraft, Supplier<CompletableFuture<Void>> reload) {
        CompletableFuture<Void> currentReload = pendingReload;
        if (currentReload != null && !currentReload.isDone()) {
            return currentReload;
        }
        Path projectRoot = PROJECT_ROOT;
        if (projectRoot == null) {
            return reload.get();
        }

        ProcessBuilder builder = Util.getPlatform() == Util.OS.WINDOWS ? new ProcessBuilder("cmd.exe", "/d", "/c", "gradlew.bat", ":processResources", "--console=plain") : new ProcessBuilder("./gradlew", ":processResources", "--console=plain");
        builder.directory(projectRoot.toFile());
        builder.inheritIO();

        Process process;
        long startedAt = Util.getMillis();
        try {
            process = builder.start();
        } catch (IOException exception) {
            Data_Energistics.LOGGER.error("Could not start the Gradle resource build in {}; reloading existing resources", projectRoot, exception);
            minecraft.gui.getChat().addMessage(Component.translatable("data_energistics.debug.resource_rebuild.failed"));
            return reload.get();
        }

        minecraft.gui.getChat().addMessage(Component.translatable("data_energistics.debug.resource_rebuild.start"));
        CompletableFuture<Void> nextReload = process.onExit().thenComposeAsync(completed -> {
            if (completed.exitValue() == 0) {
                minecraft.gui.getChat().addMessage(Component.translatable("data_energistics.debug.resource_rebuild.done", Util.getMillis() - startedAt));
            } else {
                Data_Energistics.LOGGER.error("Gradle resource build in {} exited with code {}; reloading existing resources", projectRoot, completed.exitValue());
                minecraft.gui.getChat().addMessage(Component.translatable("data_energistics.debug.resource_rebuild.failed"));
            }
            return reload.get();
        }, minecraft);
        pendingReload = nextReload;
        return nextReload;
    }

    private static @Nullable Path findProjectRoot() {
        Path directory = Path.of("").toAbsolutePath().normalize();
        String wrapper = Util.getPlatform() == Util.OS.WINDOWS ? "gradlew.bat" : "gradlew";
        while (directory != null) {
            if ((Files.isRegularFile(directory.resolve("settings.gradle")) || Files.isRegularFile(directory.resolve("settings.gradle.kts"))) &&
                    Files.isRegularFile(directory.resolve(wrapper))) {
                return directory;
            }
            directory = directory.getParent();
        }
        Data_Energistics.LOGGER.warn("Could not locate a Gradle project above the working directory; development resource reloads will use existing resources");
        return null;
    }
}
