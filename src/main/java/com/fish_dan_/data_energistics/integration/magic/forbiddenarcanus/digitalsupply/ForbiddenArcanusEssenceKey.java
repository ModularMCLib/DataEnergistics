package com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply;

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

import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

/** One stable AE identity for a dynamically discovered Forbidden Arcanus essence type. */
public final class ForbiddenArcanusEssenceKey extends AEKey {

    private static final Object2ObjectMap<ResourceLocation, ForbiddenArcanusEssenceKey> KEYS = createKeys();
    private final ResourceLocation id;
    @Getter
    private final EssenceType essenceType;

    private ForbiddenArcanusEssenceKey(ResourceLocation id, EssenceType essenceType) {
        this.id = id;
        this.essenceType = essenceType;
    }

    public static Optional<ForbiddenArcanusEssenceKey> resolve(ResourceLocation id) {
        return Optional.ofNullable(KEYS.get(id));
    }

    public static ForbiddenArcanusEssenceKey of(EssenceType type) {
        for (ForbiddenArcanusEssenceKey key : KEYS.values()) {
            if (key.essenceType == type) return key;
        }
        throw new IllegalArgumentException("Unsupported Forbidden Arcanus essence type: " + type);
    }

    public static ObjectList<ForbiddenArcanusEssenceKey> all() {
        return ObjectList.of(KEYS.values().toArray(ForbiddenArcanusEssenceKey[]::new));
    }

    @Override
    public AEKeyType getType() {
        return ForbiddenArcanusEssenceKeyType.TYPE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ForbiddenArcanusEssenceKeyType.RESOURCE_FIELD, id.toString());
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
        return essenceType.getComponent();
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
        return other instanceof ForbiddenArcanusEssenceKey key && id.equals(key.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ForbiddenArcanusEssenceKey[" + id + "]";
    }

    private static Object2ObjectMap<ResourceLocation, ForbiddenArcanusEssenceKey> createKeys() {
        var result = new Object2ObjectLinkedOpenHashMap<ResourceLocation, ForbiddenArcanusEssenceKey>();
        for (EssenceType type : EssenceType.values()) {
            ResourceLocation id = Data_Energistics.id("forbidden_arcanus/" + type.getSerializedName());
            result.put(id, new ForbiddenArcanusEssenceKey(id, type));
        }
        return result;
    }
}
