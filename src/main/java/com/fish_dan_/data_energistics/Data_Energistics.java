package com.fish_dan_.data_energistics;

import com.fish_dan_.data_energistics.bootstrap.common.CommonProxy;
import com.fish_dan_.data_energistics.util.LogPairs;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

@Mod(Data_Energistics.MODID)
public class Data_Energistics {

    public static final String MODID = "data_energistics";

    public static final String MODNAME = "Data Energistics";

    public static final Logger LOGGER = LogManager.getLogger(MODNAME);

    public Data_Energistics(IEventBus modEventBus) {
        CommonProxy.init(modEventBus);
        String[] selectedLogPair = LogPairs.STARTUP_SHUTDOWN_LOG_PAIRS[RandomSource.create().nextInt(LogPairs.STARTUP_SHUTDOWN_LOG_PAIRS.length)];
        LOGGER.info(selectedLogPair[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> LOGGER.info(selectedLogPair[1]),
                "data-energistics-shutdown-log"));
    }

    public static boolean isProd() {
        return FMLLoader.isProduction();
    }

    public static boolean isDev() {
        return !isProd();
    }

    public static boolean isDataGen() {
        return DatagenModLoader.isRunningDataGen();
    }

    @Nullable
    public static MinecraftServer getMinecraftServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static boolean isClientThread() {
        if (!isClientSide()) {
            return false;
        }
        return Minecraft.getInstance().isSameThread();
    }

    public static boolean isClientSide() {
        return FMLEnvironment.dist.isClient();
    }

    public static ResourceLocation id(String path) {
        if (path.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(MODID, "");
        }
        int i = path.indexOf(':');
        if (i > 0) {
            return ResourceLocation.tryParse(path);
        } else if (i == 0) {
            path = path.substring(i + 1);
        }
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
