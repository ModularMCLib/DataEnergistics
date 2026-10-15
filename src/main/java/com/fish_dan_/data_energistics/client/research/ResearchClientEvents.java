package com.fish_dan_.data_energistics.client.research;

import com.fish_dan_.data_energistics.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.research.synchronization.ResearchClientCatalog;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Item upgrades follow the stack globally, including other mods' menus and creative inventory. */
public final class ResearchClientEvents {

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        for (var id : ItemResearchUpgrades.of(event.getItemStack()).ids()) {
            var node = ResearchClientCatalog.find(id);
            Component title = node == null ? Component.literal(id.toString()) : Component.translatable(node.name());
            event.getToolTip().add(Component.translatable("tooltip.data_energistics.research.upgrade", title).withStyle(ChatFormatting.AQUA));
        }
    }

    @SubscribeEvent
    public void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        ResearchClientCatalog.clear();
    }
}
