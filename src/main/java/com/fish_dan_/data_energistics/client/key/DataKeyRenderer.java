package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class DataKeyRenderer extends SpriteKeyRenderer<DataKey> {

    private static final ResourceLocation SPRITE = Data_Energistics.id("key/data");

    public DataKeyRenderer() {
        super(key -> Minecraft.getInstance().getGuiSprites().getSprite(SPRITE));
    }
}
