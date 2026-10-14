package com.fish_dan_.data_energistics.api.research;

import com.fish_dan_.data_energistics.research.definition.ResearchDefinitions;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Mod-bus registration after Minecraft registries are available. Handlers run on the setup thread;
 * register complete defaults here. Datapacks can override rules but cannot introduce Java-unknown IDs.
 */
public final class RegisterResearchNodesEvent extends Event implements IModBusEvent {

    /** Registers a unique ID. Duplicate registration fails immediately; null is never accepted. */
    public void register(ResearchNode node) {
        ResearchDefinitions.register(node);
    }
}
