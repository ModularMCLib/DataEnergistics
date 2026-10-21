package com.fish_dan_.data_energistics.common.menu.universal;

import com.fish_dan_.data_energistics.common.part.terminal.UniversalTerminalPart;

public interface UniversalTerminalMenuBridge {

    int getAvailableTerminalMask();

    int getActiveTerminalIndex();

    UniversalTerminalPart getUniversalTerminalHost();

    void sendCycleTerminal(boolean reverse);
}
