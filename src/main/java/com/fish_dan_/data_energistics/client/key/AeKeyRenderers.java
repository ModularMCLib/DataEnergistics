package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics.MOD;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalBiologicalResourceKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalBiologicalResourceKeyType;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalizationKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.ManifestBinaryKeyType;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;

public final class AeKeyRenderers {

    private static final DigitalizationKeyRenderer DIGITALIZATION_RENDERER = new DigitalizationKeyRenderer();
    private static final DataKeyRenderer DATA_RENDERER = new DataKeyRenderer();
    private static final DigitalBiologicalResourceKeyRenderer DIGITAL_BIOLOGICAL_RESOURCE_RENDERER = new DigitalBiologicalResourceKeyRenderer();

    private AeKeyRenderers() {}

    public static int ammunitionColor(AEKey key) {
        return key instanceof DataKey data ? DATA_RENDERER.color(data) : DIGITALIZATION_RENDERER.color((DigitalizationKey) key);
    }

    public static void clearColors() {
        DATA_RENDERER.clearColors();
        DIGITALIZATION_RENDERER.clearColors();
    }

    public static void register() {
        AEKeyRendering.register(DigitalizationKeyType.TYPE, DigitalizationKey.class, DIGITALIZATION_RENDERER);
        AEKeyRendering.register(ManifestBinaryKeyType.TYPE, DataKey.class, DATA_RENDERER);
        AEKeyRendering.register(DigitalBiologicalResourceKeyType.TYPE, DigitalBiologicalResourceKey.class, DIGITAL_BIOLOGICAL_RESOURCE_RENDERER);

        if (MOD.GOETY.isLoaded()) {
            GoetyKeyRenderers.register();
        }
        if (MOD.FORBIDDEN_ARCANUS.isLoaded()) {
            ForbiddenArcanusKeyRenderers.register();
        }
        if (MOD.NATURES_AURA.isLoaded()) {
            NaturesAuraKeyRenderers.register();
        }
        if (MOD.ASTRAL_SORCERY.isLoaded()) {
            AstralSorceryKeyRenderers.register();
        }
    }
}
