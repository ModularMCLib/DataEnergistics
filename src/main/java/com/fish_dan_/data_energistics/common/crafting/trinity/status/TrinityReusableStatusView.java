package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityReusableStatus.Phase;
import com.fish_dan_.data_energistics.util.FormattingUtils;

/** Client observation with bounded display text instead of arbitrary-precision tool balances. */
public record TrinityReusableStatusView(Phase phase, int sessions, String heldTools, String spareTools, String diagnostic) {

    public static TrinityReusableStatusView of(TrinityReusableStatus status) {
        return new TrinityReusableStatusView(status.phase(), status.sessions(), FormattingUtils.format(status.heldTools()),
                FormattingUtils.format(status.spareTools()), status.diagnostic());
    }
}
