package com.fish_dan_.data_energistics.integration.technology.create.domain;

import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.processing.burner.ScrollInstance;
import com.simibubi.create.content.processing.burner.ScrollTransformedInstance;
import dev.engine_room.flywheel.api.instance.Instance;
import org.jspecify.annotations.Nullable;

/** Stops Create's GPU time terms at the current phase and resumes without jumping through frozen time. */
public record CreateFrozenInstance(Instance instance, float speedU, float speedV, float phaseU, float phaseV) {

    public static @Nullable CreateFrozenInstance capture(Instance instance, float ticks) {
        CreateFrozenInstance result;
        if (instance instanceof RotatingInstance rotating) {
            float phase = rotating.rotationOffset + rotating.rotationalSpeed * ticks / 20;
            result = new CreateFrozenInstance(instance, rotating.rotationalSpeed, 0, phase, 0);
            rotating.rotationalSpeed = 0;
            rotating.rotationOffset = phase;
        } else if (instance instanceof ScrollInstance scroll) {
            float u = scroll.offsetU + scroll.speedU * ticks, v = scroll.offsetV + scroll.speedV * ticks;
            result = new CreateFrozenInstance(instance, scroll.speedU, scroll.speedV, u, v);
            scroll.speedU = scroll.speedV = 0;
            scroll.offsetU = u;
            scroll.offsetV = v;
        } else if (instance instanceof ScrollTransformedInstance scroll) {
            float u = scroll.offsetU + scroll.speedU * ticks, v = scroll.offsetV + scroll.speedV * ticks;
            result = new CreateFrozenInstance(instance, scroll.speedU, scroll.speedV, u, v);
            scroll.speedU = scroll.speedV = 0;
            scroll.offsetU = u;
            scroll.offsetV = v;
        } else return null;
        instance.setChanged();
        return result;
    }

    public void restore(float ticks) {
        if (instance instanceof RotatingInstance rotating) {
            rotating.rotationalSpeed = speedU;
            rotating.rotationOffset = phaseU - speedU * ticks / 20;
        } else if (instance instanceof ScrollInstance scroll) {
            scroll.speedU = speedU;
            scroll.speedV = speedV;
            scroll.offsetU = phaseU - speedU * ticks;
            scroll.offsetV = phaseV - speedV * ticks;
        } else if (instance instanceof ScrollTransformedInstance scroll) {
            scroll.speedU = speedU;
            scroll.speedV = speedV;
            scroll.offsetU = phaseU - speedU * ticks;
            scroll.offsetV = phaseV - speedV * ticks;
        }
        instance.setChanged();
    }
}
