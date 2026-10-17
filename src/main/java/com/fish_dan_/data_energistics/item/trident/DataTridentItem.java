package com.fish_dan_.data_energistics.item.trident;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class DataTridentItem extends TridentItem {

    public static final float DAMAGE = 17.0F;
    public static final float FLIGHT_SPEED_MULTIPLIER = 1.5F;

    public DataTridentItem(Properties properties) {
        super(properties.durability(250)
                .rarity(Rarity.RARE)
                .attributes(createDataTridentAttributes())
                .component(DataComponents.TOOL, TridentItem.createToolProperties()));
    }

    private static ItemAttributeModifiers createDataTridentAttributes() {
        // The player's base attack damage contributes the remaining point shown in the tooltip.
        return TridentItem.createAttributes().withModifierAdded(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE - 1.0D, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }

    @Override
    public void shoot(Projectile projectile, double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(projectile, x, y, z, velocity * FLIGHT_SPEED_MULTIPLIER, inaccuracy);
    }
}
