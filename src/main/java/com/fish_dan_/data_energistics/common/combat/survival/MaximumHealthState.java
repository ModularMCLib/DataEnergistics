package com.fish_dan_.data_energistics.common.combat.survival;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Detached copies preserve both transient and permanent modifiers without serializing the player's inventory. */
public final class MaximumHealthState {

    private final AttributeInstance attributes = new AttributeInstance(Attributes.MAX_HEALTH, AttributeInstance::getValue);
    private final AttributeInstance target;

    public MaximumHealthState(AttributeInstance source) {
        target = source;
        attributes.replaceFrom(source);
    }

    public void restore() {
        target.replaceFrom(attributes);
    }
}
