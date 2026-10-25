package com.fish_dan_.data_energistics.util.input;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** Mod-bus registration event, fired once during common setup on both physical sides. */
public final class SyncedKeyMappingEvent extends Event implements IModBusEvent {}
