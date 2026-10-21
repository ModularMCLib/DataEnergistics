package com.fish_dan_.data_energistics.client.terminal.magnet;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.FormattingUtils;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Client-thread HUD data; physical stack quantities and inventory screen rendering remain authoritative. */
@NullMarked
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class TerminalRestockClient {

    private static final Object2LongMap<AEItemKey> AMOUNTS = new Object2LongOpenHashMap<>();

    private TerminalRestockClient() {}

    public static void receive(List<GenericStack> amounts) {
        AMOUNTS.clear();
        for (GenericStack stack : amounts) AMOUNTS.put((AEItemKey) stack.what(), stack.amount());
    }

    public static @Nullable String count(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator()) return null;
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || !AMOUNTS.containsKey(key)) return null;
        long stored = AMOUNTS.getLong(key);
        long available = stored > Long.MAX_VALUE - stack.getCount() ? Long.MAX_VALUE : stored + stack.getCount();
        return FormattingUtils.format(available);
    }

    @SubscribeEvent
    public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        AMOUNTS.clear();
    }
}
