package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumMap;
import java.util.Locale;

/** Registry-backed filter for every vanilla and modded status effect, grouped by effect category. */
final class PlatformEffectSettingsPanel extends UIElement {

    private final Player player;
    private final UIElement effects = new UIElement();
    private final ObjectList<EffectEntry> rows = new ObjectArrayList<>();
    private final EnumMap<MobEffectCategory, Button> categoryTabs = new EnumMap<>(MobEffectCategory.class);
    private MobEffectCategory selected = MobEffectCategory.BENEFICIAL;

    PlatformEffectSettingsPanel(Player player) {
        this.player = player;
        addClass("platform-effect-settings");
        UIElement tabs = new UIElement();
        tabs.addClass("platform-effect-tabs");
        effects.addClass("platform-effect-list");
        for (MobEffectCategory category : MobEffectCategory.values()) {
            Button tab = new Button();
            tab.setText(Component.translatable("gui.data_energistics.weapon_platform.effect_category." + category.name().toLowerCase(Locale.ROOT)));
            tab.addClasses("platform-action", "platform-effect-tab");
            tab.setOnClick(event -> select(category));
            categoryTabs.put(category, tab);
            tabs.addChild(tab);
        }
        for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            MobEffect effect = entry.getValue();
            rows.add(new EffectEntry(id, effect, createRow(id, effect)));
        }
        rows.sort((first, second) -> first.effect().getDisplayName().getString().compareToIgnoreCase(second.effect().getDisplayName().getString()));
        addChildren(tabs, effects);
        select(MobEffectCategory.BENEFICIAL);
    }

    void bind(PlatformSnapshot snapshot) {
        boolean researched = ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformChipUpgrades.EFFECT_SUPPRESSION);
        for (var entry : rows) {
            entry.row().setActive(researched);
            entry.row().toggle.setOn(snapshot.effects().suppresses(entry.id()), false);
        }
    }

    private void select(MobEffectCategory category) {
        categoryTabs.get(selected).removeClass("selected");
        selected = category;
        categoryTabs.get(selected).addClass("selected");
        effects.clearAllChildren();
        for (EffectEntry row : rows) if (row.effect().getCategory() == category) effects.addChild(row.row());
    }

    private record EffectEntry(ResourceLocation id, MobEffect effect, WeaponPlatformToggleRow row) {}

    private WeaponPlatformToggleRow createRow(ResourceLocation id, MobEffect effect) {
        var row = new WeaponPlatformToggleRow(effect.getDisplayName(), on -> {
                if (PlatformEffectSettingsPanel.this.player.level().isClientSide)
                    RPCPacketDistributor.rpcToServer(PlatformRpc.EFFECT_SUPPRESSION, id);
            });
        row.addClass("platform-effect-row");
        row.style(style -> style.tooltips(Component.literal(id.toString())));
        return row;
    }
}
