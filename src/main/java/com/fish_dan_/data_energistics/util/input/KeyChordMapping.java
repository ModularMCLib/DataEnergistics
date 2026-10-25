package com.fish_dan_.data_energistics.util.input;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;

/** A normal controls-page binding with an additional, exact set of Ctrl/Shift/Alt modifiers. */
@OnlyIn(Dist.CLIENT)
public final class KeyChordMapping extends KeyMapping {

    private int modifiers;
    private final int defaultModifiers;

    public KeyChordMapping(String name, InputConstants.Type type, int keyCode, String category) {
        this(name, KeyConflictContext.UNIVERSAL, KeyModifier.NONE, type, keyCode, category);
    }

    public KeyChordMapping(String name, IKeyConflictContext context, InputConstants.Type type, int keyCode, String category) {
        this(name, context, KeyModifier.NONE, type, keyCode, category);
    }

    public KeyChordMapping(String name, IKeyConflictContext context, KeyModifier modifier, InputConstants.Type type, int keyCode, String category) {
        super(name, context, modifier, type, keyCode, category);
        this.modifiers = KeyChordModifiers.of(getKeyModifier());
        this.defaultModifiers = this.modifiers;
    }

    /** Updates the complete chord before the options screen saves the ordinary key entry. */
    public void setChord(InputConstants.Key key, int modifiers) {
        int normalized = KeyChordModifiers.withoutKey(modifiers, key);
        super.setKeyModifierAndCode(KeyChordModifiers.primary(normalized), key);
        this.modifiers = normalized;
        setDown(false);
        while (super.consumeClick()) {
            // A queued press belongs to the old chord and must not trigger its replacement.
        }
    }

    @Override
    public void setKeyModifierAndCode(KeyModifier modifier, InputConstants.Key key) {
        setChord(key, KeyChordModifiers.of(modifier));
    }

    @Override
    public boolean matches(int keyCode, int scanCode) {
        return isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    @Override
    public boolean matchesMouse(int button) {
        return isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button));
    }

    @Override
    public boolean isActiveAndMatches(InputConstants.Key key) {
        return !isUnbound() && getKey().equals(key) && isConflictContextAndModifierActive();
    }

    @Override
    public boolean isConflictContextAndModifierActive() {
        return getKeyConflictContext().isActive() &&
                (this.modifiers == 0 ? getKeyModifier().isActive(getKeyConflictContext()) :
                        KeyChordModifiers.withoutKey(KeyChordModifiers.current(), getKey()) == this.modifiers);
    }

    @Override
    public Component getTranslatedKeyMessage() {
        Component text = getKey().getDisplayName();
        for (KeyModifier modifier : new KeyModifier[] { KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.CONTROL }) {
            if ((this.modifiers & KeyChordModifiers.of(modifier)) != 0) {
                Component suffix = text;
                text = modifier.getCombinedName(getKey(), () -> suffix);
            }
        }
        return text;
    }

    @Override
    public boolean isDefault() {
        return getKey().equals(getDefaultKey()) && this.modifiers == this.defaultModifiers;
    }

    @Override
    public void setToDefault() {
        setChord(getDefaultKey(), this.defaultModifiers);
    }

    @Override
    public boolean same(KeyMapping other) {
        if (!getKeyConflictContext().conflicts(other.getKeyConflictContext()) && !other.getKeyConflictContext().conflicts(getKeyConflictContext())) return false;
        if (hasKeyModifierConflict(other) || other.hasKeyModifierConflict(this)) return true;
        int otherModifiers = other instanceof KeyChordMapping chord ? chord.modifiers : KeyChordModifiers.of(other.getKeyModifier());
        return !isUnbound() && getKey().equals(other.getKey()) && this.modifiers == otherModifiers;
    }

    @Override
    public boolean hasKeyModifierConflict(KeyMapping other) {
        if (!getKeyConflictContext().conflicts(other.getKeyConflictContext()) && !other.getKeyConflictContext().conflicts(getKeyConflictContext())) return false;
        return (this.modifiers & KeyChordModifiers.of(KeyModifier.getKeyModifier(other.getKey()))) != 0;
    }

    /** Adds a companion option while retaining NeoForge's existing key and single-modifier entry. */
    public void processOptions(Options.FieldAccess access) {
        String prefix = getKey().getName() + ":" + getKeyModifier().name() + ":";
        String saved = access.process("data_energistics_modifiers_" + getName(), prefix + this.modifiers);
        if (!saved.startsWith(prefix) || saved.equals(prefix + this.modifiers)) return;
        try {
            setChord(getKey(), Integer.parseInt(saved.substring(prefix.length())));
        } catch (IllegalArgumentException failure) {
            Data_Energistics.LOGGER.warn("Ignoring invalid modifier option for {}: {}", getName(), saved, failure);
        }
    }
}
