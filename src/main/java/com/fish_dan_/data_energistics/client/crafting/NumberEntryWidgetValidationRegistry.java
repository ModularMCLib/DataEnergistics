package com.fish_dan_.data_energistics.client.crafting;

import appeng.client.gui.widgets.NumberEntryWidget;

import java.util.Map;
import java.util.OptionalLong;
import java.util.WeakHashMap;

/** Tracks the AE2 amount widgets that should use long-expression validation. */
public final class NumberEntryWidgetValidationRegistry {

    private static final Map<NumberEntryWidget, Boolean> STOCK_MODE = new WeakHashMap<>();

    private NumberEntryWidgetValidationRegistry() {}

    public static synchronized void enable(NumberEntryWidget widget) {
        STOCK_MODE.put(widget, false);
    }

    public static synchronized boolean isEnabled(NumberEntryWidget widget) {
        return STOCK_MODE.containsKey(widget);
    }

    public static synchronized void enableStockAmount(NumberEntryWidget widget) {
        STOCK_MODE.put(widget, true);
    }

    public static synchronized boolean isStockAmount(NumberEntryWidget widget) {
        return Boolean.TRUE.equals(STOCK_MODE.get(widget));
    }

    public static OptionalLong parse(NumberEntryWidget widget, String input) {
        return isStockAmount(widget) ? LongAmountExpressionParser.parseStockAmount(input, widget.getType().amountPerUnit()) : LongAmountExpressionParser.parse(input);
    }
}
