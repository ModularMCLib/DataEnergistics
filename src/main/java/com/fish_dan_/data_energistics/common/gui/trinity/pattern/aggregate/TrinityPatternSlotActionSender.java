package com.fish_dan_.data_energistics.common.gui.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternSlotAction;

import org.jetbrains.annotations.ApiStatus;

/** Sends one server-authoritative action for a currently displayed aggregate pattern slot. */
@ApiStatus.Internal
@FunctionalInterface
public interface TrinityPatternSlotActionSender {

    boolean send(long generation,
                 long layoutRevision,
                 long catalogRevision,
                 int globalSlot,
                 TrinityPatternSlotAction action);
}
