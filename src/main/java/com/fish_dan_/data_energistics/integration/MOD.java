package com.fish_dan_.data_energistics.integration;

import net.neoforged.fml.ModList;

import org.jspecify.annotations.NullMarked;

/** Optional integrations, queried after mod discovery and cached for the lifetime of the loaded mod list. */
@NullMarked
public enum MOD {

    JECHARACTERS("jecharacters"),
    EMI("emi"),
    AE2WTLIB("ae2wtlib"),
    CURIOS("curios"),
    XAERO_WORLD_MAP("xaeroworldmap", "xaerolib"),
    FTB_CHUNKS("ftbchunks"),
    APPFLUX("appflux"),
    DRACONIC_EVOLUTION("draconicevolution"),
    NEO_ECO_AE("neoecoae"),
    EXTENDED_AE("extendedae"),
    ADVANCED_AE("advanced_ae"),
    AE2CS("ae2cs"),
    APPLIED_CREATE("appliedcreate"),
    MEGA_CELLS("megacells"),
    ASTRAL_SORCERY("astralsorcery");

    private static final byte UNKNOWN = 0;
    private static final byte LOADED = 1;
    private static final byte ABSENT = 2;

    private final String[] modIds;
    private volatile byte loadedState;

    MOD(String... modIds) {
        this.modIds = modIds;
    }

    /**
     * Checks all required mod IDs on the first call after mod discovery. The immutable result is shared safely
     * between threads; subsequent calls only read the cached state.
     */
    public boolean isLoaded() {
        byte state = loadedState;
        if (state == UNKNOWN) {
            synchronized (this) {
                state = loadedState;
                if (state == UNKNOWN) {
                    state = LOADED;
                    for (String modId : modIds) {
                        if (!ModList.get().isLoaded(modId)) {
                            state = ABSENT;
                            break;
                        }
                    }
                    loadedState = state;
                }
            }
        }
        return state == LOADED;
    }
}
