package com.fish_dan_.data_energistics.integration.magic.astral;

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

import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

/** Stable key for one Astral Sorcery registry entry. */
public final class AstralSorceryKey extends AEKey {

    private static final String LUMEN_PREFIX = "lumen/";
    private static final String CONSTELLATION_PREFIX = "constellation/";
    @Getter
    private final ResourceLocation resourceId;
    @Getter
    private final ResourceLocation registryId;
    @Getter
    private final Kind kind;
    @Getter
    private final Object value;

    private AstralSorceryKey(ResourceLocation resourceId, ResourceLocation registryId, Kind kind, Object value) {
        this.resourceId = resourceId;
        this.registryId = registryId;
        this.kind = kind;
        this.value = value;
    }

    public static Optional<AstralSorceryKey> resolve(ResourceLocation id) {
        String path = id.getPath();
        if (id.getNamespace().equals(Data_Energistics.MODID) && path.startsWith(LUMEN_PREFIX)) {
            ResourceLocation registryId = parseRegistryId(path.substring(LUMEN_PREFIX.length()));
            Lumen lumen = RegistriesAS.REGISTRY_LUMEN.get(registryId);
            return lumen == null ? Optional.empty() : Optional.of(lumen(registryId, lumen));
        }
        if (id.getNamespace().equals(Data_Energistics.MODID) && path.startsWith(CONSTELLATION_PREFIX)) {
            ResourceLocation registryId = parseRegistryId(path.substring(CONSTELLATION_PREFIX.length()));
            BaseConstellation constellation = RegistriesAS.REGISTRY_CONSTELLATIONS.get(registryId);
            return constellation == null ? Optional.empty() : Optional.of(constellation(registryId, constellation));
        }
        return Optional.empty();
    }

    public static AstralSorceryKey lumen(ResourceLocation registryId, Lumen lumen) {
        return new AstralSorceryKey(getResourceId(LUMEN_PREFIX, registryId), registryId, Kind.LUMEN, lumen);
    }

    public static AstralSorceryKey constellation(ResourceLocation registryId, BaseConstellation constellation) {
        return new AstralSorceryKey(getResourceId(CONSTELLATION_PREFIX, registryId), registryId, Kind.CONSTELLATION, constellation);
    }

    private static ResourceLocation getResourceId(String prefix, ResourceLocation id) {
        return Data_Energistics.id(prefix + id.getNamespace() + "/" + id.getPath());
    }

    private static ResourceLocation parseRegistryId(String path) {
        int slash = path.indexOf('/');
        if (slash <= 0 || slash == path.length() - 1) throw new IllegalArgumentException("Invalid Astral resource path " + path);
        return ResourceLocation.fromNamespaceAndPath(path.substring(0, slash), path.substring(slash + 1));
    }

    @Override
    public AEKeyType getType() {
        return AstralSorceryKeyType.TYPE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("resource", this.resourceId.toString());
        return tag;
    }

    @Override
    public Object getPrimaryKey() {
        return this.resourceId;
    }

    @Override
    public ResourceLocation getId() {
        return this.resourceId;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        ResourceLocation.STREAM_CODEC.encode(buffer, this.resourceId);
    }

    @Override
    protected Component computeDisplayName() {
        return this.kind == Kind.LUMEN ? ((Lumen) this.value).getName() : ((BaseConstellation) this.value).getName();
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
        return other instanceof AstralSorceryKey key && this.resourceId.equals(key.resourceId);
    }

    @Override
    public int hashCode() {
        return this.resourceId.hashCode();
    }

    @Override
    public String toString() {
        return "AstralSorceryKey[" + this.resourceId + "]";
    }

    public enum Kind {
        LUMEN,
        CONSTELLATION
    }
}
