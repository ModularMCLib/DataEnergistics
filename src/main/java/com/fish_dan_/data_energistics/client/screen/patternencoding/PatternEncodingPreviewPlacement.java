package com.fish_dan_.data_energistics.client.screen.patternencoding;

import net.minecraft.client.renderer.Rect2i;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;

/** Defines stable panel geometry and chooses a low-overlap automatic anchor. */
final class PatternEncodingPreviewPlacement {

    private static final int SCREEN_MARGIN = 4;
    private static final int DRAG_HANDLE_RIGHT_PADDING = 4;
    private static final int DRAG_HANDLE_TOP_OFFSET = -7;

    private PatternEncodingPreviewPlacement() {}

    /**
     * Internal controls are already covered by the panel. Their temporary render visibility must not
     * change JEI's exclusion set, which invalidates the ingredient layout whenever a rectangle changes.
     */
    static ObjectList<Rect2i> interactiveBounds(Rect2i panelBounds, int dragHandleWidth, int dragHandleHeight) {
        return ObjectList.of(panelBounds, dragHandleBounds(panelBounds, dragHandleWidth, dragHandleHeight));
    }

    /** Derives the handle from the current panel layout, including before widgets are repositioned. */
    static Rect2i dragHandleBounds(Rect2i panelBounds, int width, int height) {
        return new Rect2i(
                panelBounds.getX() + panelBounds.getWidth() - width - DRAG_HANDLE_RIGHT_PADDING,
                panelBounds.getY() + DRAG_HANDLE_TOP_OFFSET,
                width, height);
    }

    static Rect2i findBestBounds(Rect2i anchor, int panelWidth, int panelHeight, int preferredY,
                                 int rightOffset, int leftOffset, int verticalGap,
                                 int screenWidth, int screenHeight, List<Rect2i> occupiedZones) {
        int rightX = anchor.getX() + anchor.getWidth() + rightOffset;
        int leftX = anchor.getX() - panelWidth + leftOffset;
        int centeredX = anchor.getX() + (anchor.getWidth() - panelWidth) / 2;
        int aboveY = anchor.getY() - panelHeight - verticalGap;
        int belowY = anchor.getY() + anchor.getHeight() + verticalGap;

        List<Rect2i> candidates = new ObjectArrayList<>();
        addCandidate(candidates, rightX, preferredY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, leftX, preferredY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, rightX, aboveY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, leftX, aboveY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, rightX, belowY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, leftX, belowY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, centeredX, aboveY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, centeredX, belowY, panelWidth, panelHeight, screenWidth, screenHeight);

        Rect2i bestCandidate = candidates.getFirst();
        long bestScore = computeScore(bestCandidate, anchor, occupiedZones);
        for (int index = 1; index < candidates.size(); index++) {
            Rect2i candidate = candidates.get(index);
            long score = computeScore(candidate, anchor, occupiedZones);
            if (score < bestScore) {
                bestCandidate = candidate;
                bestScore = score;
            }
        }
        return bestCandidate;
    }

    static Rect2i fromPercent(double xPercent, double yPercent, int panelWidth, int panelHeight,
                              int screenWidth, int screenHeight) {
        int availableWidth = Math.max(0, screenWidth - panelWidth - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(0, screenHeight - panelHeight - SCREEN_MARGIN * 2);
        int x = SCREEN_MARGIN + (int) Math.round(clampPercent(xPercent) * availableWidth);
        int y = SCREEN_MARGIN + (int) Math.round(clampPercent(yPercent) * availableHeight);
        return new Rect2i(x, y, panelWidth, panelHeight);
    }

    static double horizontalPercent(int x, int panelWidth, int screenWidth) {
        int minX = SCREEN_MARGIN;
        int maxX = Math.max(minX, screenWidth - panelWidth - SCREEN_MARGIN);
        return percent(x, minX, maxX);
    }

    static double verticalPercent(int y, int panelHeight, int screenHeight) {
        int minY = SCREEN_MARGIN;
        int maxY = Math.max(minY, screenHeight - panelHeight - SCREEN_MARGIN);
        return percent(y, minY, maxY);
    }

    static boolean overlapsAny(Rect2i candidate, List<Rect2i> occupiedZones) {
        for (Rect2i occupiedZone : occupiedZones) {
            if (computeOverlapArea(candidate, occupiedZone) > 0) {
                return true;
            }
        }
        return false;
    }

    private static void addCandidate(List<Rect2i> candidates, int x, int y, int width, int height,
                                     int screenWidth, int screenHeight) {
        candidates.add(clamp(x, y, width, height, screenWidth, screenHeight));
    }

    private static Rect2i clamp(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        int clampedX = Math.clamp(x, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN));
        int clampedY = Math.clamp(y, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN));
        return new Rect2i(clampedX, clampedY, width, height);
    }

    private static double percent(int position, int minimum, int maximum) {
        if (maximum <= minimum) {
            return 0.0;
        }
        return clampPercent((position - minimum) / (double) (maximum - minimum));
    }

    private static double clampPercent(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.clamp(value, 0.0, 1.0);
    }

    private static long computeScore(Rect2i candidate, Rect2i anchor, List<Rect2i> occupiedZones) {
        long score = computeOverlapArea(candidate, anchor) * 8L;
        for (Rect2i occupiedZone : occupiedZones) {
            score += computeOverlapArea(candidate, occupiedZone);
        }
        return score;
    }

    private static int computeOverlapArea(Rect2i first, Rect2i second) {
        int overlapWidth = Math.min(first.getX() + first.getWidth(), second.getX() + second.getWidth()) - Math.max(first.getX(), second.getX());
        int overlapHeight = Math.min(first.getY() + first.getHeight(), second.getY() + second.getHeight()) - Math.max(first.getY(), second.getY());
        if (overlapWidth <= 0 || overlapHeight <= 0) {
            return 0;
        }
        return overlapWidth * overlapHeight;
    }
}
