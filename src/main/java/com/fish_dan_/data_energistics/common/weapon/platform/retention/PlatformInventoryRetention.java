package com.fish_dan_.data_energistics.common.weapon.platform.retention;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** A committed death keeps equipment in place and transfers extra drops through a persistent typed attachment. */
public final class PlatformInventoryRetention {

    /** Called only when death reaches loot settlement, after shields, reboot and cancellation have resolved. */
    public static void capture(ServerPlayer player) {
        var previous = player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY);
        if (previous.retained()) return;
        ItemStack platform = EquippedWeaponPlatform.find(player);
        if (platform.isEmpty() || !platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT).enabled(PlatformAbility.KEEP_INVENTORY) || !ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformAbility.KEEP_INVENTORY.research)) return;
        var pending = new ObjectArrayList<>(previous.pending());
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) pending.add(carried.copy());
        player.setData(DEAttachments.PLATFORM_RETAINED_INVENTORY, new RetainedInventoryState(true, pending));
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.closeContainer();
    }

    /** The caller has already checked the death decision. Never remove an item until its copy is retained. */
    public static void retain(ServerPlayer player, ItemStack stack) {
        var pending = new ObjectArrayList<>(player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).pending());
        pending.add(stack.copy());
        player.setData(DEAttachments.PLATFORM_RETAINED_INVENTORY, new RetainedInventoryState(true, pending));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).retained()) return;
        var pending = new ObjectArrayList<>(player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).pending());
        for (var drop : event.getDrops()) if (!drop.getItem().isEmpty()) pending.add(drop.getItem().copy());
        player.setData(DEAttachments.PLATFORM_RETAINED_INVENTORY, new RetainedInventoryState(true, pending));
        event.getDrops().clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void clone(PlayerEvent.Clone event) {
        var previous = event.getOriginal();
        if (!previous.hasData(DEAttachments.PLATFORM_RETAINED_INVENTORY)) return;
        var state = previous.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY);
        event.getEntity().setData(DEAttachments.PLATFORM_RETAINED_INVENTORY, new RetainedInventoryState(false, state.pending()));
        previous.removeData(DEAttachments.PLATFORM_RETAINED_INVENTORY);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restorePending(player);
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive() && player.tickCount % 20 == 0) restorePending(player);
    }

    private static void restorePending(ServerPlayer player) {
        if (!player.hasData(DEAttachments.PLATFORM_RETAINED_INVENTORY)) return;
        var previous = player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY);
        if (previous.pending().isEmpty()) return;
        var pending = new ObjectArrayList<ItemStack>();
        for (ItemStack stack : previous.pending()) {
            ItemStack remaining = stack.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) pending.add(remaining);
        }
        player.setData(DEAttachments.PLATFORM_RETAINED_INVENTORY, new RetainedInventoryState(false, pending));
        player.inventoryMenu.broadcastChanges();
    }
}
