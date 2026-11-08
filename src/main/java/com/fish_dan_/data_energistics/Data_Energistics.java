package com.fish_dan_.data_energistics;

import com.fish_dan_.data_energistics.common.CommonProxy;
import com.fish_dan_.data_energistics.common.network.DEPayloads;
import com.fish_dan_.data_energistics.config.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.config.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.utils.LogPairs;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import dev.emi.emi.config.EmiConfig;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;

@Mod(Data_Energistics.MOD_ID)
public class Data_Energistics {

    public static final String MOD_ID = "data_energistics";
    private static final ResourceLocation TEMPLATE_LOCATION = ResourceLocation.fromNamespaceAndPath(MOD_ID, "");

    public static final String MOD_NAME = "Data Energistics";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);
    public static final Path DE_FOLDER = getGameDir().resolve("de");
    @ApiStatus.Internal
    public static IEventBus deModBus;

    public Data_Energistics(IEventBus modBus) {
        Data_Energistics.deModBus = modBus;

        Data_Energistics.LOGGER.debug(
                "Registered Configuration schemas {} and {}",
                DataEnergisticsConfiguration.HOLDER.getConfigId(),
                DataExtractorRulesConfiguration.HOLDER.getConfigId());
        CommonProxy.init(modBus);

        modBus.addListener(DEPayloads::registerPayloads);
        String[] selectedLogPair = LogPairs.STARTUP_SHUTDOWN_LOG_PAIRS[RandomSource.create().nextInt(LogPairs.STARTUP_SHUTDOWN_LOG_PAIRS.length)];
        LOGGER.info(selectedLogPair[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> LOGGER.info(selectedLogPair[1]),
                "data-energistics-shutdown-log"));
    }

    public static ResourceLocation id(String path) {
        if (path.isBlank()) {
            return TEMPLATE_LOCATION;
        }
        int i = path.indexOf(':');
        if (i > 0) {
            return ResourceLocation.tryParse(path);
        } else if (i == 0) {
            path = path.substring(i + 1);
        }
        return TEMPLATE_LOCATION.withPath(path);
    }

    public static String appendIdString(String id) {
        int i = id.indexOf(':');
        if (i > 0) {
            return id;
        } else if (i == 0) {
            return MOD_ID + id;
        } else {
            return MOD_ID + ":" + id;
        }
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

    public static MinecraftServer getMinecraftServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** Queries discovered Mod IDs, including external entrypoint requirements before ModList is available. */
    public static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        if (modList != null) return modList.isLoaded(modId);
        else return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }

    public static boolean isClientThread() {
        if (!isClientSide()) {
            return false;
        }
        return Minecraft.getInstance().isSameThread();
    }

    /**
     * @return if the game is the <strong>PHYSICAL</strong> client, e.g. not a dedicated server.
     * @apiNote Do not use this to check if you're currently on the server thread for side-specific actions!
     *          It does <strong>NOT</strong> work for that. Use {@link #isClientThread()} instead.
     * @see #isClientThread()
     */
    public static boolean isClientSide() {
        return FMLEnvironment.dist.isClient();
    }

    /**
     * This check isn't the same for client and server!
     *
     * @return if it's safe to access the current instance {@link net.minecraft.world.level.Level Level} on client or if
     *         it's safe to access any level on server.
     */
    public static boolean canGetServerLevel() {
        if (isClientSide()) {
            return Minecraft.getInstance().level != null;
        }
        var server = getMinecraftServer();
        return server != null &&
                !(server.isStopped() || server.isShutdown() || !server.isRunning() || server.isCurrentlySaving());
    }

    /** Returns the game instance directory supplied by NeoForge, including during early Mixin loading. */
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /** Optional integrations cached after Mod discovery. Viewer selection additionally follows frontend policy. */
    @NullMarked
    public enum MOD {

        JECHARACTERS("jecharacters"),
        JEI("jei"),
        EMI("emi"),
        AE2_JEI_INTEGRATION("ae2jeiintegration"),
        AE2WTLIB("ae2wtlib"),
        CURIOS("curios"),
        XAERO_WORLD_MAP("xaeroworldmap", "xaerolib"),
        FTB_CHUNKS("ftbchunks"),
        FTB_TEAMS("ftbteams"),
        FTB_QUESTS("ftbquests"),
        FTB_ULTIMINE("ftbultimine"),
        APPFLUX("appflux"),
        DRACONIC_EVOLUTION("draconicevolution"),
        NEO_ECO_AE("neoecoae"),
        EXTENDED_AE("extendedae"),
        EXTENDED_AE_PLUS("extendedae_plus"),
        ADVANCED_AE("advanced_ae"),
        AE2CS("ae2cs"),
        APPLIED_CREATE("appliedcreate"),
        CREATE("create"),
        FLYWHEEL("flywheel"),
        MEGA_CELLS("megacells"),
        ASTRAL_SORCERY("astralsorcery"),
        GOETY("goety"),
        FORBIDDEN_ARCANUS("forbidden_arcanus"),
        NATURES_AURA("naturesaura");

        /** True only when all IDs required by this integration were discovered; stable for this launch. */
        @Getter
        private final boolean loaded;

        MOD(String... modIds) {
            boolean present = true;
            for (String modId : modIds) {
                present &= Data_Energistics.isModLoaded(modId);
            }
            this.loaded = present;
        }

        /** Selects the native JEI frontend only when EMI is absent, matching plugin registration policy. */
        public static boolean isJEILoaded() {
            return JEI.isLoaded() && !EMI.isLoaded();
        }

        /** Checks EMI presence and the current client toggle; does not access EMI client classes on a server. */
        public static boolean isEMILoaded() {
            return EMI.isLoaded() && (!Data_Energistics.isClientSide() || EmiConfig.enabled);
        }
    }
}
