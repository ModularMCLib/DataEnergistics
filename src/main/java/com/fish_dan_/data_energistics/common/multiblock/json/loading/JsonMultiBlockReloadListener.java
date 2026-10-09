package com.fish_dan_.data_energistics.common.multiblock.json.loading;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.registry.JsonMultiBlockDefinitionRegistry;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Server datapack reload listener that atomically applies JSON multiblock definitions.
 */
public final class JsonMultiBlockReloadListener
                                                extends SimplePreparableReloadListener<Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition>> {

    private final JsonMultiBlockDefinitionRegistry registry;
    private final JsonMultiBlockDefinitionLoader loader;

    public JsonMultiBlockReloadListener(JsonMultiBlockDefinitionRegistry registry) {
        this(registry, new MdlibJsonMultiBlockDefinitionLoader());
    }

    public JsonMultiBlockReloadListener(JsonMultiBlockDefinitionRegistry registry, JsonMultiBlockDefinitionLoader loader) {
        this.registry = registry;
        this.loader = loader;
    }

    @Override
    protected Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> prepare(ResourceManager resourceManager,
                                                                                             ProfilerFiller profiler) {
        return this.loader.load(resourceManager);
    }

    @Override
    protected void apply(Object2ObjectMap<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        this.registry.applyJsonDefinitions(definitions.values());
    }
}
