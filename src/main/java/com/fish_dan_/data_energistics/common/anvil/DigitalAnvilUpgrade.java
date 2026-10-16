package com.fish_dan_.data_energistics.common.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

/** Independent technology gates; the bit positions are used only by the open menu's view state. */
public enum DigitalAnvilUpgrade {

    FIXED_PENALTY("fixed_penalty"),
    UNRESTRICTED_TYPES("unrestricted_types"),
    UNRESTRICTED_LEVELS("unrestricted_levels"),
    COPY_ENCHANTMENTS("copy_enchantments");

    private final String key;
    private final ResourceLocation researchId;

    DigitalAnvilUpgrade(String key) {
        this.key = key;
        this.researchId = Data_Energistics.id("me_digital_anvil/" + key);
    }

    public ResourceLocation researchId() {
        return researchId;
    }

    public String translationKey() {
        return "research.data_energistics.me_digital_anvil." + key;
    }

    public int mask() {
        return 1 << ordinal();
    }

    public boolean enabled(int mask) {
        return (mask & mask()) != 0;
    }
}
