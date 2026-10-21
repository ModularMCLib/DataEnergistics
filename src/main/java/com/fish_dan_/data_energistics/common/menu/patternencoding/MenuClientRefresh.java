package com.fish_dan_.data_energistics.common.menu.patternencoding;

import com.fish_dan_.data_energistics.client.screen.MenuClientRefreshHandler;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class MenuClientRefresh {

    private MenuClientRefresh() {}

    public static void refreshDataRipperScreen() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        MenuClientRefreshHandler.refreshDataRipperScreen();
    }

    public static void refreshDataDistributionTowerScreen() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        MenuClientRefreshHandler.refreshDataDistributionTowerScreen();
    }
}
