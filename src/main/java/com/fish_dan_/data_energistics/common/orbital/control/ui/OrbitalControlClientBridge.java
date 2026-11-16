package com.fish_dan_.data_energistics.common.orbital.control.ui;

import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;

/** Dist-safe bridge installed by client bootstrap before any orbital control menu can open. */
public final class OrbitalControlClientBridge {

    private static Binder binder;

    private OrbitalControlClientBridge() {}

    /** Installs the sole physical-client binding factory during client bootstrap. */
    public static void install(Binder clientBinder) {
        binder = clientBinder;
    }

    /** Creates one logical-client binding without exposing client-only types to the common UI tree. */
    static OrbitalControlClientBinding bind(
                                            OrbitalControlDashboard dashboard,
                                            OrbitalControlUiSource source,
                                            RPCEmitter commandEmitter) {
        return binder.bind(dashboard, source, commandEmitter);
    }

    /**
     * Creates a menu-scoped client binding. Called only on the Minecraft client thread; the returned binding must be
     * closed when its screen is replaced.
     */
    @FunctionalInterface
    public interface Binder {

        OrbitalControlClientBinding bind(
                                         OrbitalControlDashboard dashboard,
                                         OrbitalControlUiSource source,
                                         RPCEmitter commandEmitter);
    }
}
