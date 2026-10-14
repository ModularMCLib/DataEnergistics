package com.fish_dan_.data_energistics.gui.ldlib2.research;

/** GUI-space geometry: full viewport, readable controls and one reachable workspace on narrow screens. */
record ResearchStationLayout(int width, int height, int margin, boolean wide, int bodyTop, int bodyHeight,
                             int graphWidth, int sidebarLeft, int sidebarWidth, int footerTop) {

    static ResearchStationLayout forViewport(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Research viewport must have positive dimensions");
        int margin = Math.clamp(Math.min(width, height) / 16, 0, 8);
        boolean wide = width >= 620;
        int bodyTop = width - margin * 2 < 328 ? 114 : 88;
        int footerTop = Math.max(bodyTop + 1, height - margin - 30);
        int bodyHeight = Math.max(1, footerTop - bodyTop - 7);
        int contentWidth = Math.max(1, width - margin * 2);
        int sidebarWidth = wide ? Math.min(330, width / 3) : contentWidth;
        int sidebarLeft = wide ? width - margin - sidebarWidth : margin;
        int graphWidth = wide ? Math.max(1, sidebarLeft - margin - 8) : contentWidth;
        return new ResearchStationLayout(width, height, margin, wide, bodyTop, bodyHeight,
                graphWidth, sidebarLeft, sidebarWidth, footerTop);
    }
}
