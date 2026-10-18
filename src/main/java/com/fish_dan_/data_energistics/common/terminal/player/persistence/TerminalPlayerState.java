package com.fish_dan_.data_energistics.common.terminal.player.persistence;

import com.fish_dan_.data_energistics.common.anvil.payment.DigitalAnvilPayment;
import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalMagnetSettings;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalPinnedResources;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspaceState;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.function.Consumer;

/**
 * Server-thread player inventory and settings. Runtime grid objects are never persisted here.
 */
public final class TerminalPlayerState {

    private final EnumMap<TerminalWorkspace, TerminalWorkspaceState> workspaces = new EnumMap<>(TerminalWorkspace.class);
    private final Runnable changed;
    private @Nullable Long frequency;
    private TerminalWorkspace workspace = TerminalWorkspace.CRAFTING;
    private final TerminalMagnetSettings magnet;
    private final TerminalPinnedResources pins;
    private final DigitalAnvilPayment anvilPayment = new DigitalAnvilPayment();
    private long paymentFrequency;
    private final TerminalReturnLedger returns;

    public TerminalPlayerState(Runnable changed) {
        this.changed = changed;
        returns = new TerminalReturnLedger(changed);
        magnet = new TerminalMagnetSettings(changed);
        pins = new TerminalPinnedResources(changed);
        for (TerminalWorkspace page : TerminalWorkspace.values())
            workspaces.put(page, new TerminalWorkspaceState(page, changed));
    }

    public @Nullable Long frequency() {
        return frequency;
    }

    public TerminalWorkspace workspace() {
        return workspace;
    }

    public TerminalWorkspaceState work() {
        return workspaces.get(workspace);
    }

    public TerminalWorkspaceState work(TerminalWorkspace page) {
        return workspaces.get(page);
    }

    public TerminalMagnetSettings magnet() {
        return magnet;
    }

    public TerminalPinnedResources pins() {
        return pins;
    }

    public DigitalAnvilPayment anvilPayment() {
        return anvilPayment;
    }

    public long paymentFrequency() {
        return paymentFrequency;
    }

    public TerminalReturnLedger returns() {
        return returns;
    }

    public void changed() {
        changed.run();
    }

    public void frequency(long value) {
        frequency = value;
        changed.run();
    }

    public void workspace(TerminalWorkspace value) {
        workspace = value;
        changed.run();
    }

    public void paymentFrequency(long value) {
        paymentFrequency = value;
        changed.run();
    }

    /**
     * Moves each physical input out exactly once; previews and ghost configurations are excluded.
     */
    public void dropPhysicalItems(Consumer<ItemStack> drop) {
        for (TerminalWorkspaceState value : workspaces.values()) {
            for (int index = 0; index < value.inputs.getContainerSize(); index++) {
                ItemStack stack = value.inputs.removeItemNoUpdate(index);
                if (!stack.isEmpty()) drop.accept(stack);
            }
            for (int index = 0; index < value.patterns.getContainerSize(); index++) {
                ItemStack stack = value.patterns.removeItemNoUpdate(index);
                if (!stack.isEmpty()) drop.accept(stack);
            }
        }
        changed.run();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (frequency != null) tag.putLong("frequency", frequency);
        tag.putString("workspace", workspace.id());
        CompoundTag pages = new CompoundTag();
        for (var entry : workspaces.entrySet()) pages.put(entry.getKey().id(), entry.getValue().save(registries));
        tag.put("workspaces", pages);
        tag.put("magnet", magnet.save(registries));
        tag.put("pins", pins.save(registries));
        anvilPayment.save(tag, registries);
        tag.putLong("payment_frequency", paymentFrequency);
        tag.put("returns", returns.save(registries));
        return tag;
    }

    public static TerminalPlayerState load(CompoundTag tag, HolderLookup.Provider registries, Runnable changed) {
        TerminalPlayerState state = new TerminalPlayerState(changed);
        if (tag.contains("frequency", Tag.TAG_LONG)) state.frequency = tag.getLong("frequency");
        state.workspace = TerminalWorkspace.byId(tag.getString("workspace"));
        CompoundTag pages = tag.getCompound("workspaces");
        for (var entry : state.workspaces.entrySet())
            entry.getValue().load(pages.getCompound(entry.getKey().id()), registries);
        state.magnet.load(tag.getCompound("magnet"), registries);
        state.pins.load(tag.getCompound("pins"), registries);
        state.anvilPayment.load(tag, registries);
        state.paymentFrequency = tag.getLong("payment_frequency");
        state.returns.load(tag.getList("returns", Tag.TAG_COMPOUND), registries);
        return state;
    }
}
