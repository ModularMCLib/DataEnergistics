package com.fish_dan_.data_energistics.util.input;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** A normal controls-page binding with an additional, exact set of Ctrl/Shift/Alt modifiers. */
@OnlyIn(Dist.CLIENT)
public final class KeyChordMapping extends KeyMapping {

    private static final ObjectList<KeyChordMapping> CHORDS = new ObjectArrayList<>();
    private static final KeyModifier[] DISPLAY_MODIFIERS = { KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.CONTROL };
    private int modifiers;
    private final int defaultModifiers;
    @Setter
    private String legacyName;

    public KeyChordMapping(String name, IKeyConflictContext context, int modifiers, InputConstants.Type type, int keyCode, String category) {
        super(name, context, KeyChordModifiers.primary(modifiers), type, keyCode, category);
        this.modifiers = KeyChordModifiers.withoutKey(modifiers, getKey());
        this.defaultModifiers = this.modifiers;
        this.legacyName = name;
        CHORDS.add(this);
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

    /** Releases raw native state even when another modifier or context is no longer active. */
    public static void releaseKey(InputConstants.Key key) {
        int releasedModifier = KeyChordModifiers.of(KeyModifier.getKeyModifier(key));
        for (KeyChordMapping chord : CHORDS) {
            if (chord.getKey().equals(key) || (chord.modifiers & releasedModifier) != 0) chord.setDown(false);
        }
    }

    /** Physical sampling also covers intercepted mouse input and modifiers pressed after the main key. */
    public boolean isPhysicallyDown(InputConstants.@Nullable Key eventKey, boolean eventDown) {
        return isConflictContextAndModifierActive() && isMainKeyDown(eventKey, eventDown);
    }

    boolean isMainKeyDown(InputConstants.@Nullable Key eventKey, boolean eventDown) {
        return isMainKeyDown(this, eventKey, eventDown);
    }

    static boolean isMainKeyDown(KeyMapping mapping, InputConstants.@Nullable Key eventKey, boolean eventDown) {
        if (mapping.isUnbound()) return false;
        if (mapping.getKey().equals(eventKey)) return eventDown;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (mapping.getKey().getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, mapping.getKey().getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, mapping.getKey().getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> mapping.isDown();
        };
    }

    boolean isShadowedBy(KeyChordMapping other) {
        return getKey().equals(other.getKey()) && Integer.bitCount(this.modifiers) < Integer.bitCount(other.modifiers);
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
        for (KeyModifier modifier : DISPLAY_MODIFIERS) {
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
        return !isUnbound() && getKey().equals(other.getKey()) &&
                (this.modifiers == otherModifiers || (this.modifiers == 0 || otherModifiers == 0) &&
                        (getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME) || other.getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME)));
    }

    @Override
    public boolean hasKeyModifierConflict(KeyMapping other) {
        if (!getKeyConflictContext().conflicts(other.getKeyConflictContext()) && !other.getKeyConflictContext().conflicts(getKeyConflictContext())) return false;
        return (this.modifiers & KeyChordModifiers.of(KeyModifier.getKeyModifier(other.getKey()))) != 0;
    }

    /** Adds a companion option while retaining NeoForge's existing key and single-modifier entry. */
    public void processOptions(Options.FieldAccess access, boolean loading) {
        String missing = "data_energistics:missing";
        if (loading && access.process("key_" + getName(), missing).equals(missing)) {
            String old = access.process("key_" + getName().replace("keybind.", "key."), missing);
            if (old.equals(missing)) old = access.process("key_" + this.legacyName, missing);
            if (!old.equals(missing)) {
                try {
                    String[] parts = old.split(":", 2);
                    setKeyModifierAndCode(parts.length == 2 ? KeyModifier.valueFromString(parts[1]) : KeyModifier.NONE, InputConstants.getKey(parts[0]));
                } catch (IllegalArgumentException failure) {
                    Data_Energistics.LOGGER.warn("Ignoring invalid legacy binding for {}: {}", getName(), old, failure);
                }
            }
        }
        String prefix = getKey().getName() + ":" + getKeyModifier().name() + ":";
        String saved = access.process("data_energistics_modifiers_" + getName(), loading ? missing : prefix + this.modifiers);
        if (loading && saved.equals(missing)) saved = access.process("data_energistics_modifiers_" + getName().replace("keybind.", "key."), missing);
        if (loading && saved.equals(missing)) saved = access.process("data_energistics_modifiers_" + this.legacyName, missing);
        if (!saved.startsWith(prefix) || saved.equals(prefix + this.modifiers)) return;
        try {
            setChord(getKey(), Integer.parseInt(saved.substring(prefix.length())));
        } catch (IllegalArgumentException failure) {
            Data_Energistics.LOGGER.warn("Ignoring invalid modifier option for {}: {}", getName(), saved, failure);
        }
    }
}
