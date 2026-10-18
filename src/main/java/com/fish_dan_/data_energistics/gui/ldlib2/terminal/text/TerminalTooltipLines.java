package com.fish_dan_.data_energistics.gui.ldlib2.terminal.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/** LDLib2 expects one component per tooltip line; visiting styled runs keeps formatting when splitting translations. */
@NullMarked
public final class TerminalTooltipLines {

    private TerminalTooltipLines() {}

    public static Component[] split(Component... components) {
        return split(ObjectImmutableList.of(components)).toArray(Component[]::new);
    }

    public static ObjectList<Component> split(Iterable<? extends Component> components) {
        ObjectList<Component> result = new ObjectArrayList<>();
        for (Component component : components) {
            ObjectList<MutableComponent> lines = new ObjectArrayList<>();
            lines.add(Component.empty());
            component.visit((style, text) -> {
                int start = 0;
                int newline;
                while ((newline = text.indexOf('\n', start)) >= 0) {
                    lines.getLast().append(Component.literal(text.substring(start, newline)).setStyle(style));
                    lines.add(Component.empty());
                    start = newline + 1;
                }
                if (start < text.length()) lines.getLast().append(Component.literal(text.substring(start)).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            result.addAll(lines);
        }
        return result;
    }
}
