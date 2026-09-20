package com.fish_dan_.data_energistics.common.crafting.dynamic;

import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutput;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputSemantics;
import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Frozen adapter runtime that resolves and validates one outer pattern before a provider receives its inputs.
 */
public final class DynamicCraftingOutputAdapters {

    private static volatile List<DynamicCraftingOutputAdapter> adapters = List.of();
    private static boolean installed;

    private DynamicCraftingOutputAdapters() {}

    /**
     * Installs the immutable plugin snapshot exactly once during common setup.
     *
     * @param values adapters in deterministic plugin and declaration order
     */
    public static synchronized void install(List<DynamicCraftingOutputAdapter> values) {
        if (installed) {
            throw new IllegalStateException("Dynamic crafting output adapters have already been installed");
        }
        ObjectArrayList<DynamicCraftingOutputAdapter> validated = new ObjectArrayList<>(values.size());
        for (DynamicCraftingOutputAdapter adapter : values) {
            if (adapter == null || adapter.id() == null) {
                throw new IllegalStateException("A dynamic crafting output adapter requires a stable ID");
            }
            validated.add(adapter);
        }
        adapters = List.copyOf(validated);
        installed = true;
    }

    /**
     * Resolves exactly one claiming adapter and verifies every declaration against the outer pattern's real outputs.
     *
     * @param details original outer pattern object selected for provider dispatch
     * @return validated semantics, or empty when all outputs remain exact
     */
    public static Optional<ResolvedSemantics> resolve(IPatternDetails details) {
        requireInstalled();
        if (details == null) {
            throw new DynamicCraftingOutputResolutionException("Dynamic output resolution requires pattern details");
        }

        if (details.getDefinition().get(AEComponents.ENCODED_PROCESSING_PATTERN) != null) {
            var explicit = EncodedPatternDynamicOutput.resolveAll(details);
            return explicit.isEmpty() ? Optional.empty() : Optional.of(new ResolvedSemantics(EncodedPatternDynamicOutput.SOURCE_ID, explicit));
        }

        ResolvedSemantics claimed = null;
        for (DynamicCraftingOutputAdapter adapter : adapters) {
            Optional<DynamicCraftingOutputSemantics> candidate;
            try {
                candidate = adapter.resolve(details);
            } catch (RuntimeException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapter.id() + " failed for pattern " + details.getDefinition(),
                        exception);
            }
            if (candidate == null) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapter.id() + " returned null for pattern " +
                                details.getDefinition());
            }
            if (candidate.isEmpty()) {
                continue;
            }
            if (claimed != null) {
                throw new DynamicCraftingOutputResolutionException(
                        "Multiple dynamic output adapters claimed pattern " + details.getDefinition() + ": " +
                                claimed.adapterId() + " and " + adapter.id());
            }
            claimed = validate(details, adapter.id(), candidate.orElseThrow());
        }
        return Optional.ofNullable(claimed);
    }

    private static ResolvedSemantics validate(IPatternDetails details,
                                              ResourceLocation adapterId,
                                              DynamicCraftingOutputSemantics semantics) {
        Object2LongLinkedOpenHashMap<AEKey> declared = new Object2LongLinkedOpenHashMap<>();
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.what() == null || output.amount() <= 0L) {
                throw new DynamicCraftingOutputResolutionException(
                        "Pattern " + details.getDefinition() + " exposes an invalid physical output");
            }
            try {
                declared.mergeLong(output.what(), output.amount(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Pattern " + details.getDefinition() + " overflows its physical output amount",
                        exception);
            }
        }

        Object2LongLinkedOpenHashMap<AEKey> claimed = new Object2LongLinkedOpenHashMap<>();
        Map<Item, AEItemKey> domains = new Object2ObjectOpenHashMap<>();
        for (DynamicCraftingOutput output : semantics.outputsFast()) {
            if (output.matchMode() == ProcessingMatchMode.EXACT ||
                    !(output.plannedOutput().what() instanceof AEItemKey plannedKey)) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId + " declared an unsupported output match");
            }
            AEItemKey existingDomain = domains.putIfAbsent(plannedKey.getItem(), plannedKey);
            if (existingDomain != null && !existingDomain.equals(plannedKey)) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId +
                                " declared multiple component templates for the same registered item");
            }
            try {
                claimed.mergeLong(plannedKey, output.plannedOutput().amount(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId + " overflows a declared output amount",
                        exception);
            }
        }
        claimed.forEach((key, amount) -> {
            long available = declared.getOrDefault(key, 0L);
            if (amount > available) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId + " declared absent output " + key +
                                " x" + amount + " for pattern " + details.getDefinition());
            }
        });
        return new ResolvedSemantics(adapterId, semantics.outputsFast());
    }

    private static void requireInstalled() {
        if (!installed) {
            throw new IllegalStateException("Dynamic crafting output adapters are not installed");
        }
    }

    /**
     * Validated declarations for one logical provider push.
     *
     * @param adapterId stable persisted source identity
     * @param outputs   dynamic physical outputs in deterministic declaration order
     */
    public record ResolvedSemantics(ResourceLocation adapterId,
                                    ObjectList<DynamicCraftingOutput> outputs) {

        public ResolvedSemantics(ResourceLocation adapterId, List<DynamicCraftingOutput> outputs) {
            this(adapterId, new ObjectImmutableList<>(outputs));
        }

        /**
         * Isolates the adapter-owned list from runtime callers.
         */
        public ResolvedSemantics {
            outputs = new ObjectImmutableList<>(outputs);
        }
    }
}
