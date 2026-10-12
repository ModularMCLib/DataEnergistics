package com.fish_dan_.data_energistics.network.meteorite;

import com.fish_dan_.data_energistics.client.meteorite.DataMeteoriteCompassClientCache;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class DataMeteoriteCompassResponseHandler {

    private DataMeteoriteCompassResponseHandler() {}

    public static void cacheSyncedCompassResult(DataMeteoriteCompassResponsePayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        DataMeteoriteCompassClientCache.cacheSyncedCompassResult(payload);
    }
}
