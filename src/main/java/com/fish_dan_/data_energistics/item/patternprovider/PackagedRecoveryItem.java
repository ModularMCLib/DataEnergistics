package com.fish_dan_.data_energistics.item.patternprovider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.patternprovider.packaged.DigitalPackagedPatternProviderLogic;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.parts.IPartHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** One-use receipt for server-owned tasks; copying this item does not copy its escrow. */
public final class PackagedRecoveryItem extends Item {

    private static final String KEY = "data_energistics_packaged_recovery";

    public PackagedRecoveryItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.setUnlimitedLifetime();
        return false;
    }

    public static ItemStack create(ServerLevel level, BlockPos origin, UUID receipt) {
        var payload = new CompoundTag();
        payload.putUUID("receipt", receipt);
        payload.putString("dimension", level.dimension().location().toString());
        payload.putLong("origin", origin.asLong());
        var data = new CompoundTag();
        data.put(KEY, payload);
        var stack = new ItemStack(DEItems.PACKAGED_RECOVERY.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public static @Nullable UUID receipt(ServerLevel level, BlockPos origin, ItemStack stack) {
        if (!stack.is(DEItems.PACKAGED_RECOVERY.get())) return null;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return null;
        var payload = custom.copyTag().getCompound(KEY);
        if (!payload.hasUUID("receipt") || !payload.contains("origin", Tag.TAG_LONG) ||
                payload.getLong("origin") != origin.asLong() ||
                !payload.getString("dimension").equals(level.dimension().location().toString()))
            return null;
        return payload.getUUID("receipt");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        var player = context.getPlayer();
        if (player == null || !level.mayInteract(player, context.getClickedPos()) ||
                !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        var entity = level.getBlockEntity(context.getClickedPos());
        PatternProviderLogicHost host = entity instanceof PatternProviderLogicHost provider ? provider : null;
        if (entity instanceof IPartHost cable && cable.getPart(context.getClickedFace()) instanceof PatternProviderLogicHost part) host = part;
        boolean restored = false;
        try {
            if (host != null) {
                var logic = host.getLogic();
                if (logic instanceof DigitalPackagedPatternProviderLogic packaged) restored = packaged.restoreRecoveryItem(context.getItemInHand());
                else if (logic instanceof AdaptivePatternProviderLogic adaptive) restored = adaptive.restoreRecoveryItem(context.getItemInHand());
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Packaged recovery failed for player {} in {} at {}",
                    player.getUUID(), level.dimension().location(), context.getClickedPos(), exception);
            player.displayClientMessage(Component.translatable("item.data_energistics.packaged_recovery.invalid"), true);
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.translatable("item.data_energistics.packaged_recovery." + (restored ? "restored" : "unavailable")), true);
        if (restored) context.getItemInHand().shrink(1);
        return restored ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("item.data_energistics.packaged_recovery.description"));
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;
        var payload = custom.copyTag().getCompound(KEY);
        if (!payload.contains("origin", Tag.TAG_LONG)) return;
        var origin = BlockPos.of(payload.getLong("origin"));
        lines.add(Component.translatable("item.data_energistics.packaged_recovery.origin", payload.getString("dimension"),
                origin.getX(), origin.getY(), origin.getZ()));
    }
}
