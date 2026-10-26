package com.fish_dan_.data_energistics.client.research;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.research.synchronization.ResearchClientCatalog;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMappings;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Item upgrades follow the stack globally, including other mods' menus and creative inventory. */
public final class ResearchClientEvents {

    private static final int PAGE_SIZE = 5;
    private ItemStack pageStack = ItemStack.EMPTY;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int page;

    public ResearchClientEvents() {
        SyncedKeyMappings.PAGE_PREV.registerScreenListener((screen, key, x, y) -> cyclePage(screen, -1));
        SyncedKeyMappings.PAGE_NEXT.registerScreenListener((screen, key, x, y) -> cyclePage(screen, 1));
    }

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        var ids = ItemResearchUpgrades.of(event.getItemStack()).ids();
        if (ids.isEmpty()) return;
        int pages = (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int shownPage = ItemStack.isSameItemSameComponents(pageStack, event.getItemStack()) ? Math.min(page, pages - 1) : 0;
        var lines = event.getToolTip();
        lines.add(Component.translatable("tooltip.data_energistics.research.completed").withStyle(ChatFormatting.AQUA));
        int start = shownPage * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, ids.size()); index++) {
            var id = ids.get(index);
            var node = ResearchClientCatalog.find(id);
            Component title = node == null ? Component.literal(id.toString()) : Component.translatable(node.name());
            lines.add(Component.literal("  ").append(title).withStyle(ChatFormatting.GRAY));
        }
        if (pages > 1) {
            lines.add(Component.translatable("screen.data_energistics.page", shownPage + 1, pages).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.data_energistics.page_navigation",
                    SyncedKeyMappings.PAGE_PREV.getTranslatedKeyMessage(), SyncedKeyMappings.PAGE_NEXT.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beginFrame(ScreenEvent.Render.Pre event) {
        hoveredStack = ItemStack.EMPTY;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderedTooltip(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (ItemResearchUpgrades.of(stack).ids().isEmpty()) return;
        if (!ItemStack.isSameItemSameComponents(pageStack, stack)) {
            pageStack = stack.copy();
            page = 0;
        }
        hoveredStack = stack;
    }

    private boolean cyclePage(Screen screen, int direction) {
        // Tooltip construction also runs during searches. Only the item actually rendered this frame
        // may consume a page key, and text fields retain their normal keyboard input.
        if (screen.getFocused() instanceof EditBox input && input.isFocused()) return false;
        int count = ItemResearchUpgrades.of(hoveredStack).ids().size();
        int pages = (count + PAGE_SIZE - 1) / PAGE_SIZE;
        if (pages <= 1) return false;
        page = Math.floorMod(page + direction, pages);
        return true;
    }

    @SubscribeEvent
    public void opening(ScreenEvent.Opening event) {
        clearPage();
    }

    @SubscribeEvent
    public void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        clearPage();
        ResearchClientCatalog.clear();
    }

    private void clearPage() {
        pageStack = ItemStack.EMPTY;
        hoveredStack = ItemStack.EMPTY;
        page = 0;
    }
}
