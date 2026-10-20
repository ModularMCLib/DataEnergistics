package com.fish_dan_.data_energistics.common.terminal.player.encoding;

import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalGhostInventory;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspaceState;

import appeng.api.stacks.GenericStack;

import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

/** Server-thread edits validate every processing slot before applying an exact, positive amount change. */
@NullMarked
public final class TerminalProcessingAmounts {

    private TerminalProcessingAmounts() {}

    public enum Operation {
        NORMALIZE,
        CYCLE_OUTPUTS,
        MULTIPLY_2,
        MULTIPLY_3,
        MULTIPLY_5,
        DIVIDE_2,
        DIVIDE_3,
        DIVIDE_5,
        ADD,
        SUBTRACT
    }

    public static String apply(TerminalPlayerState state, Operation operation) {
        if (state.workspace() != TerminalWorkspace.PROCESSING)
            throw new IllegalArgumentException("Amount controls require the processing page");
        TerminalWorkspaceState work = state.work();
        if (operation == Operation.CYCLE_OUTPUTS) {
            cycleOutputs(work);
            return "";
        }
        long divisor = divisor(work);
        if (divisor == 0) return "empty_processing";
        if (operation == Operation.SUBTRACT && divisor == 1) return "minimum_ratio";
        ObjectList<Change> changes = new ObjectArrayList<>();
        for (var inventory : new TerminalGhostInventory[] { work.patternInputs, work.patternOutputs }) {
            for (int index = 0; index < inventory.size(); index++) {
                GenericStack stack = inventory.get(index);
                if (stack == null) continue;
                long amount = stack.amount();
                long adjusted;
                switch (operation) {
                    case MULTIPLY_2, MULTIPLY_3, MULTIPLY_5 -> {
                        int factor = factor(operation);
                        if (amount > Long.MAX_VALUE / factor) return "amount_overflow";
                        adjusted = amount * factor;
                    }
                    case DIVIDE_2, DIVIDE_3, DIVIDE_5 -> {
                        int factor = factor(operation);
                        if (amount % factor != 0) return "not_divisible";
                        adjusted = amount / factor;
                    }
                    case NORMALIZE -> adjusted = amount / divisor;
                    case ADD -> {
                        long increment = amount / divisor;
                        if (amount > Long.MAX_VALUE - increment) return "amount_overflow";
                        adjusted = amount + increment;
                    }
                    case SUBTRACT -> adjusted = amount - amount / divisor;
                    default -> throw new IllegalStateException("Output cycling cannot change processing amounts");
                }
                if (adjusted != amount) changes.add(new Change(inventory, index, new GenericStack(stack.what(), adjusted)));
            }
        }
        for (Change change : changes) change.inventory().set(change.index(), change.stack());
        return "";
    }

    private static int factor(Operation operation) {
        return switch (operation) {
            case MULTIPLY_2, DIVIDE_2 -> 2;
            case MULTIPLY_3, DIVIDE_3 -> 3;
            case MULTIPLY_5, DIVIDE_5 -> 5;
            default -> throw new IllegalArgumentException("Operation has no fixed scale factor");
        };
    }

    private static long divisor(TerminalWorkspaceState work) {
        long divisor = 0;
        for (var inventory : new TerminalGhostInventory[] { work.patternInputs, work.patternOutputs }) {
            for (int index = 0; index < inventory.size(); index++) {
                GenericStack stack = inventory.get(index);
                if (stack != null) divisor = LongMath.gcd(divisor, stack.amount());
            }
        }
        return divisor;
    }

    private static void cycleOutputs(TerminalWorkspaceState work) {
        var slots = new IntArrayList();
        ObjectList<GenericStack> stacks = new ObjectArrayList<>();
        for (int index = 0; index < work.patternOutputs.size(); index++) {
            GenericStack stack = work.patternOutputs.get(index);
            if (stack != null) {
                slots.add(index);
                stacks.add(stack);
            }
        }
        if (slots.size() < 2) return;
        for (int index = 0; index < slots.size(); index++)
            work.patternOutputs.set(slots.getInt(index), stacks.get((index + 1) % stacks.size()));
        work.matching.cycleOutputs(slots);
    }

    private record Change(TerminalGhostInventory inventory, int index, GenericStack stack) {}
}
