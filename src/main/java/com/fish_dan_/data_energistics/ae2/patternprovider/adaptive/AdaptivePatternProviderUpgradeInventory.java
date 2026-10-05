package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.Tooltips;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Upgrade inventory for an adaptive provider.
 *
 * <p>
 * The physical inventory belongs to the adaptive provider. The adaptive item contributes its fixed base registrations;
 * the currently selected provider contributes additional registrations. Provider item components are not inspected,
 * copied, or modified.
 * </p>
 */
public final class AdaptivePatternProviderUpgradeInventory extends AppEngInternalInventory
                                                           implements InternalInventoryHost, IUpgradeInventory {

    private static final ResourceLocation APPFLUX_INDUCTION_CARD = ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
    private static final ResourceLocation AE2CS_CRYSTAL_GROWTH_CARD = ResourceLocation.fromNamespaceAndPath("ae2cs", "crystal_growth_card");
    private static final ResourceLocation AE2CS_OVERLOAD_CARD = ResourceLocation.fromNamespaceAndPath("ae2cs", "overload_card");
    private static final String EXTENDEDAE_PLUS_NAMESPACE = "extendedae_plus";

    private final Item adaptiveItem;
    private final Supplier<? extends Item> providerItemSupplier;
    @Nullable
    private final Runnable changeCallback;

    private Item cachedProviderItem = Items.AIR;
    private Reference2IntMap<Item> providerMaximums = new Reference2IntArrayMap<>();
    private boolean providerRulesInitialized;
    @Nullable
    private Reference2IntMap<Item> installed;

    public AdaptivePatternProviderUpgradeInventory(ItemLike adaptiveItem,
                                                   Supplier<? extends Item> providerItemSupplier,
                                                   int slots,
                                                   @Nullable Runnable changeCallback) {
        super(null, slots, 1);
        this.adaptiveItem = adaptiveItem.asItem();
        this.providerItemSupplier = providerItemSupplier;
        this.changeCallback = changeCallback;
        this.setHost(this);
        this.setFilter(new UpgradeInventoryFilter());
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    protected boolean eventsEnabled() {
        return true;
    }

    @Override
    public ItemLike getUpgradableItem() {
        return this.adaptiveItem;
    }

    @Override
    public int getMaxInstalled(ItemLike upgradeCard) {
        Item card = upgradeCard.asItem();
        int adaptiveMaximum = adaptiveMaximumFor(card);
        if (adaptiveMaximum > 0) {
            return adaptiveMaximum;
        }

        if (isExtendedAePlusCard(card)) {
            return 0;
        }

        ensureProviderRules();
        return this.providerMaximums.getOrDefault(card, 0);
    }

    @Override
    public int getInstalledUpgrades(ItemLike upgradeCard) {
        ensureProviderRules();
        if (this.installed == null) {
            updateInstalled();
        }
        return this.installed.getOrDefault(upgradeCard.asItem(), 0);
    }

    /**
     * Invalidates the provider-derived rules after the selected provider changes.
     */
    public void refreshProviderRules() {
        this.providerRulesInitialized = false;
        this.installed = null;
    }

    /**
     * Returns tooltip lines for the effective adaptive and selected-provider upgrade rules.
     */
    public List<Component> getCompatibleUpgradeTooltipLines() {
        ensureProviderRules();
        ObjectArrayList<Item> cards = new ObjectArrayList<>();
        for (Item card : BuiltInRegistries.ITEM) {
            if (isRegisteredUpgradeCard(card) && getMaxInstalled(card) > 0) {
                cards.add(card);
            }
        }
        cards.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));

        ObjectArrayList<Component> lines = new ObjectArrayList<>();
        for (Item card : cards) {
            int maximum = getMaxInstalled(card);
            if (maximum <= 0) {
                continue;
            }

            Component description = card.getDescription().copy().withStyle(ChatFormatting.GRAY);
            if (maximum > 1) {
                description = Tooltips.of(
                        description,
                        Tooltips.of(" ("),
                        Tooltips.ofUnformattedNumber(maximum),
                        Tooltips.of(")"));
            }
            lines.add(description);
        }
        return List.copyOf(lines);
    }

    @Override
    public void readFromNBT(CompoundTag data, String subtag, HolderLookup.Provider registries) {
        super.readFromNBT(data, subtag, registries);
        this.installed = null;
        ensureProviderRules();
        updateInstalled();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {}

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.installed = null;
        if (this.changeCallback != null) {
            this.changeCallback.run();
        }
    }

    @Override
    public void sendChangeNotification(int slot) {
        this.installed = null;
        super.sendChangeNotification(slot);
    }

    private void ensureProviderRules() {
        Item providerItem = this.providerItemSupplier.get();
        if (this.providerRulesInitialized && this.cachedProviderItem == providerItem) {
            return;
        }

        this.cachedProviderItem = providerItem;
        this.providerMaximums = new Reference2IntArrayMap<>();
        if (providerItem != Items.AIR) {
            for (Item candidate : BuiltInRegistries.ITEM) {
                if (!isRegisteredUpgradeCard(candidate)) {
                    continue;
                }
                if (isExtendedAePlusCard(candidate)) {
                    continue;
                }

                int maximum = Upgrades.getMaxInstallable(candidate, providerItem);
                if (maximum > 0) {
                    this.providerMaximums.put(candidate, maximum);
                }
            }
        }
        this.providerRulesInitialized = true;
        this.installed = null;
    }

    private void updateInstalled() {
        Reference2IntArrayMap<Item> result = new Reference2IntArrayMap<>(size());
        for (ItemStack stack : this) {
            if (stack.isEmpty()) {
                continue;
            }

            Item card = stack.getItem();
            int maximum = maximumFor(card);
            if (maximum > 0) {
                result.merge(card, 1, (current, ignored) -> Math.min(maximum, current + 1));
            }
        }
        this.installed = result;
    }

    private int maximumFor(Item card) {
        int adaptiveMaximum = adaptiveMaximumFor(card);
        if (adaptiveMaximum > 0) {
            return adaptiveMaximum;
        }
        if (isExtendedAePlusCard(card)) {
            return 0;
        }
        return this.providerMaximums.getOrDefault(card, 0);
    }

    private int adaptiveMaximumFor(Item card) {
        if (!isAdaptiveBaseCard(card)) {
            return 0;
        }
        return Upgrades.getMaxInstallable(card, this.adaptiveItem);
    }

    private static boolean isAdaptiveBaseCard(Item card) {
        return card == AEItems.CAPACITY_CARD.get() || card == DEItems.REDSTONE_TUNING_CARD.get() || BuiltInRegistries.ITEM.getKey(card).equals(APPFLUX_INDUCTION_CARD);
    }

    private static boolean isRegisteredUpgradeCard(Item card) {
        if (Upgrades.isUpgradeCardItem(card)) {
            return true;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(card);
        return id.equals(AE2CS_CRYSTAL_GROWTH_CARD) || id.equals(AE2CS_OVERLOAD_CARD);
    }

    private static boolean isExtendedAePlusCard(Item card) {
        return BuiltInRegistries.ITEM.getKey(card).getNamespace().equals(EXTENDEDAE_PLUS_NAMESPACE);
    }

    private final class UpgradeInventoryFilter implements IAEItemFilter {

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack itemstack) {
            Item card = itemstack.getItem();
            return getInstalledUpgrades(card) < getMaxInstalled(card);
        }
    }
}
