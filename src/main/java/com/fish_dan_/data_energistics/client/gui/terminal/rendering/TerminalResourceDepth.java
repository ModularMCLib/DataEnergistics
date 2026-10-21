package com.fish_dan_.data_energistics.client.gui.terminal.rendering;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import org.joml.Matrix3f;
import org.jspecify.annotations.NullMarked;

/** Keeps native item models and decorations within a floating window's UI depth interval. */
@NullMarked
public final class TerminalResourceDepth {

    private TerminalResourceDepth() {}

    /** Changes only the caller's pushed pose; preserving the normal matrix retains native model lighting. */
    public static void flatten(GUIContext context) {
        var normal = new Matrix3f(context.graphics.pose().last().normal());
        context.pose.scale(1, 1, 0.001F);
        context.graphics.pose().last().normal().set(normal);
    }

    /** Lifts labels above native item geometry, retaining the four-unit spacing between floating windows. */
    public static void decorations(GUIContext context) {
        context.pose.translate(0, 0, 1);
        flatten(context);
    }
}
