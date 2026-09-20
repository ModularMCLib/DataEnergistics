package com.fish_dan_.data_energistics.mixin.magic.botania;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;

/** Read-only readiness check, avoiding partially delivering a cycle during the altar's native cooldown. */
@Mixin(value = RunicAltarBlockEntity.class, remap = false)
public interface RunicAltarAccessor {

    @Accessor("cooldown")
    int dataEnergistics$craftingCooldown();
}
