package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Discovers and invokes the single public Data Energistics plugin entrypoint during common setup.
 *
 * <p>
 * This class is the sole reflection boundary. Runtime provider matching and dispatch consume only the frozen values
 * produced here and never retain plugin classes, constructors, scan records, or mutable staging registries.
 * </p>
 */
public final class DataEnergisticsEntrypointLoader {

    private static final String REQUIRED_MODS_MEMBER = "requiredMods";
    private static volatile @Nullable DataEnergisticsRegistrySnapshot publishedSnapshot;

    private DataEnergisticsEntrypointLoader() {}

    /**
     * Loads plugins in deterministic owning-mod and class order, isolates failures, and publishes one snapshot.
     *
     * @return immutable runtime registry snapshot
     */
    public static synchronized DataEnergisticsRegistrySnapshot initialize() {
        if (publishedSnapshot != null) {
            throw new IllegalStateException("Data Energistics entrypoints have already been initialized");
        }

        PluginRegistrationAccumulator registry = new PluginRegistrationAccumulator();
        ObjectList<EntrypointCandidate> candidates = discoverCandidates(false);
        int loaded = 0;
        for (EntrypointCandidate candidate : candidates) {
            PluginRegistrationAccumulator.@Nullable PluginStaging staging = null;
            try {
                DataEnergisticsPlugin plugin = instantiate(candidate, DataEnergisticsPlugin.class);
                staging = registry.createStaging(candidate.owningModId(), candidate.className());
                plugin.register(staging);
                registry.commit(staging);
                loaded++;
            } catch (Exception | LinkageError exception) {
                if (staging != null) {
                    staging.discard();
                }
                Data_Energistics.LOGGER.error(
                        "Failed to register Data Energistics plugin {} owned by mod {}; discarded all of its staged registrations",
                        candidate.className(),
                        candidate.owningModId(),
                        exception);
            }
        }

        publishedSnapshot = registry.freeze();
        Data_Energistics.LOGGER.info(
                "Loaded {} of {} Data Energistics plugins: {} terminals, {} provider declarations, {} provider workstation sources, {} machine capacity declarations, {} pattern upload workstation declarations, {} adaptive provider definitions, {} Trinity recipe resolvers, {} Trinity search contributors, {} virtual output adapters",
                loaded,
                candidates.size(),
                publishedSnapshot.universalTerminalRegistrations().size(),
                publishedSnapshot.patternProviderRegistrations().size(),
                publishedSnapshot.patternProviderWorkstationSourceRegistrations().size(),
                publishedSnapshot.craftingMachineCapacityRegistrations().size(),
                publishedSnapshot.patternUploadWorkstationRegistrations().size(),
                publishedSnapshot.adaptivePatternProviderRegistrations().size(),
                publishedSnapshot.trinityPatternRecipeResolverCount(),
                publishedSnapshot.trinityPatternSearchTermRegistrations().size(),
                publishedSnapshot.virtualCraftingOutputAdapters().size());
        return publishedSnapshot;
    }

    /**
     * Returns the immutable registry after common setup has completed.
     */
    public static DataEnergisticsRegistrySnapshot snapshot() {
        DataEnergisticsRegistrySnapshot current = publishedSnapshot;
        if (current == null) {
            throw new IllegalStateException("Data Energistics entrypoints have not been initialized yet");
        }
        return current;
    }

    /**
     * Reads only marker annotations and canonical owning mod IDs from NeoForge scan data.
     * The phase flag is checked before annotated classes are resolved. Call with false during common setup;
     * the client bootstrap calls with true only after common setup. No plugin code executes during discovery.
     */
    public static ObjectList<EntrypointCandidate> discoverCandidates(boolean clientOnly) {
        ObjectArrayList<EntrypointCandidate> candidates = new ObjectArrayList<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            List<ModFileScanData.AnnotationData> annotations = scanData
                    .getAnnotatedBy(DataEnergisticsEntrypoint.class, ElementType.TYPE)
                    .toList();
            if (annotations.isEmpty()) {
                continue;
            }
            try {
                String owningModId = resolveOwningModId(scanData);
                for (ModFileScanData.AnnotationData annotation : annotations) {
                    try {
                        Object encodedClientOnly = annotation.annotationData().get("clientOnly");
                        boolean isClientOnly = encodedClientOnly instanceof Boolean value && value;
                        if (isClientOnly != clientOnly) {
                            continue;
                        }
                        List<String> missingMods = requiredMods(annotation).stream()
                                .filter(Predicate.not(ModList.get()::isLoaded))
                                .toList();
                        if (!missingMods.isEmpty()) {
                            Data_Energistics.LOGGER.debug(
                                    "Skipping Data Energistics plugin {} owned by mod {}; missing required mods {}",
                                    annotation.clazz().getClassName(),
                                    owningModId,
                                    missingMods);
                            continue;
                        }
                        candidates.add(new EntrypointCandidate(owningModId, annotation.clazz().getClassName()));
                    } catch (RuntimeException exception) {
                        Data_Energistics.LOGGER.error(
                                "Failed to read required mods for Data Energistics entrypoint {} owned by mod {}; " + "the entrypoint will be ignored",
                                annotation.clazz().getClassName(),
                                owningModId,
                                exception);
                    }
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to resolve the owning mod for {} Data Energistics entrypoint annotation(s); " + "those entrypoints will be ignored",
                        annotations.size(),
                        exception);
            }
        }
        candidates.sort(Comparator.comparing(EntrypointCandidate::owningModId)
                .thenComparing(EntrypointCandidate::className));
        return ObjectLists.unmodifiable(new ObjectArrayList<>(new ObjectLinkedOpenHashSet<>(candidates)));
    }

    /**
     * Decodes the marker's string-array member without resolving the annotated plugin class.
     */
    static ObjectList<String> requiredMods(ModFileScanData.AnnotationData annotation) {
        @Nullable
        Object encoded = annotation.annotationData().get(REQUIRED_MODS_MEMBER);
        if (encoded == null) {
            return ObjectList.of();
        }
        if (!(encoded instanceof List<?> values)) {
            throw new IllegalArgumentException("Data Energistics requiredMods scan value is not an array");
        }

        ObjectLinkedOpenHashSet<String> requiredMods = new ObjectLinkedOpenHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String modId) || modId.isBlank()) {
                throw new IllegalArgumentException("Data Energistics requiredMods contains an invalid mod ID");
            }
            requiredMods.add(modId);
        }
        ObjectArrayList<String> sorted = new ObjectArrayList<>(requiredMods);
        sorted.sort(Comparator.naturalOrder());
        return ObjectLists.unmodifiable(sorted);
    }

    /**
     * Resolves the only mod descriptor that can unambiguously own entrypoints in one scanned mod file.
     */
    private static String resolveOwningModId(ModFileScanData scanData) {
        List<IModFileInfo> fileInfos = scanData.getIModInfoData();
        if (fileInfos.isEmpty()) {
            throw new IllegalStateException("A mod file containing a Data Energistics entrypoint has no mod metadata");
        }
        List<String> owningModIds = fileInfos.stream()
                .flatMap(fileInfo -> fileInfo.getMods().stream())
                .map(IModInfo::getModId)
                .distinct()
                .sorted()
                .toList();
        if (owningModIds.isEmpty()) {
            throw new IllegalStateException("A mod file containing a Data Energistics entrypoint declares no owning mod");
        }
        if (owningModIds.size() != 1) {
            throw new IllegalStateException(
                    "A mod file containing a Data Energistics entrypoint has ambiguous owners: " + owningModIds);
        }
        return owningModIds.getFirst();
    }

    /**
     * Validates the public plugin contract before invoking its no-argument constructor.
     * Only setup-phase loaders may call this; the supplied contract must match the candidate's phase.
     * Invalid plugin classes and inaccessible constructors throw rather than returning an incomplete plugin.
     */
    public static <P> P instantiate(EntrypointCandidate candidate, Class<P> contract) throws ReflectiveOperationException {
        Class<?> rawClass = Class.forName(
                candidate.className(), false, DataEnergisticsEntrypointLoader.class.getClassLoader());
        if (!contract.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException("Entrypoint does not implement " + contract.getSimpleName() + ": " + candidate.className());
        }
        int modifiers = rawClass.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers)) {
            throw new IllegalArgumentException("Entrypoint must be a public concrete class: " + candidate.className());
        }

        Class<? extends P> pluginClass = rawClass.asSubclass(contract);
        Constructor<? extends P> constructor = pluginClass.getConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
            throw new IllegalArgumentException("Entrypoint must expose a public no-argument constructor: " + candidate.className());
        }
        return constructor.newInstance();
    }

    /**
     * Stable discovery key used exclusively before plugin instantiation.
     */
    public record EntrypointCandidate(String owningModId, String className) {}
}
