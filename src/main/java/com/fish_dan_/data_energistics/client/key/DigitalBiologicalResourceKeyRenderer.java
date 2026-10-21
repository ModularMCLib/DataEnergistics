package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.BloodKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalBiologicalResourceKey;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Renders the Data Energistics-owned blood and experience icons independently of optional fluid integrations. */
public final class DigitalBiologicalResourceKeyRenderer extends SpriteKeyRenderer<DigitalBiologicalResourceKey> {

    private static final ResourceLocation BLOOD_SPRITE = Data_Energistics.id("key/blood");
    private static final ResourceLocation EXPERIENCE_SPRITE = Data_Energistics.id("key/experience");

    public DigitalBiologicalResourceKeyRenderer() {
        super(key -> Minecraft.getInstance().getGuiSprites().getSprite(key instanceof BloodKey ? BLOOD_SPRITE : EXPERIENCE_SPRITE));
    }
}
