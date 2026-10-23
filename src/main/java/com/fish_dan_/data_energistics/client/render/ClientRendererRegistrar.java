package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.client.render.beam.BeamFormerRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataChargerRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataDistributionTowerRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataExtractorRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataMimeticFieldRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataSanctumRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataSanctumReturnPortalRenderer;
import com.fish_dan_.data_energistics.client.render.entity.DataNukeRenderer;
import com.fish_dan_.data_energistics.client.render.entity.DispersingDataRenderer;
import com.fish_dan_.data_energistics.client.render.entity.FloatingGunProjectileRenderer;
import com.fish_dan_.data_energistics.client.render.entity.LightBladeChargeRenderer;
import com.fish_dan_.data_energistics.client.render.entity.MatterConvergingBoltRenderer;
import com.fish_dan_.data_energistics.client.render.entity.OrbitalAnnihilatorRenderer;
import com.fish_dan_.data_energistics.client.render.entity.ThrownLightSaberRenderer;
import com.fish_dan_.data_energistics.client.render.research.ResearchStationRenderer;
import com.fish_dan_.data_energistics.common.data.DEBlockEntities;
import com.fish_dan_.data_energistics.common.data.DEEntities;

import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ClientRendererRegistrar {

    private ClientRendererRegistrar() {}

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DEEntities.FLOATING_GUN_PROJECTILE.get(), FloatingGunProjectileRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.RESEARCH_STATION.get(), ResearchStationRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.ME_BEAM_FORMER.get(), context -> new BeamFormerRenderer());
        event.registerBlockEntityRenderer(DEBlockEntities.ME_OMNI_BEAM_FORMER.get(), context -> new BeamFormerRenderer());
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DataExtractorRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DataDistributionTowerRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), DataMimeticFieldRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), DataSanctumRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_SANCTUM_RETURN_PORTAL_BLOCK_ENTITY.get(), DataSanctumReturnPortalRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get(), DataChargerRenderer::new);
        event.registerEntityRenderer(DEEntities.DISPERSING_DATA.get(), DispersingDataRenderer::new);
        event.registerEntityRenderer(DEEntities.FALLING_DIGITAL_ANVIL.get(), FallingBlockRenderer::new);
        event.registerEntityRenderer(DEEntities.LIGHT_BLADE_CHARGE.get(), LightBladeChargeRenderer::new);
        event.registerEntityRenderer(DEEntities.MATTER_CONVERGING_BOLT.get(), MatterConvergingBoltRenderer::new);
        event.registerEntityRenderer(DEEntities.THROWN_LIGHT_SABER.get(), ThrownLightSaberRenderer::new);
        event.registerEntityRenderer(DEEntities.TNT_CONFIGURABLE_PRIMED.get(), TntRenderer::new);
        event.registerEntityRenderer(DEEntities.DATA_NUKE_PRIMED.get(), DataNukeRenderer::new);
        event.registerEntityRenderer(DEEntities.ORBITAL_ANNIHILATOR_PROJECTILE.get(), OrbitalAnnihilatorRenderer::new);
    }
}
