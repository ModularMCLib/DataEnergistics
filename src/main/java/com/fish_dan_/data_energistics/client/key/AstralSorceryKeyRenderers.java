package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.integration.magic.astral.AstralSorceryKey;
import com.fish_dan_.data_energistics.integration.magic.astral.AstralSorceryKeyType;

import appeng.api.client.AEKeyRendering;

/** Client-side Astral Sorcery key renderer kept behind the optional-mod class boundary. */
final class AstralSorceryKeyRenderers {

    private static final AstralSorceryKeyRenderer HANDLER = new AstralSorceryKeyRenderer();

    private AstralSorceryKeyRenderers() {}

    static void register() {
        if (AEKeyRendering.get(AstralSorceryKeyType.TYPE) == null) {
            AEKeyRendering.register(AstralSorceryKeyType.TYPE, AstralSorceryKey.class, HANDLER);
        }
    }
}
