package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKey;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.ae2.key.StellarFluxKey;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** Renders every resource in the shared Digitalization AE key type. */
public final class DigitalizationKeyRenderer extends SpriteKeyRenderer<DigitalizationKey> {

    private static final ResourceLocation DATA_FLOW_SPRITE = Data_Energistics.id("key/data_flow");
    private static final ResourceLocation ECHO_SPRITE = ResourceLocation.withDefaultNamespace("item/echo_shard");
    private static final ResourceLocation STELLAR_FLUX_SPRITE = Data_Energistics.id("key/stellar_flux");

    public DigitalizationKeyRenderer() {
        super(key -> switch (key) {
            case DataFlowKey ignored -> Minecraft.getInstance().getGuiSprites().getSprite(DATA_FLOW_SPRITE);
            case EchoKey ignored -> Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ECHO_SPRITE);
            case StellarFluxKey ignored -> Minecraft.getInstance().getGuiSprites().getSprite(STELLAR_FLUX_SPRITE);
        });
    }
}
