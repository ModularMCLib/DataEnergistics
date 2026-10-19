package com.fish_dan_.data_energistics.common.terminal.player.encoding;

import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;
import com.fish_dan_.data_energistics.common.crafting.pattern.matching.EncodedPatternMatching;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntList;
import org.jspecify.annotations.NullMarked;

/** Sparse editor rules keep the original processing slot indexes, including empty and scrolled slots. */
@NullMarked
public final class TerminalProcessingMatching {

    private final Runnable changed;
    private String inputs = "";
    private String outputs = "";

    public TerminalProcessingMatching(Runnable changed) {
        this.changed = changed;
    }

    public void set(boolean output, int index, ProcessingMatchMode mode) {
        int size = output ? TerminalWorkspace.PROCESSING.patternOutputSlots() : TerminalWorkspace.PROCESSING.patternInputSlots();
        if (index < 0 || index >= size) throw new IllegalArgumentException("Invalid processing matching slot");
        StringBuilder modes = new StringBuilder(output ? outputs : inputs);
        while (modes.length() <= index) modes.append('0');
        modes.setCharAt(index, (char) ('0' + mode.ordinal()));
        if (output) outputs = modes.toString();
        else inputs = modes.toString();
        changed.run();
    }

    public void clear() {
        inputs = "";
        outputs = "";
        changed.run();
    }

    /** Matching rules follow their resources when nonempty outputs rotate, preserving sparse slot indexes. */
    void cycleOutputs(IntList slots) {
        StringBuilder rotated = new StringBuilder(outputs);
        while (rotated.length() <= slots.getInt(slots.size() - 1)) rotated.append('0');
        for (int index = 0; index < slots.size(); index++) {
            int source = slots.getInt((index + 1) % slots.size());
            rotated.setCharAt(slots.getInt(index), source < outputs.length() ? outputs.charAt(source) : '0');
        }
        outputs = rotated.toString();
        changed.run();
    }

    public boolean apply(ServerLevel level, ItemStack pattern) {
        return EncodedPatternMatching.apply(level, pattern, inputs, outputs);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("inputs", inputs);
        tag.putString("outputs", outputs);
        return tag;
    }

    public void load(CompoundTag tag) {
        inputs = tag.getString("inputs");
        outputs = tag.getString("outputs");
    }
}
