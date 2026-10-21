package com.fish_dan_.data_energistics.common.gui.research;

/** GUI-space geometry with a reserved right control column and a separate research workspace. */
record ResearchStationLayout(int width, int height, int margin, boolean wide, int bodyTop, int bodyHeight,
                             int graphWidth, int sidebarLeft, int sidebarWidth, int controlsLeft, int controlsWidth) {

    private static final int PANEL_GAP = 8;

    static ResearchStationLayout forViewport(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Research viewport must have positive dimensions");
        int margin = Math.clamp(Math.min(width, height) / 16, 0, 8);
        int contentWidth = Math.max(1, width - margin * 2);
        int controlsWidth = Math.min(contentWidth, Math.clamp(contentWidth / 5, 104, 128));
        int controlsLeft = width - margin - controlsWidth;
        int workspaceWidth = Math.max(1, controlsLeft - margin - PANEL_GAP);
        boolean wide = workspaceWidth >= 560;
        int bodyTop = margin + 26;
        int bodyHeight = Math.max(1, height - margin - bodyTop);
        int sidebarWidth = wide ? Math.min(300, workspaceWidth / 3) : workspaceWidth;
        int sidebarLeft = margin + workspaceWidth - sidebarWidth;
        int graphWidth = wide ? Math.max(1, sidebarLeft - margin - PANEL_GAP) : workspaceWidth;
        return new ResearchStationLayout(width, height, margin, wide, bodyTop, bodyHeight,
                graphWidth, sidebarLeft, sidebarWidth, controlsLeft, controlsWidth);
    }

    int workspaceWidth() {
        return Math.max(1, controlsLeft - margin - PANEL_GAP);
    }
}
