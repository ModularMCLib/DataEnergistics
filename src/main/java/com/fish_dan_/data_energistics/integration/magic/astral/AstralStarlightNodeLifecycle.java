package com.fish_dan_.data_energistics.integration.magic.astral;

import net.minecraft.core.BlockPos;

import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;

/** Exposes the Astral transmission-node lifecycle needed by the AE-backed interface. */
public interface AstralStarlightNodeLifecycle {

    void dataEnergistics$replaceNode(BlockPos position, TransmissionNode node);

    void dataEnergistics$removeNode(BlockPos position);
}
