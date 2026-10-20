package com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding;

import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalProcessingAmounts.Operation;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BiConsumer;

/** Compact processing controls flank the output column and edit configuration independently of network access. */
@NullMarked
public final class TerminalProcessingControlsUi {

    private final UIElement root;
    private @Nullable Operation lastOperation;

    public TerminalProcessingControlsUi(UIElement root, BiConsumer<String, CompoundTag> request) {
        this.root = root;
        for (Operation operation : Operation.values()) {
            Button button = TerminalUiLayout.require(root, id(operation), Button.class);
            button.style(style -> style.tooltips(TerminalTooltipLines.split(tooltip(operation))));
            button.setOnClick(event -> {
                if (event.button != 0) return;
                lastOperation = operation;
                CompoundTag data = new CompoundTag();
                data.putString("operation", operation.name());
                request.accept("processing_amount", data);
            });
        }
    }

    public static UIElement create() {
        UIElement root = TerminalUiLayout.at(new UIElement(), 84, 0, 40, 58);
        root.setId("processing_amounts");
        root.setAllowHitTest(false);
        root.addChildren(number(Operation.NORMALIZE, "/1", 2, 2),
                number(Operation.DIVIDE_2, "÷2", 2, 13),
                number(Operation.DIVIDE_3, "÷3", 2, 24),
                number(Operation.DIVIDE_5, "÷5", 2, 35),
                icon(Operation.SUBTRACT, "icon-scale-subtract", 2, 46),
                icon(Operation.CYCLE_OUTPUTS, "icon-cycle-outputs", 32, 2),
                number(Operation.MULTIPLY_2, "×2", 32, 13),
                number(Operation.MULTIPLY_3, "×3", 32, 24),
                number(Operation.MULTIPLY_5, "×5", 32, 35),
                icon(Operation.ADD, "icon-scale-add", 32, 46));
        return root;
    }

    public void apply(CompoundTag snapshot) {
        for (Operation operation : Operation.values()) {
            Component description = tooltip(operation);
            String message = snapshot.getString("message");
            Component[] lines = operation == lastOperation && snapshot.getString("action").equals("processing_amount") && !message.isEmpty() ?
                    TerminalTooltipLines.split(description, Component.translatable("gui.data_energistics.player_terminal.message." + message)) :
                    TerminalTooltipLines.split(description);
            TerminalUiLayout.require(root, id(operation), Button.class).style(style -> style.tooltips(lines));
        }
    }

    private static Button number(Operation operation, String label, int x, int y) {
        return TerminalUiLayout.smallButton(id(operation), Component.literal(label), x, y);
    }

    private static Button icon(Operation operation, String glyph, int x, int y) {
        return TerminalUiLayout.icon(id(operation), "terminal-pattern-option", glyph, x, y, 8, 8);
    }

    private static String id(Operation operation) {
        return "amount_" + operation.name();
    }

    private static Component tooltip(Operation operation) {
        return Component.translatable("gui.data_energistics.player_terminal.processing." + operation.name().toLowerCase(Locale.ROOT));
    }
}
