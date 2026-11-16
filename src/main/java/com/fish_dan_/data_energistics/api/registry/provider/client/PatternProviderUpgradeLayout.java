package com.fish_dan_.data_energistics.api.registry.provider.client;

/** Slot layout used by the public native-provider screen factory; existing resource-pack layouts take precedence. */
public enum PatternProviderUpgradeLayout {

    /** One upgrade between the encoded-pattern row and the storage row. */
    INLINE_SINGLE,

    /** A vertical upgrade strip on the right of a tall provider screen. */
    RIGHT_PANEL_VERTICAL
}
