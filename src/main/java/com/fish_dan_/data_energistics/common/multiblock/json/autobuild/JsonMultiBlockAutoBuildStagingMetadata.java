package com.fish_dan_.data_energistics.common.multiblock.json.autobuild;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/**
 * Raw auto-build staging declarations read from one JSON multiblock metadata object.
 *
 * <p>
 * Symbols deliberately refer to the surrounding predicate declarations instead of repeating block or item ids. The
 * loader resolves these declarations into immutable runtime candidates only after every predicate is available.
 * </p>
 */
public final class JsonMultiBlockAutoBuildStagingMetadata {

    private static final String AUTO_BUILD_STAGING_PROPERTY = "auto_build_staging";
    private static final String BLOCK_SYMBOLS_PROPERTY = "block_symbols";
    private static final String REPLACEABLE_COMPARTMENT_SYMBOLS_PROPERTY = "replaceable_compartment_symbols";
    private static final String PHYSICAL_BLOCK_SYMBOLS_PROPERTY = "physical_block_symbols";
    private static final String PART_HOST_SYMBOLS_PROPERTY = "part_host_symbols";
    private static final JsonMultiBlockAutoBuildStagingMetadata NONE = new JsonMultiBlockAutoBuildStagingMetadata(
            ObjectSets.emptySet(),
            ObjectSets.emptySet(),
            ObjectSets.emptySet(),
            ObjectSets.emptySet());

    private final ObjectSet<String> blockSymbols;
    private final ObjectSet<String> replaceableCompartmentSymbols;
    private final ObjectSet<String> physicalBlockSymbols;
    private final ObjectSet<String> partHostSymbols;

    private JsonMultiBlockAutoBuildStagingMetadata(ObjectSet<String> blockSymbols,
                                                   ObjectSet<String> replaceableCompartmentSymbols,
                                                   ObjectSet<String> physicalBlockSymbols,
                                                   ObjectSet<String> partHostSymbols) {
        this.blockSymbols = ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(blockSymbols));
        this.replaceableCompartmentSymbols = ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(replaceableCompartmentSymbols));
        this.physicalBlockSymbols = ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(physicalBlockSymbols));
        this.partHostSymbols = ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(partHostSymbols));
    }

    public static JsonMultiBlockAutoBuildStagingMetadata none() {
        return NONE;
    }

    public static JsonMultiBlockAutoBuildStagingMetadata read(JsonObject metadata, ResourceLocation resourceId) {
        if (!metadata.has(AUTO_BUILD_STAGING_PROPERTY)) {
            return NONE;
        }
        JsonElement stagingElement = metadata.get(AUTO_BUILD_STAGING_PROPERTY);
        if (!stagingElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock auto_build_staging must be an object: " + resourceId);
        }
        JsonObject staging = stagingElement.getAsJsonObject();
        ObjectSet<String> blockSymbols = readSymbols(staging, BLOCK_SYMBOLS_PROPERTY, resourceId);
        ObjectSet<String> replaceableCompartmentSymbols = readSymbols(
                staging,
                REPLACEABLE_COMPARTMENT_SYMBOLS_PROPERTY,
                resourceId);
        ObjectSet<String> physicalBlockSymbols = readSymbols(staging, PHYSICAL_BLOCK_SYMBOLS_PROPERTY, resourceId);
        ObjectSet<String> partHostSymbols = readSymbols(staging, PART_HOST_SYMBOLS_PROPERTY, resourceId);
        if (!blockSymbols.containsAll(physicalBlockSymbols)) {
            throw new IllegalArgumentException("JSON multiblock physical_block_symbols must be declared in block_symbols: " +
                    resourceId);
        }
        return new JsonMultiBlockAutoBuildStagingMetadata(
                blockSymbols,
                replaceableCompartmentSymbols,
                physicalBlockSymbols,
                partHostSymbols);
    }

    public boolean isEmpty() {
        return this.blockSymbols.isEmpty() && this.replaceableCompartmentSymbols.isEmpty() &&
                this.partHostSymbols.isEmpty();
    }

    public ObjectSet<String> blockSymbols() {
        return this.blockSymbols;
    }

    public ObjectSet<String> replaceableCompartmentSymbols() {
        return this.replaceableCompartmentSymbols;
    }

    public ObjectSet<String> physicalBlockSymbols() {
        return this.physicalBlockSymbols;
    }

    public ObjectSet<String> partHostSymbols() {
        return this.partHostSymbols;
    }

    private static ObjectSet<String> readSymbols(JsonObject staging, String property, ResourceLocation resourceId) {
        if (!staging.has(property)) {
            return ObjectSets.emptySet();
        }
        JsonElement symbolsElement = staging.get(property);
        if (!symbolsElement.isJsonArray()) {
            throw new IllegalArgumentException("JSON multiblock auto_build_staging." + property +
                    " must be an array: " + resourceId);
        }
        ObjectLinkedOpenHashSet<String> symbols = new ObjectLinkedOpenHashSet<>();
        for (JsonElement symbolElement : symbolsElement.getAsJsonArray()) {
            if (!symbolElement.isJsonPrimitive() || !symbolElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("JSON multiblock auto-build staging symbols must be strings: " +
                        resourceId);
            }
            String symbol = symbolElement.getAsString();
            if (symbol.isBlank() || symbol.length() != 1) {
                throw new IllegalArgumentException("JSON multiblock auto-build staging symbols must be one non-blank " +
                        "character: " + resourceId);
            }
            if (!symbols.add(symbol)) {
                throw new IllegalArgumentException("JSON multiblock auto-build staging symbol is declared more than once: '" +
                        symbol + "' in " + resourceId);
            }
        }
        return ObjectSets.unmodifiable(symbols);
    }
}
