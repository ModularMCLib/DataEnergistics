package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.ae2.key.ManifestBinaryKeyType;
import com.fish_dan_.data_energistics.ae2.key.StellarFluxKey;
import com.fish_dan_.data_energistics.api.registry.worldenergy.AeKeyTypeRegistration;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.IRegistryExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;

/**
 * Owns the complete catalog of custom AE keys and key types supplied by Data Energistics.
 */
public final class DEAE2Keys {

    private static final ObjectList<AEKeyType> BUILTIN_TYPES = ObjectList.of(
            DigitalizationKeyType.TYPE,
            ManifestBinaryKeyType.TYPE);
    private static volatile ObjectList<AEKeyType> registeredTypes = BUILTIN_TYPES;
    private static final ObjectList<AEKey> KEYS = ObjectList.of(
            DataFlowKey.of(),
            DataKey.of(),
            EchoKey.of(),
            StellarFluxKey.of());

    private DEAE2Keys() {}

    public static void register(RegisterEvent event) {
        if (!event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) {
            return;
        }
        ObjectArrayList<AEKeyType> types = new ObjectArrayList<>(BUILTIN_TYPES);
        ObjectSet<ResourceLocation> ids = new ObjectLinkedOpenHashSet<>();
        for (AEKeyType type : BUILTIN_TYPES) {
            ids.add(type.getId());
        }
        for (AeKeyTypeRegistration registration : DataEnergisticsEntrypointLoader.snapshot().aeKeyTypes()) {
            if (!ids.add(registration.id())) {
                throw new IllegalStateException("Duplicate AEKeyType ID during registry event: " + registration.id());
            }
            types.add(registration.keyType());
        }
        registeredTypes = ObjectLists.unmodifiable(types);
        for (AEKeyType type : registeredTypes) {
            event.register(AEKeyType.REGISTRY_KEY, type.getId(), () -> type);
        }
        IRegistryExtension<?> registry = (IRegistryExtension<?>) event.getRegistry();
        registry.addAlias(DataFlowKey.ID, DigitalizationKeyType.TYPE.getId());
        registry.addAlias(EchoKey.ID, DigitalizationKeyType.TYPE.getId());
        registry.addAlias(StellarFluxKey.ID, DigitalizationKeyType.TYPE.getId());
        registry.addAlias(DataKey.ID, ManifestBinaryKeyType.TYPE.getId());
    }

    /**
     * Returns the ordered key-type catalog used for registration and generic integration hooks.
     */
    public static ObjectList<AEKeyType> types() {
        return registeredTypes;
    }

    /**
     * Returns every singleton key supplied by this mod in stable display order.
     */
    public static ObjectList<AEKey> keys() {
        return KEYS;
    }

    /**
     * Identifies whether a type belongs to this mod's custom resource catalog.
     */
    public static boolean isCustomType(AEKeyType type) {
        return type != null && registeredTypes.contains(type);
    }

    /**
     * Identifies whether a key belongs to this mod's custom resource catalog.
     */
    public static boolean isCustomKey(AEKey key) {
        return key != null && isCustomType(key.getType());
    }
}
