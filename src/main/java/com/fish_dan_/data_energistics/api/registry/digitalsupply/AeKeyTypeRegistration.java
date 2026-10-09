package com.fish_dan_.data_energistics.api.registry.digitalsupply;

import appeng.api.stacks.AEKeyType;

import net.minecraft.resources.ResourceLocation;

/**
 * One AE2 key type declared through the unified Data Energistics plugin transaction.
 *
 * <p>
 * The key type must already contain its stable codec and packet decoder. The registration only publishes the
 * value before AE2's key-type registry event; it never mutates a running network.
 * </p>
 */
public record AeKeyTypeRegistration(ResourceLocation id,
                                    AEKeyType keyType,
                                    ResourceLocation resourceDirectory) {

    public AeKeyTypeRegistration {
        if (!id.equals(keyType.getId())) {
            throw new IllegalArgumentException(
                    "AEKeyType registration ID " + id + " does not match key type ID " + keyType.getId());
        }
    }
}
