package com.fish_dan_.data_energistics.api.production;

import com.fish_dan_.data_energistics.common.blockentity.machine.DataExtractorBlockEntity;

import appeng.api.stacks.AEKey;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import org.jspecify.annotations.NullMarked;

/**
 * Synchronous server-side notification that a data extractor's sword attack killed a living entity.
 *
 * <p>
 * Optional integrations can append additional AE-key output without adding their types to the base extractor.
 * Listeners must finish during event dispatch; output mutation after {@link #buildAdditionalOutput()} is rejected.
 * </p>
 */
@NullMarked
public final class DataExtractorKillEvent extends Event {

    private final DataExtractorBlockEntity extractor;
    private final ServerLevel level;
    private final LivingEntity target;
    private final Player attacker;
    private final ItemStack weapon;
    private final DamageSource damageSource;
    private final DataProductionOutput.Accumulator additionalOutput = DataProductionOutput.accumulator();
    private boolean outputBuilt;

    public DataExtractorKillEvent(DataExtractorBlockEntity extractor, ServerLevel level, LivingEntity target,
                                  Player attacker, ItemStack weapon, DamageSource damageSource) {
        this.extractor = extractor;
        this.level = level;
        this.target = target;
        this.attacker = attacker;
        this.weapon = weapon.copy();
        this.damageSource = damageSource;
    }

    /** Returns the extractor that performed this kill. */
    public DataExtractorBlockEntity extractor() {
        return this.extractor;
    }

    /** Returns the server level containing the kill. */
    public ServerLevel level() {
        return this.level;
    }

    /** Returns the killed entity while it remains available during synchronous dispatch. */
    public LivingEntity target() {
        return this.target;
    }

    /** Returns the player credited by the extractor's sword damage source. */
    public Player attacker() {
        return this.attacker;
    }

    /** Returns a copy of the weapon used for the fatal attack. */
    public ItemStack weapon() {
        return this.weapon.copy();
    }

    /** Returns the exact damage source used for the fatal attack. */
    public DamageSource damageSource() {
        return this.damageSource;
    }

    /** Adds a positive amount of optional output to the extractor's persistent pending ledger. */
    public void appendOutput(AEKey key, long amount) {
        if (this.outputBuilt) {
            throw new IllegalStateException("Data extractor kill output is already finalized");
        }
        this.additionalOutput.add(key, amount);
    }

    /** Finalizes and returns optional output after all synchronous listeners have run. */
    public DataProductionOutput buildAdditionalOutput() {
        this.outputBuilt = true;
        return this.additionalOutput.build();
    }
}
