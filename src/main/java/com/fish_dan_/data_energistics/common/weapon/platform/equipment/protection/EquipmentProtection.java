package com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection;

import com.fish_dan_.data_energistics.Data_Energistics.MOD;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.combat.survival.LivingDeathState;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.DisarmProtection;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.CuriosEquipmentProtection;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Rejects transfer before extraction; no copied inventory or delayed restoration can duplicate a rejected item. */
public final class EquipmentProtection {

    public static final int INVENTORY = 0;
    public static final int ARMOR = 1;
    public static final int OFF_HAND = 2;
    public static final int CURIO = 3;
    // Both sides are weak: a value must not retain its player, whose inventory in turn retains the key.
    private static final Map<ItemStack, Binding> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());

    private EquipmentProtection() {}

    public static void bind(ServerPlayer player, ItemStack stack, int group, int slot, String identifier, boolean cosmetic) {
        if (!stack.isEmpty()) OWNERS.put(stack, new Binding(new WeakReference<>(player), group, slot, identifier, cosmetic));
    }

    public static boolean denyReplacement(ServerPlayer player, int group, int slot, ItemStack previous, ItemStack replacement) {
        if (previous.isEmpty() || previous == replacement || ItemStack.matches(previous, replacement) || permitted(player)) return false;
        return protectedSlot(player, group, slot, previous);
    }

    public static boolean denyExtraction(ItemStack stack) {
        Binding binding = OWNERS.get(stack);
        if (binding == null) return false;
        ServerPlayer player = binding.owner().get();
        return player != null && !permitted(player) && binding.current(player) == stack && protectedSlot(player, binding.group(), binding.slot(), stack);
    }

    public static boolean denyExtraction(ItemStack stack, DataComponentType<?> component) {
        return denyExtraction(stack) && component == DEDataComponents.PLATFORM_CONTENTS.get();
    }

    public static boolean denyDrop(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty() || permitted(serverPlayer)) return false;
        ItemStack platform = EquippedWeaponPlatform.find(player);
        if (platform.isEmpty()) return false;
        var settings = platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT).equipmentProtection();
        if (!settings.enabled() || !ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformChipUpgrades.DISARM_PROTECTION)) return false;
        if (ItemStack.matches(stack, platform)) return true;
        if (settings.mainHand() && ItemStack.matches(stack, player.getMainHandItem()) || settings.offHand() && ItemStack.matches(stack, player.getOffhandItem())) return true;
        if (settings.armor()) for (ItemStack armor : player.getArmorSlots()) if (ItemStack.matches(stack, armor)) return true;
        return settings.curios() && MOD.CURIOS.isLoaded() && CuriosEquipmentProtection.matches(serverPlayer, stack);
    }

    private static boolean permitted(ServerPlayer player) {
        return player.getData(DEAttachments.EQUIPMENT_PROTECTION).permits > 0 || player.getData(DEAttachments.SHIELD_HEALTH_WRITES).deathCommitted || !player.isAlive() || ((LivingDeathState) player).dataEnergistics$deathPending() || ErasureAttachments.blocksRecovery(player);
    }

    private static boolean protectedSlot(ServerPlayer player, int group, int slot, ItemStack stack) {
        ItemStack platform = EquippedWeaponPlatform.find(player);
        if (platform.isEmpty()) return false;
        DisarmProtection settings = platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT).equipmentProtection();
        if (!settings.enabled() || !ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformChipUpgrades.DISARM_PROTECTION)) return false;
        if (stack == platform) return true;
        return switch (group) {
            case ARMOR -> settings.armor();
            case OFF_HAND -> settings.offHand();
            case CURIO -> settings.curios();
            default -> slot == player.getInventory().selected && settings.mainHand();
        };
    }

    public static boolean denyContents(ItemStack platform, DataComponentType<?> component, @Nullable Object replacement) {
        if (!denyExtraction(platform, component)) return false;
        ItemContainerContents previous = platform.getOrDefault(DEDataComponents.PLATFORM_CONTENTS, ItemContainerContents.EMPTY);
        ItemContainerContents next = replacement == null ? ItemContainerContents.EMPTY : (ItemContainerContents) replacement;
        for (int slot = 0; slot < previous.getSlots(); slot++) {
            ItemStack oldStack = previous.getStackInSlot(slot);
            if (oldStack.isEmpty()) continue;
            ItemStack newStack = slot < next.getSlots() ? next.getStackInSlot(slot) : ItemStack.EMPTY;
            if (!ItemStack.isSameItem(oldStack, newStack) || newStack.getCount() < oldStack.getCount() || !ItemResearchUpgrades.of(oldStack).equals(ItemResearchUpgrades.of(newStack))) return true;
        }
        return false;
    }

    private record Binding(WeakReference<ServerPlayer> owner, int group, int slot, String identifier, boolean cosmetic) {

        private ItemStack current(ServerPlayer player) {
            return switch (group) {
                case ARMOR -> player.getInventory().armor.get(slot);
                case OFF_HAND -> player.getInventory().offhand.get(slot);
                case CURIO -> CuriosEquipmentProtection.stack(player, identifier, slot, cosmetic);
                default -> player.getInventory().items.get(slot);
            };
        }
    }
}
