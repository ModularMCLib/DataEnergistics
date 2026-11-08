package com.fish_dan_.data_energistics.core.mixins.dev.datagen;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyMappingLookup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = KeyMappingLookup.class, remap = false)
public class FixParallelKeyMappingRegistrationMixin {

    @WrapOperation(method = "<clinit>",
                   at = @At(value = "INVOKE",
                            target = "Ljava/util/EnumMap;put(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static <K extends Enum<K>, V> V gtceu$makeKeyMappingMapsConcurrent(EnumMap<K, V> instance, K key, V value,
                                                                               Operation<V> original) {
        return original.call(instance, key, new ConcurrentHashMap<InputConstants.Key, List<KeyMapping>>());
    }
}
