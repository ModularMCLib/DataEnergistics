package com.fish_dan_.data_energistics.common.terminal.player.encoding;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Recipe identity is configuration, never an extra physical ingredient or catalyst slot. */
@NullMarked
public record TerminalPatternContext(@Nullable ResourceLocation recipeType, @Nullable ResourceLocation recipeId) {

    public static final TerminalPatternContext EMPTY = new TerminalPatternContext(null, null);

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (recipeType != null) tag.putString("type", recipeType.toString());
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        return tag;
    }

    public static TerminalPatternContext load(CompoundTag tag) {
        return new TerminalPatternContext(id(tag, "type"), id(tag, "recipe"));
    }

    private static @Nullable ResourceLocation id(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) return null;
        String value = tag.getString(key);
        if (value.length() > 256) throw new IllegalArgumentException("Terminal recipe identity is too long");
        ResourceLocation id = ResourceLocation.parse(value);
        if (!id.getPath().startsWith("/")) return id;
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(1));
    }
}
