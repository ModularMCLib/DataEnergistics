package com.fish_dan_.data_energistics.core.mixin.condition;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/** Immutable startup facts shared by condition checks; no mod or target classes are loaded. */
public record MixinLoadingEnvironment(Object2ObjectMap<String, String> modVersions, boolean production, boolean datagen) {

    public MixinLoadingEnvironment {
        modVersions = Object2ObjectMaps.unmodifiable(new Object2ObjectOpenHashMap<>(modVersions));
    }

}
