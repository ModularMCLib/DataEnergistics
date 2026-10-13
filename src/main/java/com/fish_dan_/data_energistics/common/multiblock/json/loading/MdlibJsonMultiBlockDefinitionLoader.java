package com.fish_dan_.data_energistics.common.multiblock.json.loading;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.multiblock.json.autobuild.JsonMultiBlockAutoBuildStaging;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockMetadata;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.ResolvedJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.matching.JsonMultiBlockCompartmentPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.matching.JsonMultiBlockPlacementPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.matching.JsonMultiBlockReplaceableCompartmentPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.matching.JsonMultiBlockStatePropertiesPredicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import com.modularmc.mdl.api.multiblock.json.StructurePatternResolver;
import com.modularmc.mdl.api.multiblock.json.StructurePatternResolver.StringArrayDefinition;
import com.modularmc.mdl.api.multiblock.json.StructurePatternResolver.Unit;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Loader backed by MDLib's GregTech-style JSON resolver.
 */
public final class MdlibJsonMultiBlockDefinitionLoader implements JsonMultiBlockDefinitionLoader {

    public static final String DIRECTORY = "multiblock";
    private static final String PREDICATES_PROPERTY = "predicates";
    private static final String AISLES_PROPERTY = "aisles";
    private static final String SLICES_PROPERTY = "slices";
    private static final String TYPE_PROPERTY = "type";
    private static final String PREDICATE_PROPERTY = "predicate";
    private static final String BLOCK_PROPERTY = "block";
    private static final String BLOCKS_PROPERTY = "blocks";
    private static final String BLOCK_STATES_PROPERTY = "block_states";
    private static final String PROPERTIES_PROPERTY = "properties";
    private static final String BLOCKS_PREDICATE_TYPE = "blocks";
    private static final String BLOCK_STATES_PREDICATE_TYPE = "block_states";
    private static final String FALLBACK_BLOCK_PREDICATE_TYPE = "mdlib:blocks";
    private static final String ANY_PREDICATE_TYPE = "mdlib:any";
    private static final String AIR_PREDICATE_TYPE = "air";
    private static final String AIR_BLOCK_ID = "minecraft:air";
    private static final char SPACE_SYMBOL = ' ';
    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final FileToIdConverter FILE_TO_ID = FileToIdConverter.json(DIRECTORY);

    @Override
    public Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> load(ResourceManager resourceManager) {
        Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectMap<JsonMultiBlockStructureKey, ResourceLocation> sources = new Object2ObjectLinkedOpenHashMap<>();
        for (var entry : FILE_TO_ID.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation resourceId = FILE_TO_ID.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                putDefinition(definitions, sources, parse(resourceId, reader), resourceId);
            } catch (IOException exception) {
                String message = "Could not read JSON multiblock resource " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            } catch (RuntimeException exception) {
                String message = "Could not parse JSON multiblock resource " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            }
        }
        return Object2ObjectMaps.unmodifiable(definitions);
    }

    @Override
    public Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> load(Object2ObjectMap<ResourceLocation, String> resources) {
        Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectMap<JsonMultiBlockStructureKey, ResourceLocation> sources = new Object2ObjectLinkedOpenHashMap<>();
        for (Object2ObjectMap.Entry<ResourceLocation, String> entry : resources.object2ObjectEntrySet()) {
            ResourceLocation resourceId = entry.getKey();
            String json = entry.getValue();
            try (Reader reader = new StringReader(json)) {
                putDefinition(definitions, sources, parse(resourceId, reader), resourceId);
            } catch (RuntimeException exception) {
                String message = "Could not parse JSON multiblock resource " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            } catch (IOException exception) {
                String message = "Could not close JSON multiblock reader for " + resourceId;
                LOGGER.error(message, exception);
                throw new IllegalStateException(message, exception);
            }
        }
        return Object2ObjectMaps.unmodifiable(definitions);
    }

    @Override
    public JsonMultiBlockDefinition parse(ResourceLocation resourceId, Reader reader) {
        JsonObject root = readRoot(reader, resourceId);
        JsonMultiBlockMetadata metadata = JsonMultiBlockMetadata.read(root, resourceId);
        return parseDefinition(resourceId, root, metadata);
    }

    private static JsonMultiBlockDefinition parseDefinition(ResourceLocation resourceId,
                                                            JsonObject root,
                                                            JsonMultiBlockMetadata metadata) {
        JsonMultiBlockStructureKey key = JsonMultiBlockResourceKeyResolver.resolve(resourceId);
        JsonObject patternRoot = root.deepCopy();
        patternRoot.remove(JsonMultiBlockMetadata.METADATA_PROPERTY);
        sanitizeBlockPredicates(resourceId, patternRoot);
        JsonMultiBlockCompartmentPredicate.registerType();
        JsonMultiBlockReplaceableCompartmentPredicate.registerType();
        JsonMultiBlockStatePropertiesPredicate.registerType();
        JsonMultiBlockPlacementPredicate.registerType();
        applySlabBlockPredicates(patternRoot);
        applyPartialBlockStatePredicates(patternRoot);
        applyCompartmentPredicates(resourceId, patternRoot, metadata.compartmentTypes());
        applyReplaceableCompartmentPredicates(resourceId, patternRoot, metadata.replaceableCompartmentTypes());
        StringArrayDefinition definition = StructurePatternResolver.parseDefinition(patternRoot);
        BlockPattern pattern = buildPattern(definition, metadata);
        JsonMultiBlockAutoBuildStaging autoBuildStaging = JsonMultiBlockAutoBuildStaging.resolve(
                resourceId,
                metadata.autoBuildStagingMetadata(),
                definition);
        return new ResolvedJsonMultiBlockDefinition(
                key,
                pattern,
                metadata.displayNameTranslationKey(),
                metadata.compartmentTypes(),
                metadata.replaceableCompartmentTypes(),
                autoBuildStaging);
    }

    private static BlockPattern buildPattern(StringArrayDefinition definition, JsonMultiBlockMetadata metadata) {
        FactoryBlockPattern builder = FactoryBlockPattern.start(
                metadata.structureDir().charDir(),
                metadata.structureDir().stringDir(),
                metadata.structureDir().aisleDir());
        for (Unit unit : definition.units()) {
            if (unit.minRepeat() != 1 || unit.maxRepeat() != 1 || unit.slices().size() > 1) {
                builder.beginRepeatable();
                unit.slices().forEach(builder::aisle);
                builder.endRepeatable(unit.minRepeat(), unit.maxRepeat());
            } else {
                builder.aisle(unit.slices().getFirst());
            }
        }
        for (var entry : definition.predicates().entrySet()) {
            if (entry.getKey() == SPACE_SYMBOL) {
                continue;
            }
            builder.where(entry.getKey(), new TraceabilityPredicate(entry.getValue()));
        }
        return builder.build();
    }

    private static void applySlabBlockPredicates(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (isSlabBlockStatePredicate(object)) {
                replaceWithBlockPredicate(object);
            }
            for (var entry : object.entrySet()) {
                applySlabBlockPredicates(entry.getValue());
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                applySlabBlockPredicates(child);
            }
        }
    }

    private static boolean isSlabBlockStatePredicate(JsonObject object) {
        if (!isBlockStatePredicate(object)) {
            return false;
        }
        ObjectList<String> blockIds = blockIds(object);
        if (blockIds.isEmpty()) {
            return false;
        }
        for (String blockId : blockIds) {
            ResourceLocation id = ResourceLocation.tryParse(blockId);
            if (id == null) {
                return false;
            }
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (!id.equals(BuiltInRegistries.BLOCK.getKey(block)) || !(block instanceof SlabBlock)) {
                return false;
            }
        }
        return true;
    }

    private static void replaceWithBlockPredicate(JsonObject predicate) {
        ObjectList<String> blockIds = new ObjectArrayList<>(new ObjectLinkedOpenHashSet<>(blockIds(predicate)));
        predicate.addProperty(TYPE_PROPERTY, FALLBACK_BLOCK_PREDICATE_TYPE);
        predicate.remove(BLOCK_STATES_PROPERTY);
        predicate.remove(PROPERTIES_PROPERTY);
        predicate.remove(BLOCK_PROPERTY);
        predicate.remove(BLOCKS_PROPERTY);
        if (blockIds.size() == 1) {
            predicate.addProperty(BLOCK_PROPERTY, blockIds.getFirst());
            return;
        }
        JsonArray blocks = new JsonArray();
        for (String blockId : blockIds) {
            blocks.add(blockId);
        }
        predicate.add(BLOCKS_PROPERTY, blocks);
    }

    private static void applyPartialBlockStatePredicates(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (isBlockStatePredicate(object)) {
                object.addProperty(TYPE_PROPERTY, JsonMultiBlockStatePropertiesPredicate.TYPE.toString());
            }
            for (var entry : object.entrySet()) {
                applyPartialBlockStatePredicates(entry.getValue());
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                applyPartialBlockStatePredicates(child);
            }
        }
    }

    private static boolean isBlockStatePredicate(JsonObject object) {
        JsonElement typeElement = object.get(TYPE_PROPERTY);
        if (typeElement == null || !typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
            return false;
        }
        ResourceLocation type = ResourceLocation.tryParse(typeElement.getAsString());
        return type != null && BLOCK_STATES_PREDICATE_TYPE.equals(type.getPath());
    }

    private static void applyCompartmentPredicates(ResourceLocation resourceId,
                                                   JsonObject root,
                                                   Object2ObjectMap<String, CompartmentType> compartmentTypes) {
        if (compartmentTypes.isEmpty()) {
            return;
        }
        JsonObject predicates = getOrCreatePredicates(root, resourceId);
        for (Object2ObjectMap.Entry<String, CompartmentType> entry : compartmentTypes.object2ObjectEntrySet()) {
            String symbol = entry.getKey();
            if (!patternUsesSymbol(root, symbol)) {
                throw new IllegalArgumentException("JSON multiblock compartment symbol '" + symbol +
                        "' is not used by pattern: " + resourceId);
            }
            JsonObject compartmentPredicate = new JsonObject();
            compartmentPredicate.addProperty(TYPE_PROPERTY, JsonMultiBlockCompartmentPredicate.TYPE.toString());
            compartmentPredicate.addProperty("compartment", entry.getValue().id());
            JsonElement existingPredicate = predicates.get(symbol);
            if (existingPredicate != null && !existingPredicate.isJsonNull()) {
                if (!existingPredicate.isJsonObject()) {
                    throw new IllegalArgumentException("JSON multiblock predicate for compartment symbol '" + symbol +
                            "' must be an object: " + resourceId);
                }
                compartmentPredicate.add("predicate", existingPredicate.deepCopy());
            }
            predicates.add(symbol, compartmentPredicate);
        }
    }

    private static void applyReplaceableCompartmentPredicates(ResourceLocation resourceId,
                                                              JsonObject root,
                                                              Object2ObjectMap<String, ObjectSet<CompartmentType>> replaceableCompartmentTypes) {
        if (replaceableCompartmentTypes.isEmpty()) {
            return;
        }
        JsonObject predicates = getOrCreatePredicates(root, resourceId);
        for (Object2ObjectMap.Entry<String, ObjectSet<CompartmentType>> entry : replaceableCompartmentTypes.object2ObjectEntrySet()) {
            String symbol = entry.getKey();
            if (!patternUsesSymbol(root, symbol)) {
                throw new IllegalArgumentException("JSON multiblock replaceable compartment symbol '" + symbol +
                        "' is not used by pattern: " + resourceId);
            }
            JsonElement existingPredicate = predicates.get(symbol);
            if (existingPredicate == null || existingPredicate.isJsonNull() || !existingPredicate.isJsonObject()) {
                throw new IllegalArgumentException("JSON multiblock replaceable compartment symbol '" + symbol +
                        "' requires an existing object predicate: " + resourceId);
            }

            JsonObject replaceablePredicate = new JsonObject();
            replaceablePredicate.addProperty(TYPE_PROPERTY, JsonMultiBlockReplaceableCompartmentPredicate.TYPE.toString());
            JsonArray compartments = new JsonArray();
            for (CompartmentType type : entry.getValue()) {
                compartments.add(type.id());
            }
            replaceablePredicate.add("compartments", compartments);
            replaceablePredicate.add(PREDICATE_PROPERTY, existingPredicate.deepCopy());
            predicates.add(symbol, replaceablePredicate);
        }
    }

    private static JsonObject getOrCreatePredicates(JsonObject root, ResourceLocation resourceId) {
        JsonElement predicatesElement = root.get(PREDICATES_PROPERTY);
        if (predicatesElement == null || predicatesElement.isJsonNull()) {
            JsonObject predicates = new JsonObject();
            root.add(PREDICATES_PROPERTY, predicates);
            return predicates;
        }
        if (!predicatesElement.isJsonObject()) {
            throw new IllegalArgumentException("JSON multiblock predicates must be an object: " + resourceId);
        }
        return predicatesElement.getAsJsonObject();
    }

    private static boolean patternUsesSymbol(JsonObject root, String symbol) {
        char expected = symbol.charAt(0);
        JsonElement aislesElement = root.get(AISLES_PROPERTY);
        if (aislesElement == null || !aislesElement.isJsonArray()) {
            return false;
        }
        for (JsonElement unitElement : aislesElement.getAsJsonArray()) {
            if (!unitElement.isJsonObject()) {
                continue;
            }
            JsonElement slicesElement = unitElement.getAsJsonObject().get(SLICES_PROPERTY);
            if (slicesElement == null || !slicesElement.isJsonArray()) {
                continue;
            }
            for (JsonElement sliceElement : slicesElement.getAsJsonArray()) {
                if (!sliceElement.isJsonArray()) {
                    continue;
                }
                for (JsonElement rowElement : sliceElement.getAsJsonArray()) {
                    if (rowElement.isJsonPrimitive() &&
                            rowElement.getAsJsonPrimitive().isString() &&
                            rowElement.getAsString().indexOf(expected) >= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static JsonObject readRoot(Reader reader, ResourceLocation resourceId) {
        String source = readSource(reader, resourceId);
        validateUniqueCompartmentSymbols(source, resourceId);
        JsonObject root = JsonParser.parseString(source).getAsJsonObject();
        if (root == null) {
            throw new IllegalArgumentException("JSON multiblock root must be an object");
        }
        return root;
    }

    private static String readSource(Reader reader, ResourceLocation resourceId) {
        try {
            StringWriter writer = new StringWriter();
            reader.transferTo(writer);
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read JSON multiblock source: " + resourceId, exception);
        }
    }

    private static void validateUniqueCompartmentSymbols(String source, ResourceLocation resourceId) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String property = reader.nextName();
                if (JsonMultiBlockMetadata.METADATA_PROPERTY.equals(property) &&
                        reader.peek() == JsonToken.BEGIN_OBJECT) {
                    validateMetadataObject(reader, resourceId);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect JSON multiblock metadata: " + resourceId, exception);
        }
    }

    private static void validateMetadataObject(JsonReader reader, ResourceLocation resourceId) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String property = reader.nextName();
            if ("compartments".equals(property) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                validateCompartmentSymbols(reader, resourceId);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static void validateCompartmentSymbols(JsonReader reader, ResourceLocation resourceId) throws IOException {
        ObjectSet<String> symbols = new ObjectOpenHashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String symbol = reader.nextName();
            if (!symbols.add(symbol)) {
                throw new IllegalArgumentException("Duplicate JSON multiblock compartment symbol '" + symbol +
                        "': " + resourceId);
            }
            reader.skipValue();
        }
        reader.endObject();
    }

    private static void sanitizeBlockPredicates(ResourceLocation resourceId, JsonObject root) {
        JsonElement predicatesElement = root.get(PREDICATES_PROPERTY);
        if (predicatesElement == null || predicatesElement.isJsonNull() || !predicatesElement.isJsonObject()) {
            return;
        }
        JsonObject predicates = predicatesElement.getAsJsonObject();
        ObjectList<String> predicateKeys = new ObjectArrayList<>(predicates.keySet());
        for (String predicateKey : predicateKeys) {
            JsonElement predicateElement = predicates.get(predicateKey);
            sanitizePredicate(resourceId, predicateKey, predicateElement);
        }
    }

    private static void sanitizePredicate(ResourceLocation resourceId, String predicateKey, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject predicate = element.getAsJsonObject();
            if (isAirPredicate(predicate)) {
                replaceWithAnyPredicate(predicate);
                return;
            }
            if (isBlockBackedPredicate(predicate)) {
                sanitizeBlockBackedPredicate(resourceId, predicateKey, predicate);
                return;
            }
            for (var entry : predicate.entrySet()) {
                sanitizePredicate(resourceId, predicateKey, entry.getValue());
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                sanitizePredicate(resourceId, predicateKey, child);
            }
        }
    }

    private static void sanitizeBlockBackedPredicate(ResourceLocation resourceId,
                                                     String predicateKey,
                                                     JsonObject predicate) {
        if (usesAirBlock(predicate)) {
            replaceWithAnyPredicate(predicate);
            return;
        }
        ObjectList<String> missingBlockIds = missingBlockIds(predicate);
        if (missingBlockIds.isEmpty()) {
            return;
        }
        replaceWithAnyPredicate(predicate);
        for (String missingBlockId : missingBlockIds) {
            LOGGER.warn(
                    "JSON multiblock resource {} predicate '{}' references missing block id {}; replacing predicate with {}",
                    resourceId,
                    predicateKey,
                    missingBlockId,
                    ANY_PREDICATE_TYPE);
        }
    }

    private static void replaceWithAnyPredicate(JsonObject predicate) {
        predicate.addProperty(TYPE_PROPERTY, ANY_PREDICATE_TYPE);
        predicate.remove(BLOCK_STATES_PROPERTY);
        predicate.remove(PROPERTIES_PROPERTY);
        predicate.remove(BLOCK_PROPERTY);
        predicate.remove(BLOCKS_PROPERTY);
    }

    private static boolean isAirPredicate(JsonObject predicate) {
        JsonElement typeElement = predicate.get(TYPE_PROPERTY);
        if (typeElement == null || !typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
            return false;
        }
        ResourceLocation type = ResourceLocation.tryParse(typeElement.getAsString());
        return type != null && AIR_PREDICATE_TYPE.equals(type.getPath());
    }

    private static boolean isBlockBackedPredicate(JsonObject predicate) {
        JsonElement typeElement = predicate.get(TYPE_PROPERTY);
        if (typeElement == null || !typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
            return false;
        }
        ResourceLocation type = ResourceLocation.tryParse(typeElement.getAsString());
        return type != null &&
                (BLOCKS_PREDICATE_TYPE.equals(type.getPath()) || BLOCK_STATES_PREDICATE_TYPE.equals(type.getPath()));
    }

    private static boolean usesAirBlock(JsonObject predicate) {
        return blockIds(predicate).stream().anyMatch(AIR_BLOCK_ID::equals);
    }

    private static ObjectList<String> missingBlockIds(JsonObject predicate) {
        ObjectArrayList<String> missingBlockIds = new ObjectArrayList<>();
        for (String blockId : blockIds(predicate)) {
            ResourceLocation id = ResourceLocation.tryParse(blockId);
            if (id == null || !blockExists(id)) {
                missingBlockIds.add(blockId);
            }
        }
        return new ObjectImmutableList<>(missingBlockIds);
    }

    private static ObjectList<String> blockIds(JsonObject predicate) {
        if (predicate.has(BLOCK_STATES_PROPERTY)) {
            JsonElement blockStatesElement = predicate.get(BLOCK_STATES_PROPERTY);
            if (!blockStatesElement.isJsonArray()) {
                return ObjectList.of();
            }
            ObjectArrayList<String> blockIds = new ObjectArrayList<>();
            JsonArray blockStates = blockStatesElement.getAsJsonArray();
            for (JsonElement blockStateElement : blockStates) {
                if (!blockStateElement.isJsonObject()) {
                    continue;
                }
                addBlockId(blockStateElement.getAsJsonObject(), blockIds);
            }
            return new ObjectImmutableList<>(blockIds);
        }
        if (predicate.has(BLOCKS_PROPERTY)) {
            JsonElement blocksElement = predicate.get(BLOCKS_PROPERTY);
            if (!blocksElement.isJsonArray()) {
                return ObjectList.of();
            }
            ObjectArrayList<String> blockIds = new ObjectArrayList<>();
            JsonArray blocks = blocksElement.getAsJsonArray();
            for (JsonElement blockElement : blocks) {
                if (blockElement.isJsonPrimitive() && blockElement.getAsJsonPrimitive().isString()) {
                    blockIds.add(blockElement.getAsString());
                }
            }
            return new ObjectImmutableList<>(blockIds);
        }
        JsonElement blockElement = predicate.get(BLOCK_PROPERTY);
        if (blockElement == null || !blockElement.isJsonPrimitive() || !blockElement.getAsJsonPrimitive().isString()) {
            return ObjectList.of();
        }
        return ObjectList.of(blockElement.getAsString());
    }

    private static void addBlockId(JsonObject object, ObjectList<String> blockIds) {
        JsonElement blockElement = object.get(BLOCK_PROPERTY);
        if (blockElement != null && blockElement.isJsonPrimitive() && blockElement.getAsJsonPrimitive().isString()) {
            blockIds.add(blockElement.getAsString());
        }
    }

    private static boolean blockExists(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        return id.equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    private static void putDefinition(Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions,
                                      Object2ObjectMap<JsonMultiBlockStructureKey, ResourceLocation> sources,
                                      JsonMultiBlockDefinition definition,
                                      ResourceLocation resourceId) {
        JsonMultiBlockDefinition previous = definitions.putIfAbsent(definition.key(), definition);
        if (previous != null) {
            String message = "Duplicate JSON multiblock key " + definition.key() + " from " + resourceId +
                    ", already loaded from " + sources.get(definition.key());
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        sources.put(definition.key(), resourceId);
    }
}
