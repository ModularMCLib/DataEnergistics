package com.fish_dan_.data_energistics.menu.research;

import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.research.ResearchStationUi;
import com.fish_dan_.data_energistics.network.research.ResearchActionPayload;
import com.fish_dan_.data_energistics.network.research.ResearchSnapshotPayload;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.research.definition.ResearchDefinitions;
import com.fish_dan_.data_energistics.research.persistence.ResearchNbt;
import com.fish_dan_.data_energistics.research.synchronization.ResearchSnapshots;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/** Owns real slots on both sides; LDLib2 wraps them without duplicating their menu identity. */
public final class ResearchStationMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;
    private final Slot targetSlot;
    private final ResearchStationBlockEntity host;
    private final ModularUI ui;
    private CompoundTag snapshot = new CompoundTag();
    private final Object2ObjectMap<ResourceLocation, ResearchNode> clientNodes = new Object2ObjectLinkedOpenHashMap<>();
    private long viewRevision;
    private int ticks;
    private long sentDefinitionsRevision = -1;

    public ResearchStationMenu(int id, Inventory inventory, ResearchStationBlockEntity host) {
        super(DEMenus.RESEARCH_STATION.get(), id);
        this.playerInventory = inventory;
        this.host = host;
        this.targetSlot = new Slot(host.targetInventory().toContainer(), 0, 0, 0) {

            @Override
            public boolean mayPickup(Player player) {
                return !ResearchStationMenu.this.targetLocked() && super.mayPickup(player);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return !ResearchStationMenu.this.targetLocked() && super.mayPlace(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.ui = ResearchStationUi.mount(this);
    }

    public static void open(ServerPlayer player, ResearchStationBlockEntity host) {
        if (host.faulted()) {
            player.sendSystemMessage(Component.translatable("research.data_energistics.faulted"));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new ResearchStationMenu(id, inventory, host),
                host.getBlockState().getBlock().getName()), buffer -> buffer.writeBlockPos(host.getBlockPos()));
    }

    public Player getPlayer() {
        return playerInventory.player;
    }

    public boolean isServerSide() {
        return !getPlayer().level().isClientSide();
    }

    public Slot targetSlot() {
        return targetSlot;
    }

    @Override
    public boolean stillValid(Player player) {
        return host.getLevel() == player.level() && !host.isRemoved() && !host.faulted() &&
                player.level().getBlockEntity(host.getBlockPos()) == host &&
                player.distanceToSqr(host.getBlockPos().getCenter()) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.mayPickup(player) || !source.hasItem()) return ItemStack.EMPTY;
        ItemStack original = source.getItem().copy();
        ItemStack remaining = source.getItem().copy();
        if (source == targetSlot) {
            for (Slot destination : slots) {
                if (destination.container == playerInventory) remaining = destination.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
        } else {
            remaining = targetSlot.safeInsert(remaining);
        }
        if (remaining.getCount() == original.getCount()) return ItemStack.EMPTY;
        source.setByPlayer(remaining);
        source.onTake(player, original.copyWithCount(original.getCount() - remaining.getCount()));
        return original;
    }

    public ResearchStationBlockEntity host() {
        return host;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // LDLib2's screen mixin disposes the client UI; the native menu owns its server lifecycle.
        if (isServerSide()) ui.onRemoved();
    }

    public CompoundTag snapshot() {
        return snapshot;
    }

    public Object2ObjectMap<ResourceLocation, ResearchNode> nodes() {
        return clientNodes;
    }

    public long viewRevision() {
        return viewRevision;
    }

    public boolean targetLocked() {
        return isServerSide() ? host.state().locked() : snapshot.getBoolean("locked");
    }

    public void request(String action, String argument) {
        PacketDistributor.sendToServer(new ResearchActionPayload(containerId, snapshot.getLong("revision"),
                snapshot.getLong("definitions_revision"), action, argument));
    }

    public void receive(CompoundTag data) {
        this.snapshot = data;
        if (data.contains("nodes")) {
            clientNodes.clear();
            for (Tag value : data.getList("nodes", Tag.TAG_COMPOUND)) {
                ResearchNode node = ResearchNbt.readNode((CompoundTag) value, getPlayer().registryAccess());
                clientNodes.put(node.id(), node);
            }
        }
        this.viewRevision++;
    }

    public void sendSnapshot(boolean definitions) {
        if (getPlayer() instanceof ServerPlayer player) {
            long revision = ResearchDefinitions.revision();
            PacketDistributor.sendToPlayer(player, new ResearchSnapshotPayload(containerId,
                    ResearchSnapshots.station(player, host, definitions || sentDefinitionsRevision != revision)));
            sentDefinitionsRevision = revision;
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && ticks++ % 10 == 0) sendSnapshot(false);
    }
}
