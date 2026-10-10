package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalBiologicalResourceKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalBiologicalResourceKeyType;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.ae2.key.ManifestBinaryKeyType;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.Map;

public final class ClientAeKeyRenderers {

    private static boolean registered;
    private static final DigitalizationKeyRenderHandler DIGITALIZATION_RENDER_HANDLER = new DigitalizationKeyRenderHandler();
    private static final DataKeyRenderHandler DATA_RENDER_HANDLER = new DataKeyRenderHandler();
    private static final DigitalBiologicalResourceKeyRenderHandler DIGITAL_BIOLOGICAL_RESOURCE_RENDER_HANDLER = new DigitalBiologicalResourceKeyRenderHandler();

    private ClientAeKeyRenderers() {}

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        AEKeyRendering.register(DigitalizationKeyType.TYPE, DigitalizationKey.class, DIGITALIZATION_RENDER_HANDLER);
        AEKeyRendering.register(ManifestBinaryKeyType.TYPE, DataKey.class, DATA_RENDER_HANDLER);
        AEKeyRendering.register(DigitalBiologicalResourceKeyType.TYPE, DigitalBiologicalResourceKey.class, DIGITAL_BIOLOGICAL_RESOURCE_RENDER_HANDLER);
        registerAstralSorcery();
        if (Data_Energistics.isModLoaded("goety")) {
            GoetyClientKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("forbidden_arcanus")) {
            ForbiddenArcanusClientKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("naturesaura")) {
            NaturesAuraClientKeyRenderers.register();
        }
    }

    public static void reregister() {
        overwrite(DigitalizationKeyType.TYPE, DIGITALIZATION_RENDER_HANDLER);
        overwrite(ManifestBinaryKeyType.TYPE, DATA_RENDER_HANDLER);
        overwrite(DigitalBiologicalResourceKeyType.TYPE, DIGITAL_BIOLOGICAL_RESOURCE_RENDER_HANDLER);
        registerAstralSorcery();
        if (Data_Energistics.isModLoaded("goety")) {
            GoetyClientKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("forbidden_arcanus")) {
            ForbiddenArcanusClientKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("naturesaura")) {
            NaturesAuraClientKeyRenderers.register();
        }
        registered = true;
    }

    private static void registerAstralSorcery() {
        if (Data_Energistics.isModLoaded("astralsorcery")) {
            AstralSorceryClientKeyRenderers.register();
        }
    }

    private static void overwrite(AEKeyType type, AEKeyRenderHandler<?> handler) {
        Map<AEKeyType, AEKeyRenderHandler<?>> updated = new Reference2ReferenceOpenHashMap<>(AEKeyRendering.renderers);
        updated.put(type, (AEKeyRenderHandler<? extends AEKey>) handler);
        AEKeyRendering.renderers = updated;
    }
}
