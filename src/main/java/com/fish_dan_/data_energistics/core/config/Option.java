package com.fish_dan_.data_energistics.core.config;

import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** Immutable rule snapshot. Package parents constrain selection; delegates also propagate dependencies. */
public final class Option {

    @Getter
    private final String name;
    @Getter
    private final boolean defaultEnabled;
    @Getter
    private final boolean userEnabled;
    @Getter
    private final boolean userDefined;
    @Getter
    private final boolean hidden;
    @Getter
    private final ObjectList<String> comment;
    @Getter
    private final @Nullable Option parent;
    @Getter
    private final @Nullable Option delegate;
    private final boolean delegateInverted;
    private final boolean available;
    private final Object2BooleanMap<String> modOverrides;

    @Builder
    private Option(String name, boolean defaultEnabled, boolean userEnabled, boolean userDefined, boolean hidden, boolean available,
                   ObjectList<String> comment, Object2BooleanMap<String> modOverrides, @Nullable Option parent,
                   @Nullable Option delegate, boolean delegateInverted) {
        this.name = name;
        this.defaultEnabled = defaultEnabled;
        this.userEnabled = userEnabled;
        this.userDefined = userDefined;
        this.hidden = hidden;
        this.available = available;
        this.comment = ObjectLists.unmodifiable(new ObjectArrayList<>(comment));
        ObjectArrayList<String> modIds = new ObjectArrayList<>(modOverrides.keySet());
        modIds.sort(String::compareTo);
        Object2BooleanLinkedOpenHashMap<String> ordered = new Object2BooleanLinkedOpenHashMap<>();
        for (String modId : modIds) ordered.put(modId, modOverrides.getBoolean(modId));
        this.modOverrides = Object2BooleanMaps.unmodifiable(ordered);
        this.parent = parent;
        this.delegate = delegate;
        this.delegateInverted = delegateInverted;
    }

    /** Effective value. A missing dependency or disabled ancestor always wins. */
    public boolean isEnabled() {
        if (!isAvailable() || isEffectivelyDisabledByParent()) {
            return false;
        }
        boolean selected = this.modOverrides.isEmpty() ? (this.userDefined ? this.userEnabled : this.defaultEnabled) : !this.modOverrides.containsValue(false);
        return selected && (this.delegate == null || this.delegate.isEnabled() != this.delegateInverted);
    }

    public boolean isAvailable() {
        return this.available && (this.parent == null || this.parent.isAvailable()) && (this.delegate == null || this.delegate.isAvailable());
    }

    public boolean isModDefined() {
        return !this.modOverrides.isEmpty();
    }

    public boolean isOverridden() {
        return isUserDefined() || isModDefined() || this.delegate != null;
    }

    public boolean isEffectivelyDisabledByParent() {
        return this.parent != null && !this.parent.isEnabled();
    }

    public int getDepth() {
        return this.parent == null ? 0 : this.parent.getDepth() + 1;
    }

    public String getSelfName() {
        return this.parent == null ? this.name : this.name.substring(this.parent.name.length() + 1);
    }

    public ObjectSet<String> getDefiningMods() {
        return this.modOverrides.keySet();
    }
}
