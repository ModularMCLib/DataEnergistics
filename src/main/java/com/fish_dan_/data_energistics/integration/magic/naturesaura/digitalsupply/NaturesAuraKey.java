package com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;
import java.util.Optional;

/** One stable AE identity backed by a Nature's Aura API type. */
public final class NaturesAuraKey extends AEKey {

    private static final Object2ObjectMap<ResourceLocation, NaturesAuraKey> KEYS = createKeys();
    private final ResourceLocation id;

    private NaturesAuraKey(ResourceLocation id) {
        this.id = id;
    }

    public static Optional<NaturesAuraKey> resolve(ResourceLocation id) {
        NaturesAuraKey existing = KEYS.get(id);
        if (existing != null) return Optional.of(existing);
        if (NaturesAuraAPI.AURA_TYPES.containsKey(id)) {
            NaturesAuraKey discovered = new NaturesAuraKey(id);
            KEYS.put(id, discovered);
            return Optional.of(discovered);
        }
        return Optional.empty();
    }

    public static NaturesAuraKey of(IAuraType type) {
        return resolve(type.getName()).orElseThrow(() -> new IllegalArgumentException("Unregistered Nature's Aura type: " + type.getName()));
    }

    public static ObjectList<NaturesAuraKey> all() {
        for (ResourceLocation id : NaturesAuraAPI.AURA_TYPES.keySet()) resolve(id);
        return ObjectList.of(KEYS.values().toArray(NaturesAuraKey[]::new));
    }

    public IAuraType auraType() {
        IAuraType type = NaturesAuraAPI.AURA_TYPES.get(id);
        if (type == null) throw new IllegalStateException("Nature's Aura type disappeared: " + id);
        return type;
    }

    @Override
    public AEKeyType getType() {
        return NaturesAuraKeyType.TYPE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString(NaturesAuraKeyType.RESOURCE_FIELD, id.toString());
        return tag;
    }

    @Override
    public Object getPrimaryKey() {
        return id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(id);
    }

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("key." + Data_Energistics.MODID + ".natures_aura", id);
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        if (amount > 0) drops.add(GenericStack.wrapInItemStack(this, amount));
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public ItemStack wrapForDisplayOrFilter() {
        return GenericStack.wrapInItemStack(this, 1);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NaturesAuraKey key && id.equals(key.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "NaturesAuraKey[" + id + "]";
    }

    private static Object2ObjectMap<ResourceLocation, NaturesAuraKey> createKeys() {
        var result = new Object2ObjectLinkedOpenHashMap<ResourceLocation, NaturesAuraKey>();
        for (ResourceLocation id : NaturesAuraAPI.AURA_TYPES.keySet()) result.put(id, new NaturesAuraKey(id));
        return result;
    }
}
