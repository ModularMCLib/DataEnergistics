package com.fish_dan_.data_energistics.menu.sanctum;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.menu.ContainerSlotInteraction;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.helpers.InventoryAction;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot.PlacableItemType;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

public class DataSanctumLargeInterfaceMenu extends UpgradeableMenu<DataSanctumLargeInterfaceHost> {

    public static final String ACTION_CONFIGURE_SLOT = "configure_slot";
    public static final String ACTION_SET_PAGE = "set_page";
    public static final int CONFIG_SLOT_COUNT = DataSanctumInterfaceConstants.CONFIG_SLOTS_PER_PAGE;
    public static final int STOCK_SLOT_COUNT = DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE;
    public static final int RETURN_SLOT_COUNT = DataSanctumInterfaceConstants.RETURN_SLOTS_PER_PAGE;
    public static final SlotSemantic RETURN_ROW_1 = SlotSemantics.register("DATA_SANCTUM_LARGE_INTERFACE_RETURN_ROW_1", false);
    public static final SlotSemantic RETURN_ROW_2 = SlotSemantics.register("DATA_SANCTUM_LARGE_INTERFACE_RETURN_ROW_2", false);

    @JsonAdapter(PageSlotTarget.Adapter.class)
    public record PageSlotTarget(@Nullable Integer pageIndex, @Nullable Integer slotOnPage) {

        private static final Pattern JSON_INTEGER = Pattern.compile("-?(?:0|[1-9][0-9]*)");
        private static final BigInteger MINIMUM_INTEGER = BigInteger.valueOf(Integer.MIN_VALUE);
        private static final BigInteger MAXIMUM_INTEGER = BigInteger.valueOf(Integer.MAX_VALUE);

        public static final class Adapter implements JsonDeserializer<PageSlotTarget> {

            @Override
            public PageSlotTarget deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
                if (!json.isJsonObject()) {
                    return new PageSlotTarget(null, null);
                }

                JsonObject object = json.getAsJsonObject();
                return new PageSlotTarget(readInteger(object.get("pageIndex")), readInteger(object.get("slotOnPage")));
            }

            @Nullable
            private static Integer readInteger(@Nullable JsonElement value) {
                if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                    return null;
                }

                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (!primitive.isNumber()) {
                    return null;
                }

                String serialized = primitive.getAsString();
                if (!JSON_INTEGER.matcher(serialized).matches()) {
                    return null;
                }

                BigInteger integer = new BigInteger(serialized);
                if (integer.compareTo(MINIMUM_INTEGER) < 0 || integer.compareTo(MAXIMUM_INTEGER) > 0) {
                    return null;
                }
                return integer.intValue();
            }
        }
    }

    @GuiSync(860)
    public int pageIndex;
    @GuiSync(861)
    public int totalPages = DataSanctumInterfaceConstants.BASE_PAGE_COUNT;
    @GuiSync(863)
    public int unlimitedSlotsMask;
    @GuiSync(864)
    public int prioritySlotsMask;

    public record SlotConfiguration(PageSlotTarget target, String expectedKey, String amount,
                                    Boolean unlimited, ConnectorPolicy policy) {}

    private List<Slot> configSlots;

    public DataSanctumLargeInterfaceMenu(int id, Inventory playerInventory, DataSanctumLargeInterfaceHost host) {
        super(DEMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), id, playerInventory, host);
        registerClientAction(ACTION_CONFIGURE_SLOT, SlotConfiguration.class, this::applySlotConfiguration);
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getInterfaceLogic().getStorage();
        var returnInventory = this.getHost().getReturnInventory();
        for (int i = 0; i < STOCK_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.addSlot(new AppEngSlot(new PagedMenuInventory(storage, () -> DataSanctumInterfaceConstants.stockSlotIndex(this.pageIndex, slotOnPage), this::isClientSide), 0).setNotDraggable(), SlotSemantics.STORAGE);
        }
        for (int i = 0; i < CONFIG_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.addSlot(new AppEngSlot(new PagedMenuInventory(returnInventory, () -> DataSanctumInterfaceConstants.returnSlotIndex(this.pageIndex, slotOnPage), this::isClientSide), 0), RETURN_ROW_1);
        }
        for (int i = CONFIG_SLOT_COUNT; i < RETURN_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.addSlot(new AppEngSlot(new PagedMenuInventory(returnInventory, () -> DataSanctumInterfaceConstants.returnSlotIndex(this.pageIndex, slotOnPage), this::isClientSide), 0), RETURN_ROW_2);
        }
    }

    @Override
    protected void setupConfig() {
        this.configSlots = new ObjectArrayList<>(CONFIG_SLOT_COUNT);
        var config = this.getHost().getInterfaceLogic().getConfig();
        for (int i = 0; i < CONFIG_SLOT_COUNT; i++) {
            int slotOnPage = i;
            this.configSlots.add(this.addSlot(new PagedFakeSlot(new PagedMenuInventory(config, () -> DataSanctumInterfaceConstants.stockSlotIndex(this.pageIndex, slotOnPage), this::isClientSide)).setNotDraggable(), SlotSemantics.CONFIG));
        }
    }

    @Override
    protected void setupUpgrades() {
        var upgrades = this.getHost().getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            this.addSlot(new RestrictedInputSlot(PlacableItemType.UPGRADES, upgrades, i), SlotSemantics.UPGRADE);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.totalPages = this.getHost().getUnlockedPageCount();
            this.pageIndex = clampPage(this.pageIndex);
            this.unlimitedSlotsMask = 0;
            this.prioritySlotsMask = 0;
            var config = (DataSanctumInterfaceInventory) getHost().getConfig();
            for (int i = 0; i < CONFIG_SLOT_COUNT; i++) {
                int slot = DataSanctumInterfaceConstants.stockSlotIndex(pageIndex, i);
                if (config.isUnlimitedSlot(slot)) this.unlimitedSlotsMask |= 1 << i;
                if (config.getSlotPolicy(slot) == ConnectorPolicy.PRIORITY) this.prioritySlotsMask |= 1 << i;
            }
        }

        super.broadcastChanges();
    }

    public List<Slot> getConfigSlots() {
        return this.configSlots != null ? this.configSlots : List.of();
    }

    /** Identifies the upper rows that only accept individually placed cursor items in this menu. */
    public boolean isManualPlacementSlot(@Nullable Slot slot) {
        if (slot == null) return false;
        var semantic = getSlotSemantic(slot);
        return semantic == SlotSemantics.CONFIG || semantic == SlotSemantics.STORAGE;
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot slot, ItemStack stack, boolean fromPlayerSide) {
        return !isManualPlacementSlot(slot) && super.isValidQuickMoveDestination(slot, stack, fromPlayerSide);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (isClientSide() || slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
        if (ContainerSlotInteraction.tryQuickMove(this, slotIndex, player, appEngSlot -> appEngSlot.getInventory() instanceof PagedMenuInventory paged ? paged.backingSlot() : appEngSlot.getContainerSlot())) {
            return ItemStack.EMPTY;
        }
        var source = slots.get(slotIndex);
        if (isPlayerSideSlot(source) && getQuickMoveDestinationSlots(source.getItem(), true).isEmpty()) {
            // AE2 otherwise falls back to writing an empty filter slot when real destinations are unavailable.
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, slotIndex);
    }

    public boolean isUnlimitedConfigSlot(int slotOnPage) {
        if (slotOnPage < 0 || slotOnPage >= CONFIG_SLOT_COUNT || this.getHost() == null) {
            return false;
        }
        return (unlimitedSlotsMask & (1 << slotOnPage)) != 0;
    }

    public ConnectorPolicy getSlotPolicy(int slotOnPage) {
        return (prioritySlotsMask & (1 << slotOnPage)) != 0 ? ConnectorPolicy.PRIORITY : ConnectorPolicy.ROUND_ROBIN;
    }

    public void configureSlot(int page, int slot, AEKey key, long amount, boolean unlimited, ConnectorPolicy policy) {
        var request = new SlotConfiguration(new PageSlotTarget(page, slot), key.toTag(registryAccess()).toString(),
                Long.toString(amount), unlimited, policy);
        if (isClientSide()) {
            unlimitedSlotsMask = unlimited ? unlimitedSlotsMask | (1 << slot) : unlimitedSlotsMask & ~(1 << slot);
            prioritySlotsMask = policy == ConnectorPolicy.PRIORITY ? prioritySlotsMask | (1 << slot) : prioritySlotsMask & ~(1 << slot);
            sendClientAction(ACTION_CONFIGURE_SLOT, request);
        } else {
            applySlotConfiguration(request);
        }
    }

    public void sendSetPage(int page) {
        this.pageIndex = clampPage(page);
        sendClientAction(ACTION_SET_PAGE, this.pageIndex);
    }

    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slotId, long id) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof AppEngSlot slot && !(slot instanceof FakeSlot) && slot.getInventory() instanceof PagedMenuInventory inventory) {
            var backing = inventory.getDelegate();
            int index = inventory.backingSlot();
            var key = backing.getKey(index);
            if (action == InventoryAction.FILL_ITEM || action == InventoryAction.FILL_ENTIRE_ITEM) {
                if (key != null) handleFillingHeldItem((amount, mode) -> backing.extract(index, key, amount, mode),
                        key, action == InventoryAction.FILL_ENTIRE_ITEM);
                return;
            }
            if (action == InventoryAction.EMPTY_ITEM || action == InventoryAction.EMPTY_ENTIRE_ITEM) {
                handleEmptyHeldItem((what, amount, mode) -> backing.insert(index, what, amount, mode),
                        action == InventoryAction.EMPTY_ENTIRE_ITEM);
                return;
            }
        }
        super.doAction(player, action, slotId, id);
        getHost().getInterfaceLogic().updateStorage();
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId < 0 || slotId >= slots.size() || !(slots.get(slotId) instanceof AppEngSlot slot) || slot instanceof FakeSlot || !(slot.getInventory() instanceof PagedMenuInventory inventory)) {
            super.clicked(slotId, button, type, player);
            return;
        }
        if (isClientSide()) return; // Server performs transfers; slot snapshots are display data only.
        if (type == ClickType.PICKUP && ContainerSlotInteraction.tryClicked(
                this,
                slot,
                inventory.getDelegate(),
                inventory.backingSlot(),
                player)) {
            broadcastChanges();
            return;
        }
        if (type == ClickType.PICKUP && !getCarried().isEmpty() && ContainerItemStrategies.findCarriedContext(null, player, this) != null) {
            // A registered container must never fall through to the item-key branch when this slot cannot accept it.
            return;
        }
        var backing = inventory.getDelegate();
        int index = inventory.backingSlot();
        var key = backing.getKey(index);
        var carried = getCarried();
        if (type == ClickType.PICKUP && !carried.isEmpty()) {
            AEItemKey carriedKey = AEItemKey.of(carried);
            if (carriedKey != null && (key == null || key instanceof AEItemKey)) {
                long inserted = backing.insert(index, carriedKey, button == 1 ? 1 : carried.getCount(), Actionable.MODULATE);
                carried.shrink((int) inserted); // A real cursor stack is bounded by Minecraft's stack size.
                setCarried(carried);
            } else if (button == 0) {
                handleFillingHeldItem((amount, mode) -> backing.extract(index, key, amount, mode), key, false);
            } else {
                handleEmptyHeldItem((what, amount, mode) -> backing.insert(index, what, amount, mode), false);
            }
        } else if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && key instanceof AEItemKey itemKey) {
            long available = backing.getAmount(index);
            long request = Math.min(itemKey.getMaxStackSize(), button == 1 ? available / 2 + available % 2 : available);
            long extracted = backing.extract(index, key, request, Actionable.MODULATE);
            var taken = itemKey.toStack((int) extracted);
            if (type == ClickType.QUICK_MOVE) {
                player.getInventory().add(taken);
                if (!taken.isEmpty()) {
                    // Player inventory was full; retain refused network returns as owned items.
                    long restored = backing.insert(index, key, taken.getCount(), Actionable.MODULATE);
                    taken.shrink((int) restored);
                    if (!taken.isEmpty()) player.drop(taken, false);
                }
            } else {
                setCarried(taken);
            }
        }
        broadcastChanges();
    }

    private void applySlotConfiguration(@Nullable SlotConfiguration request) {
        if (request == null || request.amount() == null || request.unlimited() == null || request.policy() == null) return;
        PageSlotTarget target = request.target();

        if (target == null || target.pageIndex() == null || target.slotOnPage() == null) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Sanctum Large Interface set-amount client action without a complete target at {}: page={}, slot={}",
                    getHost().getInterfaceBlockPos(),
                    target == null ? null : target.pageIndex(),
                    target == null ? null : target.slotOnPage());
            return;
        }

        int targetPage = target.pageIndex();
        int slotOnPage = target.slotOnPage();
        int serverPage = this.pageIndex;
        int unlockedPageCount = getHost().getUnlockedPageCount();
        if (serverPage < 0 || serverPage >= unlockedPageCount) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Sanctum Large Interface set-amount client action for page {} and slot {} because server page {} is outside [0, {}) at {}",
                    targetPage,
                    slotOnPage,
                    serverPage,
                    unlockedPageCount,
                    getHost().getInterfaceBlockPos());
            return;
        }

        if (targetPage != serverPage) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Sanctum Large Interface set-amount client action for stale page {} and slot {}; server page is {} of {} at {}",
                    targetPage,
                    slotOnPage,
                    serverPage,
                    unlockedPageCount,
                    getHost().getInterfaceBlockPos());
            return;
        }

        if (slotOnPage < 0 || slotOnPage >= CONFIG_SLOT_COUNT) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Sanctum Large Interface set-amount client action for page {} with slot {} outside [0, {}) at {}",
                    serverPage,
                    slotOnPage,
                    CONFIG_SLOT_COUNT,
                    getHost().getInterfaceBlockPos());
            return;
        }

        var config = (DataSanctumInterfaceInventory) getHost().getConfig();
        int configSlot = DataSanctumInterfaceConstants.stockSlotIndex(serverPage, slotOnPage);
        if (configSlot < 0 || configSlot >= config.size()) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Data Sanctum Large Interface set-amount client action for page {} and slot {} because config slot {} is outside inventory size {} at {}",
                    serverPage,
                    slotOnPage,
                    configSlot,
                    config.size(),
                    getHost().getInterfaceBlockPos());
            return;
        }

        var stack = config.getStack(configSlot);
        if (stack == null || !stack.what().toTag(registryAccess()).toString().equals(request.expectedKey())) return;
        long amount;
        try {
            amount = Long.parseLong(request.amount());
        } catch (NumberFormatException invalidAmount) {
            Data_Energistics.LOGGER.warn("Rejected invalid interface stock amount at {}: {}", getHost().getInterfaceBlockPos(), request.amount());
            return;
        }
        if (amount < 0) return;
        config.beginBatch();
        try {
            if (amount == 0) {
                config.setStack(configSlot, null);
            } else {
                config.setStack(configSlot, new GenericStack(stack.what(), amount));
                config.setUnlimitedSlot(configSlot, request.unlimited());
                config.setSlotPolicy(configSlot, request.policy());
            }
        } finally {
            config.endBatch();
        }
        getHost().getInterfaceLogic().updateStorage();
        broadcastChanges();
    }

    private void setPage(Integer page) {
        if (page == null) {
            return;
        }
        this.pageIndex = clampPage(page);
        broadcastChanges();
    }

    private int clampPage(int page) {
        int pages = Math.max(1, this.totalPages);
        if (isServerSide() && this.getHost() != null) {
            pages = Math.max(1, this.getHost().getUnlockedPageCount());
        }
        return Math.max(0, Math.min(page, pages - 1));
    }

    private static final class PagedMenuInventory extends ConfigMenuInventory {

        private final IntSupplier backingSlotSupplier;
        private final BooleanSupplier clientSide;
        private ItemStack snapshot = ItemStack.EMPTY;

        private PagedMenuInventory(GenericStackInv inv, IntSupplier backingSlotSupplier, BooleanSupplier clientSide) {
            super(inv);
            this.backingSlotSupplier = backingSlotSupplier;
            this.clientSide = clientSide;
        }

        private int backingSlot() {
            return this.backingSlotSupplier.getAsInt();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return super.isItemValid(backingSlot(), stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return super.getSlotLimit(backingSlot());
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return clientSide.getAsBoolean() ? snapshot : GenericStack.wrapInItemStack(getDelegate().getStack(backingSlot()));
        }

        @Override
        public @Nullable GenericStack convertToSuitableStack(ItemStack stack) {
            GenericStack wrapped = GenericStack.unwrapItemStack(stack);
            return wrapped != null ? wrapped : super.convertToSuitableStack(stack);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (clientSide.getAsBoolean()) {
                snapshot = stack.copy();
            } else {
                super.setItemDirect(backingSlot(), stack);
            }
        }
    }

    private static final class PagedFakeSlot extends FakeSlot {

        private final PagedMenuInventory inventory;

        private PagedFakeSlot(PagedMenuInventory inv) {
            super(inv, 0);
            this.inventory = inv;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
        }

        @Override
        public void increase(ItemStack stack) {
            var realInv = this.inventory.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                GenericStack newFilter = this.inventory.convertToSuitableStack(stack);
                int backingSlot = this.inventory.backingSlot();
                if (newFilter != null && newFilter.what().equals(realInv.getKey(backingSlot))) {
                    realInv.insert(backingSlot, newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }
            set(stack);
        }

        @Override
        public void decrease(ItemStack stack) {
            var realInv = this.inventory.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                GenericStack newFilter = this.inventory.convertToSuitableStack(stack);
                if (newFilter != null) {
                    realInv.extract(this.inventory.backingSlot(), newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }

            ItemStack current = getItem();
            if (stack.isEmpty()) {
                current = current.copy();
                current.shrink(1);
                set(current);
            } else if (ItemStack.isSameItemSameComponents(current, stack)) {
                current = current.copy();
                current.grow(1);
                set(current);
            } else {
                stack = stack.copy();
                stack.setCount(1);
                set(stack);
            }
        }
    }
}
