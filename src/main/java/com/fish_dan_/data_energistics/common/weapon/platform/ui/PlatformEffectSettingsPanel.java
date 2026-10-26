package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumMap;
import java.util.Locale;

/** Registry-backed effect controls grouped into independently enabled and collapsible categories. */
final class PlatformEffectSettingsPanel extends WeaponPlatformScrollView {

    private final Player player;
    private final EnumMap<MobEffectCategory, CategoryGroup> groups = new EnumMap<>(MobEffectCategory.class);
    private final UIElement pinnedHeading = new UIElement();
    private final WeaponPlatformToggleRow pinnedMaster;
    private final Button pinnedFold = new Button();
    private MobEffectCategory pinnedCategory = MobEffectCategory.BENEFICIAL;
    private boolean restoreScrollPending;
    private float pendingScrollOffset;

    PlatformEffectSettingsPanel(Player player) {
        this.player = player;
        addClass("platform-effect-settings");
        pinnedHeading.addClass("platform-effect-sticky-heading");
        pinnedMaster = createMaster(categoryName(pinnedCategory), on -> setCategorySuppressed(pinnedCategory, on));
        pinnedMaster.toggle.setOnSwitchChanged(on -> setCategoryTooltip(pinnedMaster, categoryName(pinnedCategory), on));
        pinnedMaster.addClass("platform-effect-category-master");
        pinnedFold.addClass("platform-action");
        pinnedFold.addClass("platform-effect-category-fold");
        pinnedFold.setOnClick(event -> groups.get(pinnedCategory).toggleExpanded());
        pinnedHeading.addChildren(pinnedMaster, pinnedFold);
        addChild(pinnedHeading);

        UIElement content = new UIElement();
        content.addClass("platform-effect-categories");
        for (MobEffectCategory category : MobEffectCategory.values()) {
            CategoryGroup group = createGroup(category);
            groups.put(category, group);
            content.addChild(group.element);
        }
        for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            MobEffect effect = entry.getValue();
            CategoryGroup group = groups.get(effect.getCategory());
            WeaponPlatformToggleRow row = createEffectRow(id, effect);
            group.rows.add(new EffectEntry(id, row));
        }
        for (var group : groups.values()) {
            group.rows.sort((first, second) -> first.row().label.getValue().getString().compareToIgnoreCase(second.row().label.getValue().getString()));
            for (EffectEntry entry : group.rows) group.effects.addChild(entry.row());
        }

        groups.get(MobEffectCategory.BENEFICIAL).setExpanded(true);
        for (var entry : groups.entrySet()) if (entry.getKey() != MobEffectCategory.BENEFICIAL) entry.getValue().setExpanded(false);
        addScrollViewChild(content);
        addEventListener(UIEvents.TICK, event -> updatePinnedHeading());
        updatePinnedHeading();
    }

    void bind(PlatformSnapshot snapshot) {
        boolean researched = ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformChipUpgrades.EFFECT_SUPPRESSION);
        for (var group : groups.values()) {
            group.master.setActive(researched);
            boolean allSuppressed = !group.rows.isEmpty();
            for (EffectEntry entry : group.rows) {
                boolean suppressed = snapshot.effects().suppresses(entry.id());
                entry.row().toggle.setOn(suppressed, false);
                entry.row().setActive(researched);
                allSuppressed &= suppressed;
            }
            group.master.toggle.setOn(allSuppressed, false);
            setCategoryTooltip(group.master, categoryName(group.category), allSuppressed);
            for (EffectEntry entry : group.rows) setEffectTooltip(entry.row(), snapshot.effects().suppresses(entry.id()));
        }
        CategoryGroup pinned = groups.get(pinnedCategory);
        pinnedMaster.setActive(researched);
        pinnedMaster.toggle.setOn(pinned.master.toggle.isOn(), false);
        setCategoryTooltip(pinnedMaster, categoryName(pinnedCategory), pinned.master.toggle.isOn());
        updatePinnedFold();
    }

    private CategoryGroup createGroup(MobEffectCategory category) {
        UIElement group = new UIElement();
        group.addClass("platform-effect-category-group");
        UIElement heading = new UIElement();
        heading.addClass("platform-effect-category-heading");
        WeaponPlatformToggleRow master = createMaster(categoryName(category), on -> setCategorySuppressed(category, on));
        master.addClass("platform-effect-category-master");

        Button fold = new Button();
        fold.addClass("platform-action");
        fold.addClass("platform-effect-category-fold");
        fold.setOnClick(event -> groups.get(category).toggleExpanded());
        UIElement divider = new UIElement();
        divider.addClass("platform-effect-category-divider");
        divider.setAllowHitTest(false);
        heading.addChildren(master, fold, divider);

        UIElement effects = new UIElement();
        effects.addClass("platform-effect-category-effects");
        for (String side : new String[] { "vertical", "top", "bottom" }) {
            UIElement edge = new UIElement();
            edge.addClass("platform-effect-group-" + side);
            edge.setAllowHitTest(false);
            effects.addChild(edge);
        }
        group.addChildren(heading, effects);
        return new CategoryGroup(category, group, heading, master, fold, effects);
    }

    private WeaponPlatformToggleRow createMaster(Component name, BooleanConsumer onChanged) {
        var master = new WeaponPlatformToggleRow(name, onChanged);
        master.toggle.setOnSwitchChanged(on -> setCategoryTooltip(master, name, on));
        setCategoryTooltip(master, name, false);
        return master;
    }

    private WeaponPlatformToggleRow createEffectRow(ResourceLocation id, MobEffect effect) {
        var row = new WeaponPlatformToggleRow(effect.getDisplayName(), on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.EFFECT_SUPPRESSION, id);
        });
        row.toggle.setOnSwitchChanged(on -> setEffectTooltip(row, on));
        row.addClass("platform-effect-row");
        setEffectTooltip(row, false);
        return row;
    }

    private static void setCategoryTooltip(WeaponPlatformToggleRow master, Component name, boolean suppressed) {
        master.style(style -> style.tooltips(Component.translatable(suppressed ? "gui.data_energistics.weapon_platform.effect_category.suppressed_tooltip" : "gui.data_energistics.weapon_platform.effect_category.not_all_suppressed_tooltip", name)));
    }

    private static void setEffectTooltip(WeaponPlatformToggleRow row, boolean suppressed) {
        row.style(style -> style.tooltips(Component.translatable(suppressed ? "gui.data_energistics.weapon_platform.effect.suppressed_tooltip" : "gui.data_energistics.weapon_platform.effect.allowed_tooltip")));
    }

    private void setCategorySuppressed(MobEffectCategory category, boolean suppressed) {
        if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.EFFECT_CATEGORY, category.ordinal(), suppressed);
    }

    private void updatePinnedHeading() {
        if (restoreScrollPending && viewPort.getContentHeight() > 0) {
            float range = Math.max(0, getContainerHeight() - viewPort.getContentHeight());
            verticalScroller.setNormalizedValue(range == 0 ? 0 : Math.clamp(pendingScrollOffset / range, 0, 1));
            restoreScrollPending = false;
        }
        if (viewPort.getSizeHeight() <= 0) return;
        float visibleTop = viewPort.getPositionY();
        MobEffectCategory visibleCategory = pinnedCategory;
        for (MobEffectCategory category : MobEffectCategory.values()) {
            CategoryGroup group = groups.get(category);
            if (group.heading.getPositionY() <= visibleTop) visibleCategory = category;
        }
        if (visibleCategory != pinnedCategory) {
            pinnedCategory = visibleCategory;
            pinnedMaster.label.setValue(categoryName(pinnedCategory));
            pinnedMaster.setActive(groups.get(pinnedCategory).master.isActive());
            setCategoryTooltip(pinnedMaster, categoryName(pinnedCategory), groups.get(pinnedCategory).master.toggle.isOn());
            updatePinnedFold();
        }
        CategoryGroup group = groups.get(pinnedCategory);
        boolean pinMaster = group.expanded && group.heading.getPositionY() + group.heading.getSizeHeight() <= viewPort.getPositionY();
        if (pinnedHeading.isDisplayed() != pinMaster) {
            setPinned(pinMaster);
        }
        boolean suppressed = group.master.toggle.isOn();
        if (pinnedMaster.toggle.isOn() != suppressed) pinnedMaster.toggle.setOn(suppressed, false);
    }

    private void setPinned(boolean pinned) {
        float range = Math.max(0, getContainerHeight() - viewPort.getContentHeight());
        pendingScrollOffset = verticalScroller.getNormalizedValue() * range;
        restoreScrollPending = true;
        if (pinned) addClass("platform-effect-sticky-active");
        else removeClass("platform-effect-sticky-active");
        pinnedHeading.setDisplay(pinned);
    }

    private void updatePinnedFold() {
        boolean expanded = groups.get(pinnedCategory).expanded;
        pinnedFold.setText(Component.literal(expanded ? "-" : "+"));
        pinnedFold.style(style -> style.tooltips(Component.translatable(expanded ? "gui.data_energistics.weapon_platform.effect_category.collapse" : "gui.data_energistics.weapon_platform.effect_category.expand")));
    }

    private static Component categoryName(MobEffectCategory category) {
        return Component.translatable("gui.data_energistics.weapon_platform.effect_category." + category.name().toLowerCase(Locale.ROOT));
    }

    private record EffectEntry(ResourceLocation id, WeaponPlatformToggleRow row) {}

    private final class CategoryGroup {

        private final MobEffectCategory category;
        private final UIElement element;
        private final UIElement heading;
        private final WeaponPlatformToggleRow master;
        private final Button fold;
        private final UIElement effects;
        private final ObjectList<EffectEntry> rows = new ObjectArrayList<>();
        private boolean expanded;

        private CategoryGroup(MobEffectCategory category, UIElement element, UIElement heading, WeaponPlatformToggleRow master, Button fold, UIElement effects) {
            this.category = category;
            this.element = element;
            this.heading = heading;
            this.master = master;
            this.fold = fold;
            this.effects = effects;
        }

        private void toggleExpanded() {
            setExpanded(!expanded);
            updatePinnedHeading();
            updatePinnedFold();
        }

        private void setExpanded(boolean expanded) {
            this.expanded = expanded;
            effects.setDisplay(expanded);
            fold.setText(Component.literal(expanded ? "-" : "+"));
            fold.style(style -> style.tooltips(Component.translatable(expanded ? "gui.data_energistics.weapon_platform.effect_category.collapse" : "gui.data_energistics.weapon_platform.effect_category.expand")));
        }
    }
}
