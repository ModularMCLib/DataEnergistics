package com.fish_dan_.data_energistics.client.screen.ae2;

import appeng.client.gui.widgets.AECheckbox;

import net.minecraft.client.Minecraft;

/**
 * Shared geometry for the AE2 key-type selection wheel-scroll patch.
 *
 * <p>
 * AE2's original screen uses a 20-pixel content origin and a 6-pixel row padding. Keeping the derived values in one
 * place ensures that the capped background and the checkbox viewport use the same row geometry.
 * </p>
 */
public final class KeyTypeSelectionLayout {

    public static final int CONTENT_TOP = 20;
    public static final int PADDING = 6;
    public static final int BOTTOM_PADDING = 8;
    public static final int KEY_TYPE_SPACING = AECheckbox.SIZE + PADDING;
    public static final int SCREEN_MARGIN = 20;
    public static final int SCROLL_THRESHOLD = 8;

    private KeyTypeSelectionLayout() {}

    public static int contentHeight(int keyTypeCount) {
        return CONTENT_TOP + keyTypeCount * KEY_TYPE_SPACING + PADDING;
    }

    public static boolean requiresScroll(int keyTypeCount) {
        return keyTypeCount >= SCROLL_THRESHOLD;
    }

    public static int maxScreenHeight() {
        int scaledHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int minimumHeight = CONTENT_TOP + KEY_TYPE_SPACING + BOTTOM_PADDING;
        return Math.max(minimumHeight, scaledHeight - SCREEN_MARGIN);
    }

    public static int cappedHeight(int requestedHeight) {
        return Math.min(requestedHeight, maxScreenHeight());
    }

    public static int visibleRows(int imageHeight) {
        return Math.max(1, (imageHeight - CONTENT_TOP - BOTTOM_PADDING) / KEY_TYPE_SPACING);
    }

    public static int viewportHeight(int imageHeight) {
        return Math.max(KEY_TYPE_SPACING, imageHeight - CONTENT_TOP - BOTTOM_PADDING);
    }
}
