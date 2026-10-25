package com.fish_dan_.data_energistics.common.weapon.platform.model;

public enum HudModule {

    COMPASS(7),
    WEAPONS(49),
    NETWORK(7),
    TARGETS(975),
    GUIDES(7),
    ORES(3);

    public final int fields;

    HudModule(int fields) {
        this.fields = fields;
    }
}
