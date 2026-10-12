package com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply;

import com.fish_dan_.data_energistics.api.production.DataExtractorKillEvent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.neoforged.neoforge.common.util.FakePlayer;

import com.Polarice3.Goety.common.events.spell.GoetyEventFactory;
import com.Polarice3.Goety.common.items.ModTiers;
import com.Polarice3.Goety.common.items.equipment.FangedDaggerItem;
import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.ModDamageSource;
import com.Polarice3.Goety.utils.SEHelper;

/** Goety listener for the mod-neutral data-extractor kill API. */
public final class GoetyDataExtractorEvents {

    private static final long MAX_SOUL_OUTPUT = Integer.MAX_VALUE;

    private GoetyDataExtractorEvents() {}

    /** Appends Goety soul output for one fatal Goety sword attack. */
    public static void onDataExtractorKill(DataExtractorKillEvent event) {
        Player attacker = event.attacker();
        ItemStack weapon = event.weapon();
        if (!(attacker instanceof FakePlayer fakePlayer) || weapon.isEmpty() || !weapon.is(ItemTags.SWORDS) || !dataEnergistics$isGoetyItem(weapon)) {
            return;
        }

        int baseSouls = Math.max(0, SEHelper.getSoulGiven(event.target()));
        int weaponMultiplier = Math.max(0, SEHelper.SoulMultiply(fakePlayer, event.damageSource()));
        float multipliedBase = baseSouls * (float) weaponMultiplier;
        if (dataEnergistics$isDarkWeapon(weapon, event.damageSource())) {
            multipliedBase *= 1.5F;
        }
        long souls = Math.clamp((long) Math.floor(multipliedBase), 0L, MAX_SOUL_OUTPUT);
        souls = dataEnergistics$saturatedMultiply(souls, Math.max(1, MainConfig.SoulTakenMultiplier.get()));
        if (souls <= 0L) {
            return;
        }
        int adjusted = Math.max(0, GoetyEventFactory.onSoulEnergyGain(fakePlayer, (int) souls));
        if (adjusted > 0) {
            event.appendOutput(GoetySoulKey.INSTANCE, adjusted);
        }
    }

    private static boolean dataEnergistics$isGoetyItem(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.getNamespace().equals("goety");
    }

    private static boolean dataEnergistics$isDarkWeapon(ItemStack stack, DamageSource source) {
        if (!ModDamageSource.physicalAttacks(source)) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof FangedDaggerItem || item instanceof TieredItem tiered && tiered.getTier() == ModTiers.DARK;
    }

    private static long dataEnergistics$saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > MAX_SOUL_OUTPUT / right ? MAX_SOUL_OUTPUT : left * right;
    }
}
