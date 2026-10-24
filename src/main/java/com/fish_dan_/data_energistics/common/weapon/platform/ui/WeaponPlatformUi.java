package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.api.gui.style.DEStylesheets;
import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.ui.TacticalHudConfigurationPanel;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration.WeaponConfigurationBindings;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration.WeaponConfigurationPanel;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerCopySlot;
import net.neoforged.neoforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/** The reference layout is constructed from LDLib2 styles and scaled with all its native slots as one tree. */
public final class WeaponPlatformUi {

    private final Player player;
    private final UIElement root = new UIElement();
    private final UIElement canvas = new UIElement();
    private final UIElement weaponRows = new UIElement();
    private final WeaponPlatformScrollView weapons = new WeaponPlatformScrollView();
    private final WeaponPlatformScrollView configurationScroll = new WeaponPlatformScrollView();
    private final UIElement details = new UIElement();
    private final UIElement settingsDetails = new UIElement();
    private final UIElement connection = new UIElement();
    private final Button back = new Button();
    private final UIElement configurationHost = new UIElement();
    private final UIElement[] rows = new UIElement[8];
    private final Button[] summaries = new Button[8];
    private final Label[] names = new Label[8];
    private final Label[] forms = new Label[8];
    private final WeaponPlatformSwitch[] switches = new WeaponPlatformSwitch[8];
    private final GunHeatBar[] heat = new GunHeatBar[8];
    private final Label configurationTitle = new Label();
    private final Label categoryTitle = new Label();
    private final WeaponPlatformSwitch enabled = new WeaponPlatformSwitch();
    private final WeaponPlatformSwitch supportSelf = new WeaponPlatformSwitch();
    private final WeaponPlatformSwitch supportTeam = new WeaponPlatformSwitch();
    private final Label supportSelfName = new Label();
    private final Label supportTeamName = new Label();
    private final WeaponPlatformSettingsPanel settings;
    private WeaponConfigurationPanel configuration = new WeaponConfigurationPanel();
    private Item configuredItem = Items.AIR;
    private PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;
    private int selected;
    private boolean expanded;
    private @Nullable PlatformSettingCategory category;
    private @Nullable WeaponPlatformScrollView categoryScroll;

    private WeaponPlatformUi(Player player, ItemStack platform) {
        this.player = player;
        canvas.addClass("platform-window");
        root.addChild(canvas);
        UIElement surface = new UIElement();
        surface.addClass("platform-window-surface");
        surface.setAllowHitTest(false);
        canvas.addChild(surface);
        for (String side : new String[] { "top", "step", "raised", "right", "bottom", "left" }) {
            UIElement outline = new UIElement();
            outline.addClass("platform-assembly-edge");
            outline.addClass("platform-assembly-" + side);
            outline.setAllowHitTest(false);
            canvas.addChild(outline);
        }
        label(canvas, new Label(), Component.translatable("item.data_energistics.digital_weapon_platform"), "platform-window-title");
        UIElement weaponFrame = new UIElement();
        weaponFrame.addClass("platform-weapon-frame");
        weaponFrame.setAllowHitTest(false);
        for (String part : new String[] { "upper-surface", "lower-surface", "top", "right", "bottom", "left", "upper-shadow" }) {
            UIElement edge = new UIElement();
            edge.addClass("platform-weapon-frame-part");
            edge.addClass("platform-weapon-frame-" + part);
            edge.setAllowHitTest(false);
            weaponFrame.addChild(edge);
        }
        canvas.addChild(weaponFrame);
        UIElement configurationTab = new UIElement();
        configurationTab.addClass("platform-configuration-tab");
        label(configurationTab, configurationTitle, Component.translatable("gui.data_energistics.weapon_platform.weapon_configuration"), "platform-configuration-title");
        configurationTitle.setAllowHitTest(true);
        canvas.addChild(configurationTab);

        weapons.addClass("platform-weapons");
        weapons.addScrollViewChild(weaponRows);
        weapons.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (!expanded && category == null) return;
            WeaponPlatformScrollView scroll = expanded ? configurationScroll : categoryScroll;
            if (scroll != null) scroll.verticalScroller.scrollByWheel(event.deltaY);
            event.stopImmediatePropagation();
        }, true);
        weaponRows.addClass("platform-weapon-rows");
        canvas.addChild(weapons);
        UIElement weaponFrameCover = createWeaponFrameCover();
        canvas.addChild(weaponFrameCover);
        IItemHandlerModifiable inventory = player.level().isClientSide ? new ItemStackHandler(9) : new WeaponPlatformInventory(platform);
        for (int slot = 0; slot < rows.length; slot++) {
            int index = slot;
            UIElement row = rows[slot] = new UIElement();
            row.addClass("platform-weapon-row");
            row.setAllowHitTest(false);
            Button mount = new Button();
            mount.noText();
            mount.addClass("platform-mount");
            mount.setOnClick(event -> {
                selected = index;
                category = null;
                expanded = true;
                apply(snapshot);
            });
            UIElement mountOutline = new UIElement();
            mountOutline.addClass("platform-mount-outline");
            mountOutline.setAllowHitTest(false);
            mount.addChild(mountOutline);
            row.addChild(mount);
            GunHeatBar bar = heat[slot] = new GunHeatBar();
            bar.addClass("platform-mount-heat");
            row.addChild(bar);
            ItemSlot item = new ItemSlot(new ItemHandlerCopySlot(inventory, slot, 0, 0));
            SharedBackpackStyles.standalone(item);
            item.addClass("platform-mount-slot");
            row.addChild(item);
            Button summary = summaries[slot] = new Button();
            summary.noText();
            summary.addClass("platform-summary");
            for (String side : new String[] { "top", "right", "bottom" }) {
                UIElement edge = new UIElement();
                edge.addClass("platform-summary-edge");
                edge.addClass("platform-summary-" + side);
                edge.setAllowHitTest(false);
                summary.addChild(edge);
            }
            summary.setOnClick(event -> {
                selected = index;
                category = null;
                expanded = true;
                apply(snapshot);
            });
            label(summary, names[slot] = new Label(), Component.empty(), "platform-summary-name");
            label(summary, forms[slot] = new Label(), Component.empty(), "platform-summary-form");
            WeaponPlatformSwitch toggle = switches[slot] = new WeaponPlatformSwitch();
            toggle.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.enabled")));
            toggle.setOnSwitchChanged(on -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ENABLED, index, 0);
            });
            toggle.addClass("platform-gun-toggle");
            summary.addChild(toggle);
            row.addChild(summary);
            weaponRows.addChild(row);
        }
        weaponRows.addEventListener(UIEvents.LAYOUT_CHANGED, event -> updateConnection());
        weapons.viewContainer.addEventListener(UIEvents.LAYOUT_CHANGED, event -> updateConnection());
        details.addClass("platform-details");
        settingsDetails.addClass("platform-details");
        for (UIElement panel : new UIElement[] { details, settingsDetails }) {
            UIElement topConnection = new UIElement();
            topConnection.addClass("platform-configuration-top-connection");
            topConnection.setAllowHitTest(false);
            for (String side : new String[] { "left", "top", "right" }) {
                UIElement edge = new UIElement();
                edge.addClass("platform-connection-" + side);
                edge.setAllowHitTest(false);
                topConnection.addChild(edge);
            }
            panel.addChild(topConnection);
            panel.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 1) return;
                for (UIElement element = event.target; element != null && element != panel; element = element.getParent()) {
                    if (element instanceof Button || element instanceof Switch || element instanceof TextField) return;
                }
                expanded = false;
                category = null;
                apply(snapshot);
                event.stopPropagation();
            });
        }
        enabled.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.enabled")));
        enabled.setOnSwitchChanged(on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ENABLED, selected, 0);
        });
        enabled.addClass("platform-gun-toggle");
        UIElement weaponConfiguration = new UIElement();
        weaponConfiguration.addClass("platform-weapon-details-content");
        weaponConfiguration.addChild(enabled);
        configurationScroll.addClass("platform-weapon-configuration-scroll");
        configurationScroll.addScrollViewChild(weaponConfiguration);
        details.addChild(configurationScroll);
        configurationHost.addClass("platform-configuration-host");
        configurationHost.setAllowHitTest(false);
        configurationHost.addChild(configuration);
        weaponConfiguration.addChild(configurationHost);
        label(weaponConfiguration, supportSelfName, Component.translatable("gui.data_energistics.weapon_platform.support_self_short"), "platform-support-self-name");
        label(weaponConfiguration, supportTeamName, Component.translatable("gui.data_energistics.weapon_platform.support_team_short"), "platform-support-team-name");
        supportSelf.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.support_self")));
        supportTeam.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.support_team")));
        supportSelf.setOnSwitchChanged(on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.SELF, selected, 0);
        });
        supportTeam.setOnSwitchChanged(on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.TEAM, selected, 0);
        });
        supportSelf.addClass("platform-support-self-toggle");
        supportTeam.addClass("platform-support-team-toggle");
        weaponConfiguration.addChildren(supportSelf, supportTeam);
        canvas.addChild(details);

        connection.addClass("platform-configuration-connection");
        connection.setAllowHitTest(false);
        for (String side : new String[] { "top", "bottom" }) {
            UIElement edge = new UIElement();
            edge.addClass("platform-connection-" + side);
            edge.setAllowHitTest(false);
            connection.addChild(edge);
        }
        canvas.addChild(connection);
        label(settingsDetails, categoryTitle, Component.empty(), "platform-category-title");
        back.setText(Component.translatable("gui.back"));
        back.addClass("platform-action");
        back.addClass("platform-category-back");
        back.setOnClick(event -> {
            category = null;
            apply(snapshot);
        });
        settingsDetails.addChild(back);
        canvas.addChild(settingsDetails);
        settings = new WeaponPlatformSettingsPanel(player, inventory, selectedCategory -> {
            expanded = false;
            if (category == selectedCategory) category = null;
            else {
                category = selectedCategory;
                if (categoryScroll != null) settingsDetails.removeChild(categoryScroll);
                categoryScroll = category == PlatformSettingCategory.HUD ? new TacticalHudConfigurationPanel() : new PlatformCombatSettingsPanel(player, category);
                settingsDetails.addChild(categoryScroll);
            }
            apply(snapshot);
        });
        canvas.addChild(settings);

        InventorySlots backpack = new InventorySlots();
        SharedBackpackStyles.inventory(backpack);
        backpack.addClass("platform-backpack");
        canvas.addChild(backpack);
    }

    private UIElement createWeaponFrameCover() {
        UIElement weaponFrameCover = new UIElement();
        weaponFrameCover.addClass("platform-weapon-frame");
        weaponFrameCover.addClass("platform-weapon-frame-cover");
        weaponFrameCover.setAllowHitTest(false);
        for (String part : new String[] { "cover-surface", "step", "lowered", "lower-shadow" }) {
            UIElement edge = new UIElement();
            edge.addClass("platform-weapon-frame-part");
            edge.addClass("platform-weapon-frame-" + part);
            edge.setAllowHitTest(false);
            weaponFrameCover.addChild(edge);
        }
        return weaponFrameCover;
    }

    public static ModularUI create(Player player, ItemStack platform, Supplier<PlatformSnapshot> provider) {
        WeaponPlatformUi view = new WeaponPlatformUi(player, platform);
        boolean client = player.level().isClientSide;
        var sync = new SyncValue<>("weapon_platform", PlatformSnapshot.class, client ? PlatformSnapshot.EMPTY : provider.get());
        sync.setToSync(!client);
        sync.setAcceptSync(client);
        sync.addListener(view::apply);
        if (!client) sync.setValueProvider(provider);
        ModularUI ui = ModularUI.of(UI.of(view.root, ObjectList.of(DEStylesheets.SHARED_BACKPACK.get(), DEStylesheets.WEAPON_PLATFORM.get()), view::resize), player);
        ui.syncManager.registerSyncValue(sync);
        view.apply(sync.getValue());
        return ui;
    }

    private Size resize(Size viewport) {
        float scale = 0.84f * Math.clamp(Math.min((viewport.getWidth() - 16) / 256f, (viewport.getHeight() - 16) / 207f), 0.25f, 2);
        int width = Math.round(256 * scale), height = Math.round(207 * scale);
        root.layout(style -> style.width(width).height(height));
        canvas.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
        return Size.of(width, height);
    }

    private void apply(PlatformSnapshot value) {
        snapshot = value;
        if (selected >= value.capacity()) selected = 0;
        GunSnapshot active = null;
        for (int slot = 0; slot < rows.length; slot++) {
            rows[slot].setDisplay(slot < value.capacity());
            heat[slot].setHeat(0, false);
            summaries[slot].setActive(false);
            names[slot].setValue(Component.translatable("gui.data_energistics.weapon_platform.empty"));
            summaries[slot].removeClass("platform-occupied");
            forms[slot].setValue(Component.empty());
            switches[slot].setActive(false);
            switches[slot].setDisplay(false);
            switches[slot].setOn(false, false);
        }
        for (GunSnapshot gun : value.guns()) {
            if (gun.slot() == selected) active = gun;
            heat[gun.slot()].setHeat(gun.heat(), gun.overheated());
            summaries[gun.slot()].setActive(true);
            names[gun.slot()].setValue(gun.item().getHoverName());
            summaries[gun.slot()].addClass("platform-occupied");
            forms[gun.slot()].setValue(Component.translatable("gun_form.data_energistics." + GunForm.of(gun.item()).id));
            switches[gun.slot()].setActive(true);
            switches[gun.slot()].setDisplay(true);
            switches[gun.slot()].setOn(gun.settings().enabled(), false);
        }
        if (active == null) expanded = false;
        if (!value.hud() && category == PlatformSettingCategory.HUD) category = null;
        details.setDisplay(expanded);
        settingsDetails.setDisplay(category != null);
        if (expanded || category != null) weapons.addClass("platform-configuring");
        else weapons.removeClass("platform-configuring");
        for (int slot = 0; slot < rows.length; slot++) {
            summaries[slot].setDisplay(!expanded && category == null);
        }
        Item item = active == null ? Items.AIR : active.item().getItem();
        if (configuredItem != item) {
            configuredItem = item;
            configurationHost.clearAllChildren();
            configuration = WeaponConfigurationBindings.create(item, ammunition -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.AMMUNITION, selected, ammunition);
            });
            configurationHost.addChild(configuration);
        }
        configuration.bind(active);
        boolean medical = active != null && GunForm.of(active.item()) == GunForm.MEDICAL;
        enabled.setActive(active != null);
        enabled.setOn(active != null && active.settings().enabled(), false);
        Component[] titleTooltips = active == null ? new Component[0] : new Component[] { active.item().getHoverName(),
                Component.translatable("gun_form.data_energistics." + GunForm.of(active.item()).id) };
        configurationTitle.style(style -> style.tooltips(category == null ? titleTooltips : new Component[0]));
        categoryTitle.setValue(category == null ? Component.empty() : Component.translatable("gui.data_energistics.weapon_platform.category." + category.name().toLowerCase(Locale.ROOT)));
        supportSelfName.setDisplay(medical);
        supportTeamName.setDisplay(medical);
        supportSelf.setDisplay(medical);
        supportTeam.setDisplay(medical);
        supportSelf.setOn(medical && active.settings().supportSelf(), false);
        supportTeam.setOn(medical && active.settings().supportTeam(), false);
        settings.bind(value, category);
        if (categoryScroll instanceof PlatformCombatSettingsPanel combat) combat.bind(value);
        updateConnection();
    }

    private void updateConnection() {
        if (!expanded) {
            connection.setDisplay(false);
            return;
        }
        float rowTop = rows[selected].getPositionY() - canvas.getPositionY();
        float viewportTop = weapons.viewPort.getContentY() - canvas.getPositionY();
        float detailsTop = details.getPositionY() - canvas.getPositionY();
        float top = Math.max(viewportTop, rowTop);
        float bottom = Math.min(Math.min(viewportTop + weapons.viewPort.getContentHeight(), detailsTop + details.getSizeHeight()),
                rowTop + rows[selected].getSizeHeight());
        boolean visible = bottom > top;
        connection.setDisplay(visible);
        if (visible) connection.layout(style -> style.top(top).height(bottom - top));
    }

    private static void label(UIElement parent, Label label, Component text, String name) {
        label.setValue(text);
        label.setAllowHitTest(false);
        label.addClass("platform-label");
        label.addClass(name);
        parent.addChild(label);
    }
}
