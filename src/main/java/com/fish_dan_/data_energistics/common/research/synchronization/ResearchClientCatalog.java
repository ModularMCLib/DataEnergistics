package com.fish_dan_.data_energistics.common.research.synchronization;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchNbt;

import net.minecraft.core.HolderLookup;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.jspecify.annotations.Nullable;

/** Client-thread catalog for global item tooltips, replaced on login/reload and cleared on disconnect. */
public final class ResearchClientCatalog {

    private static final Object2ObjectMap<ResourceLocation, ResearchNode> NODES = new Object2ObjectLinkedOpenHashMap<>();

    private ResearchClientCatalog() {}

    public static void receive(CompoundTag tag, HolderLookup.Provider registries) {
        NODES.clear();
        for (Tag value : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            ResearchNode node = ResearchNbt.readNode((CompoundTag) value, registries);
            NODES.put(node.id(), node);
            if (!Language.getInstance().has(node.name()) || !Language.getInstance().has(node.description())) {
                Data_Energistics.LOGGER.warn("Research node {} has missing translations: name={}, description={}",
                        node.id(), node.name(), node.description());
            }
        }
    }

    public static @Nullable ResearchNode find(ResourceLocation id) {
        return NODES.containsKey(id) ? NODES.get(id) : null;
    }

    public static void clear() {
        NODES.clear();
    }
}
