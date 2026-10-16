package com.fish_dan_.data_energistics.menu.machine.anvil;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.blockentity.machine.anvil.MeDigitalAnvilBlockEntity;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.anvil.enchantment.DigitalAnvilCalculation;
import com.fish_dan_.data_energistics.common.anvil.enchantment.DigitalAnvilOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.anvil.MeDigitalAnvilUi;
import com.fish_dan_.data_energistics.network.anvil.DigitalAnvilActionPayload;
import com.fish_dan_.data_energistics.network.anvil.DigitalAnvilSnapshotPayload;
import com.fish_dan_.data_energistics.registry.DEMenus;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import appeng.api.networking.security.IActionSource;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native slots and a server-owned anvil preview. The result is transferred only after payment succeeds. */
public final class MeDigitalAnvilMenu extends AbstractContainerMenu {

    private final Inventory inventory;
    private final MeDigitalAnvilBlockEntity host;
    private final SimpleContainer inputs = new SimpleContainer(2);
    private final SimpleContainer result = new SimpleContainer(1);
    private final Slot left;
    private final Slot right;
    private final Slot output;
    private final ModularUI ui;
    private DigitalAnvilOperation operation = DigitalAnvilOperation.EMPTY;
    private CompoundTag snapshot = new CompoundTag();
    private ItemStack previousLeft = ItemStack.EMPTY;
    private String name = "";
    private int requestedBoost;
    private int upgrades;
    private int ticks;
    private boolean changingInputs;
    private boolean ready;

    public MeDigitalAnvilMenu(int id, Inventory inventory, MeDigitalAnvilBlockEntity host) {
        super(DEMenus.ME_DIGITAL_ANVIL.get(), id);
        this.inventory = inventory;
        this.host = host;
        this.left = new Slot(inputs, 0, 0, 0) {

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.right = new Slot(inputs, 1, 0, 0);
        this.output = new Slot(result, 0, 0, 0) {

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return isServerSide() ? ready : snapshot.getBoolean("ready");
            }
        };
        inputs.addListener(container -> {
            if (isServerSide() && !changingInputs) refresh();
        });
        this.ui = MeDigitalAnvilUi.mount(this);
    }

    public static void open(ServerPlayer player, MeDigitalAnvilBlockEntity host) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new MeDigitalAnvilMenu(id, inventory, host),
                host.getBlockState().getBlock().getName()), buffer -> buffer.writeBlockPos(host.getBlockPos()));
    }

    public Player getPlayer() {
        return inventory.player;
    }

    public boolean isServerSide() {
        return !getPlayer().level().isClientSide();
    }

    public MeDigitalAnvilBlockEntity host() {
        return host;
    }

    public Slot leftSlot() {
        return left;
    }

    public Slot rightSlot() {
        return right;
    }

    public Slot outputSlot() {
        return output;
    }

    public CompoundTag snapshot() {
        return snapshot;
    }

    public void receive(CompoundTag data) {
        snapshot = data;
    }

    public void request(String action, String value) {
        PacketDistributor.sendToServer(new DigitalAnvilActionPayload(containerId, action, value));
    }

    public void action(String action, String value) {
        if (!isServerSide()) return;
        refresh();
        switch (action) {
            case "rename" -> {
                String filtered = StringUtil.filterText(value);
                if (filtered.length() > 50) return;
                name = filtered;
            }
            case "boost_up", "boost_down" -> {
                if (!DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.enabled(upgrades)) return;
                requestedBoost = Math.clamp(requestedBoost + (action.equals("boost_up") ? 1 : -1), 0, 254);
            }
            default -> {
                return;
            }
        }
        refresh();
        sendSnapshot();
    }

    @Override
    public boolean stillValid(Player player) {
        return !host.isRemoved() && player.level() == host.getLevel() &&
                player.level().getBlockEntity(host.getBlockPos()) == host &&
                player.distanceToSqr(host.getBlockPos().getCenter()) <= 64;
    }

    private void refresh() {
        try {
            updatePreview();
        } catch (RuntimeException error) {
            host.fault(error);
            operation = DigitalAnvilOperation.EMPTY;
            result.setItem(0, ItemStack.EMPTY);
            ready = false;
        }
    }

    private void updatePreview() {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        ItemStack currentLeft = inputs.getItem(0);
        if (!ItemStack.matches(previousLeft, currentLeft)) {
            name = currentLeft.isEmpty() ? "" : currentLeft.getHoverName().getString();
            requestedBoost = 0;
            previousLeft = currentLeft.copy();
        }
        upgrades = 0;
        for (DigitalAnvilUpgrade upgrade : DigitalAnvilUpgrade.values()) {
            if (ResearchAccess.isUnlocked(player, null, upgrade.researchId())) upgrades |= upgrade.mask();
        }
        if (!DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.enabled(upgrades)) requestedBoost = 0;
        operation = host.faulted() ? DigitalAnvilOperation.EMPTY :
                DigitalAnvilCalculation.calculate(currentLeft, inputs.getItem(1), name, upgrades, requestedBoost);
        if (!ItemStack.matches(result.getItem(0), operation.output())) result.setItem(0, operation.output().copy());
        var grid = host.getMainNode().getGrid();
        ready = !operation.output().isEmpty() && grid != null && host.getMainNode().isOnline() &&
                host.payment().available(grid, IActionSource.ofPlayer(player, host), operation.cost());
    }

    /** Handles every result click before vanilla can mutate the carried stack, hotbar or inventory. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId != output.index) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (!isServerSide() || player.isSpectator() || !stillValid(player)) return;
        try {
            refresh();
            if (!ready) return;
            ItemStack product = operation.output().copy();
            switch (clickType) {
                case PICKUP -> {
                    if (button != 0 && button != 1 || !fits(getCarried(), product)) return;
                    if (!commit(player)) return;
                    ItemStack carried = getCarried();
                    if (carried.isEmpty()) setCarried(product);
                    else carried.grow(product.getCount());
                }
                case QUICK_MOVE -> quickMoveStack(player, slotId);
                case SWAP -> {
                    if (button < 0 || button > 8 && button != 40 || !fits(inventory.getItem(button), product)) return;
                    if (!commit(player)) return;
                    ItemStack destination = inventory.getItem(button);
                    if (destination.isEmpty()) inventory.setItem(button, product);
                    else destination.grow(product.getCount());
                }
                case THROW -> {
                    if (button != 0 && button != 1 || !getCarried().isEmpty()) return;
                    if (commit(player)) player.drop(product, true);
                }
                default -> {
                    return;
                }
            }
            broadcastChanges();
        } catch (RuntimeException error) {
            host.fault(error);
            ready = false;
            result.setItem(0, ItemStack.EMPTY);
            sendSnapshot();
        }
    }

    private boolean commit(Player player) {
        var grid = host.getMainNode().getGrid();
        if (grid == null || !host.getMainNode().isOnline() || host.faulted() ||
                !player.mayInteract(player.level(), host.getBlockPos()))
            return false;
        if (!host.payment().pay(grid, IActionSource.ofPlayer(player, host), operation.cost(), host::saveChanges)) return false;
        changingInputs = true;
        try {
            if (!operation.preserveLeft()) inputs.setItem(0, ItemStack.EMPTY);
            inputs.removeItem(1, operation.rightConsumed());
            result.setItem(0, ItemStack.EMPTY);
            operation = DigitalAnvilOperation.EMPTY;
            ready = false;
            player.level().levelEvent(1030, host.getBlockPos(), 0);
        } finally {
            changingInputs = false;
        }
        return true;
    }

    private static boolean fits(ItemStack destination, ItemStack product) {
        return destination.isEmpty() || ItemStack.isSameItemSameComponents(destination, product) &&
                destination.getCount() + product.getCount() <= destination.getMaxStackSize();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original = source.getItem().copy();
        ItemStack remaining = original.copy();
        if (source == output) {
            if (!isServerSide()) return ItemStack.EMPTY;
            refresh();
            if (!ready || !ItemStack.matches(original, operation.output())) return ItemStack.EMPTY;
            boolean space = false;
            for (Slot destination : slots) {
                if (destination.container == inventory && fits(destination.getItem(), original)) {
                    space = true;
                    break;
                }
            }
            if (!space || !commit(player)) return ItemStack.EMPTY;
            for (Slot destination : slots) {
                if (destination.container == inventory) remaining = destination.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
            if (!remaining.isEmpty()) throw new IllegalStateException("Anvil result lost its reserved inventory space");
            return original;
        }
        if (source.container == inputs) {
            for (Slot destination : slots) {
                if (destination.container == inventory) remaining = destination.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
        } else {
            remaining = left.hasItem() ? right.safeInsert(remaining) : left.safeInsert(remaining);
        }
        if (remaining.getCount() == original.getCount()) return ItemStack.EMPTY;
        source.setByPlayer(remaining);
        source.onTake(player, original.copyWithCount(original.getCount() - remaining.getCount()));
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot != output && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot != output && super.canDragTo(slot);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) refresh();
        super.broadcastChanges();
        if (isServerSide() && ticks++ % 5 == 0) sendSnapshot();
    }

    private void sendSnapshot() {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        CompoundTag data = new CompoundTag();
        data.putString("name", name);
        data.putInt("boost", requestedBoost);
        data.putInt("effective_boost", operation.levelBoost());
        data.putInt("upgrades", upgrades);
        data.putBoolean("ready", ready);
        data.putBoolean("valid", !operation.output().isEmpty());
        data.putBoolean("online", host.getMainNode().isOnline());
        data.putBoolean("faulted", host.faulted());
        data.putBoolean("refund_pending", host.payment().pending());
        var cost = operation.cost();
        data.putLong("energy", cost.energy());
        data.putLong("levels", cost.experienceLevels());
        data.putLong("experience", cost.experiencePoints());
        data.putLong("data_flow", cost.dataFlow());
        data.putLong("stellar_flux", cost.stellarFlux());
        data.putLong("echo", cost.echo());
        if (!data.equals(snapshot)) {
            snapshot = data;
            PacketDistributor.sendToPlayer(player, new DigitalAnvilSnapshotPayload(containerId, data));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (isServerSide()) {
            changingInputs = true;
            clearContainer(player, inputs);
            ui.onRemoved();
        }
    }
}
