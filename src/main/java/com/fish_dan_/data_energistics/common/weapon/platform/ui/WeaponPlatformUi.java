package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration.WeaponConfigurationBindings;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration.WeaponConfigurationPanel;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
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

import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.function.Supplier;

import static com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle.*;

/** The reference layout is constructed from LDLib2 styles and scaled with all its native slots as one tree. */
public final class WeaponPlatformUi {

    private final Player player;
    private final UIElement root = new UIElement();
    private final UIElement canvas = new UIElement();
    private final UIElement weaponRows = new UIElement();
    private final UIElement information = new UIElement();
    private final UIElement details = new UIElement();
    private final UIElement configurationHost = new UIElement();
    private final UIElement[] rows = new UIElement[8];
    private final UIElement[] connections = new UIElement[8];
    private final Button[] selectors = new Button[8];
    private final Button[] summaries = new Button[8];
    private final Label[] names = new Label[8];
    private final Label[] forms = new Label[8];
    private final WeaponPlatformSwitch[] switches = new WeaponPlatformSwitch[8];
    private final GunHeatBar[] heat = new GunHeatBar[8];
    private final WeaponPlatformSwitch[] targets = new WeaponPlatformSwitch[5];
    private final Label network = new Label();
    private final Label selectedName = new Label();
    private final Label selectedForm = new Label();
    private final Label thermal = new Label();
    private final Label research = new Label();
    private final WeaponPlatformSwitch enabled = new WeaponPlatformSwitch();
    private final WeaponPlatformSwitch supportSelf = new WeaponPlatformSwitch();
    private final WeaponPlatformSwitch supportTeam = new WeaponPlatformSwitch();
    private final Label supportSelfName = new Label();
    private final Label supportTeamName = new Label();
    private final WeaponPlatformSwitch protection;
    private final Button display;
    private WeaponConfigurationPanel configuration = new WeaponConfigurationPanel();
    private Item configuredItem = Items.AIR;
    private PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;
    private int selected;
    private boolean expanded;

    private WeaponPlatformUi(Player player, ItemStack platform) {
        this.player = player;
        place(canvas, 0, 0, 256, 207);
        canvas.style(style -> style.backgroundTexture(surface(SURFACE)));
        root.addChild(canvas);
        panel(canvas, 5, 20, 165, 100, SECTION);
        panel(canvas, 5, 120, 165, 81, SECTION);
        panel(canvas, 171, 31, 73, 170, SECTION);
        panel(canvas, 94, 11, 77, 18, SURFACE);
        panel(canvas, 171, 11, 73, 22, SURFACE);
        label(canvas, new Label(), Component.translatable("item.data_energistics.digital_weapon_platform"), 8, 3, 84, 10, 7);
        label(canvas, network, Component.empty(), 8, 13, 80, 7, 6);
        Label configurationTitle = new Label();
        label(canvas, configurationTitle, Component.translatable("gui.data_energistics.weapon_platform.weapon_configuration"), 97, 14, 70, 13, 8);
        configurationTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        label(canvas, new Label(), Component.translatable("gui.data_energistics.weapon_platform.platform_configuration"), 174, 14, 48, 16, 7);

        ScrollerView weapons = scrollPanel(weaponRows);
        place(weapons, 9, 23, 157, 95);
        weaponRows.layout(style -> style.width(157).height(48));
        canvas.addChild(weapons);
        IItemHandlerModifiable inventory = player.level().isClientSide ? new ItemStackHandler(9) : new WeaponPlatformInventory(platform);
        for (int slot = 0; slot < rows.length; slot++) {
            int index = slot;
            UIElement row = rows[slot] = new UIElement();
            place(row, 0, slot * 24, 157, 23);
            UIElement mount = new UIElement();
            place(mount, 0, 1, 29, 22);
            mount.style(style -> style.backgroundTexture(surface(SECTION)));
            mount.setAllowHitTest(false);
            row.addChild(mount);
            GunHeatBar bar = heat[slot] = new GunHeatBar();
            place(bar, 2, 3, 6, 18);
            row.addChild(bar);
            ItemSlot item = new ItemSlot(new ItemHandlerCopySlot(inventory, slot, 0, 0));
            SharedBackpackStyles.standalone(item);
            item.style(style -> style.backgroundTexture(surface(SECTION)));
            place(item, 9, 3, 18, 18);
            row.addChild(item);
            UIElement connection = connections[slot] = new UIElement();
            place(connection, 28, 1, 7, 22);
            connection.style(style -> style.backgroundTexture(new ColorRectTexture(SECTION)).zIndex(3));
            connection.setAllowHitTest(false);
            for (int y : new int[] { 0, 2, 19, 21 }) {
                UIElement edge = new UIElement();
                place(edge, 0, y, 7, 1);
                edge.style(style -> style.backgroundTexture(new ColorRectTexture(EDGE)));
                edge.setAllowHitTest(false);
                connection.addChild(edge);
            }
            row.addChild(connection);
            Button select = selectors[slot] = new Button();
            select.setText(Component.literal("›"));
            select.style(style -> style.zIndex(4));
            select.text.textStyle(style -> style.fontSize(7).textColor(TEXT).textShadow(false));
            select.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY).hoverTexture(surface(SURFACE)).pressedTexture(surface(SURFACE)));
            select.setOnClick(event -> {
                selected = index;
                expanded = true;
                apply(snapshot);
            });
            place(select, 29, 1, 3, 22);
            row.addChild(select);
            Button summary = summaries[slot] = new Button();
            summary.noText();
            summary.buttonStyle(style -> style.baseTexture(doubleBorder(SECTION)).hoverTexture(doubleBorder(SURFACE)).pressedTexture(doubleBorder(SURFACE)));
            summary.setOnClick(event -> {
                selected = index;
                expanded = true;
                apply(snapshot);
            });
            place(summary, 32, 1, 125, 22);
            label(summary, names[slot] = new Label(), Component.empty(), 4, 4, 101, 8, 6);
            label(summary, forms[slot] = new Label(), Component.empty(), 4, 13, 101, 6, 5);
            WeaponPlatformSwitch toggle = switches[slot] = new WeaponPlatformSwitch();
            toggle.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.enabled")));
            toggle.setOnSwitchChanged(on -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ENABLED, index, 0);
            });
            place(toggle, 109, 4, 13, 7);
            summary.addChild(toggle);
            row.addChild(summary);
            weaponRows.addChild(row);
        }
        place(details, 40, 23, 126, 95);
        details.style(style -> style.backgroundTexture(doubleBorder(SECTION)).zIndex(2));
        details.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 1) return;
            for (UIElement element = event.target; element != null && element != details; element = element.getParent()) {
                if (element instanceof Button || element instanceof Switch) return;
            }
            expanded = false;
            apply(snapshot);
            event.stopPropagation();
        });
        label(details, selectedName, Component.empty(), 4, 4, 101, 7, 6);
        label(details, selectedForm, Component.empty(), 4, 12, 101, 5, 5);
        enabled.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.enabled")));
        enabled.setOnSwitchChanged(on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ENABLED, selected, 0);
        });
        place(enabled, 109, 4, 13, 7);
        details.addChild(enabled);
        place(configurationHost, 0, 12, 126, 82);
        configurationHost.addChild(configuration);
        details.addChild(configurationHost);
        label(details, supportSelfName, Component.translatable("gui.data_energistics.weapon_platform.support_self_short"), 2, 51, 23, 7, 5);
        label(details, supportTeamName, Component.translatable("gui.data_energistics.weapon_platform.support_team_short"), 102, 51, 23, 7, 5);
        supportSelf.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.support_self")));
        supportTeam.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.support_team")));
        supportSelf.setOnSwitchChanged(on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.SELF, selected, 0);
        });
        supportTeam.setOnSwitchChanged(on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.TEAM, selected, 0);
        });
        place(supportSelf, 4, 61, 13, 7);
        place(supportTeam, 106, 61, 13, 7);
        details.addChildren(supportSelf, supportTeam);
        label(details, thermal, Component.empty(), 4, 86, 118, 7, 5);
        canvas.addChild(details);

        ItemSlot chip = new ItemSlot(new ItemHandlerCopySlot(inventory, WeaponPlatformInventory.CHIP_SLOT, 0, 0));
        SharedBackpackStyles.standalone(chip);
        place(chip, 225, 13, 18, 18);
        chip.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.chip")));
        canvas.addChild(chip);
        ScrollerView settings = scrollPanel(information);
        place(settings, 175, 36, 65, 160);
        information.layout(style -> style.width(62));
        research.setAllowHitTest(false);
        research.textStyle(style -> style.fontSize(6).textWrap(TextWrap.WRAP).adaptiveWidth(false).adaptiveHeight(true).textColor(TEXT).textShadow(false));
        research.layout(style -> style.width(62).marginBottom(6));
        information.addChild(research);
        String[] categories = { "players", "hostile", "friendly", "neutral", "other" };
        for (int index = 0; index < targets.length; index++) targets[index] = setting("targets." + categories[index], PlatformRpc.TARGETS, 1 << index);
        protection = setting("protection", PlatformRpc.PROTECTION, 0);
        display = new Button();
        display.setText(Component.translatable("gui.data_energistics.weapon_platform.display_settings"));
        display.text.textStyle(style -> style.textColor(TEXT).textShadow(false).fontSize(6).textAlignHorizontal(Horizontal.CENTER));
        display.buttonStyle(style -> style.baseTexture(surface(SECTION)).hoverTexture(surface(SURFACE)).pressedTexture(surface(SURFACE)));
        display.layout(style -> style.width(62).height(16).marginTop(4));
        display.setOnClick(event -> { if (player.level().isClientSide) PlatformClientState.openAppearance(); });
        information.addChild(display);
        canvas.addChild(settings);

        InventorySlots backpack = new InventorySlots();
        SharedBackpackStyles.inventory(backpack);
        place(backpack, 8, 123, 162, 76);
        canvas.addChild(backpack);
    }

    public static ModularUI create(Player player, ItemStack platform, Supplier<PlatformSnapshot> provider) {
        WeaponPlatformUi view = new WeaponPlatformUi(player, platform);
        boolean client = player.level().isClientSide;
        var sync = new SyncValue<>("weapon_platform", PlatformSnapshot.class, client ? PlatformSnapshot.EMPTY : provider.get());
        sync.setToSync(!client);
        sync.setAcceptSync(client);
        sync.addListener(view::apply);
        if (!client) sync.setValueProvider(provider);
        ModularUI ui = ModularUI.of(UI.of(view.root, ObjectLists.singleton(SharedBackpackStyles.stylesheet()), view::resize), player);
        ui.syncManager.registerSyncValue(sync);
        view.apply(sync.getValue());
        return ui;
    }

    private Size resize(Size viewport) {
        float scale = Math.clamp(Math.min((viewport.getWidth() - 16) / 256f, (viewport.getHeight() - 16) / 207f), 0.25f, 2);
        int width = Math.round(256 * scale), height = Math.round(207 * scale);
        root.layout(style -> style.width(width).height(height));
        canvas.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
        return Size.of(width, height);
    }

    private void apply(PlatformSnapshot value) {
        snapshot = value;
        if (selected >= value.capacity()) selected = 0;
        weaponRows.layout(style -> style.height(value.capacity() * 24));
        network.setValue(Component.translatable("gui.data_energistics.weapon_platform.network_short." + (value.failed() ? "error" : value.connected() ? "connected" : "disconnected")));
        GunSnapshot active = null;
        for (int slot = 0; slot < rows.length; slot++) {
            rows[slot].setDisplay(slot < value.capacity());
            heat[slot].setHeat(0, false);
            selectors[slot].setActive(false);
            selectors[slot].style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.empty")));
            summaries[slot].setActive(false);
            names[slot].setValue(Component.translatable("gui.data_energistics.weapon_platform.empty"));
            forms[slot].setValue(Component.empty());
            switches[slot].setActive(false);
            switches[slot].setDisplay(false);
            switches[slot].setOn(false, false);
        }
        for (GunSnapshot gun : value.guns()) {
            if (gun.slot() == selected) active = gun;
            heat[gun.slot()].setHeat(gun.heat(), gun.overheated());
            selectors[gun.slot()].setActive(true);
            selectors[gun.slot()].style(style -> style.tooltips(gun.item().getHoverName()));
            summaries[gun.slot()].setActive(true);
            names[gun.slot()].setValue(gun.item().getHoverName());
            forms[gun.slot()].setValue(Component.translatable("gun_form.data_energistics." + GunForm.of(gun.item()).id));
            switches[gun.slot()].setActive(true);
            switches[gun.slot()].setDisplay(true);
            switches[gun.slot()].setOn(gun.settings().enabled(), false);
        }
        if (active == null) expanded = false;
        details.setDisplay(expanded);
        for (int slot = 0; slot < rows.length; slot++) {
            summaries[slot].setDisplay(!expanded);
            connections[slot].setDisplay(expanded && slot == selected);
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
        selectedName.setValue(active == null ? Component.empty() : active.item().getHoverName());
        selectedForm.setValue(active == null ? Component.empty() : Component.translatable("gun_form.data_energistics." + GunForm.of(active.item()).id));
        supportSelfName.setDisplay(medical);
        supportTeamName.setDisplay(medical);
        supportSelf.setDisplay(medical);
        supportTeam.setDisplay(medical);
        supportSelf.setOn(medical && active.settings().supportSelf(), false);
        supportTeam.setOn(medical && active.settings().supportTeam(), false);
        for (int index = 0; index < targets.length; index++) {
            targets[index].setOn((value.targetMask() & 1 << index) != 0, false);
        }
        display.setActive(value.hud());
        thermal.setValue(active == null ? Component.empty() : Component.translatable("gui.data_energistics.weapon_platform.heat", (int) active.heat()).append(" · ").append(Component.translatable("gui.data_energistics.weapon_platform.state." + active.state())));
        research.setValue(Component.translatable("gui.data_energistics.weapon_platform.research_compact", value.capacity(), BiochipResearch.cooling(value.chip()), value.hud() ? "✓" : "—"));
        protection.setOn(value.friendlyProtection(), false);
    }

    private WeaponPlatformSwitch setting(String key, int operation, int argument) {
        UIElement row = new UIElement();
        row.layout(style -> style.width(62).height(13));
        Component text = Component.translatable("gui.data_energistics.weapon_platform." + key);
        label(row, new Label(), text, 0, 2, 46, 9, 6);
        WeaponPlatformSwitch toggle = new WeaponPlatformSwitch();
        place(toggle, 49, 3, 13, 7);
        toggle.style(style -> style.tooltips(text));
        toggle.setOnSwitchChanged(on -> { if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, selected, argument); });
        row.addChild(toggle);
        information.addChild(row);
        return toggle;
    }

    private static ScrollerView scrollPanel(UIElement content) {
        ScrollerView panel = new ScrollerView();
        panel.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.NEVER).adaptiveHeight(false).adaptiveWidth(false));
        panel.viewPort.layout(style -> style.paddingAll(0));
        panel.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        panel.addScrollViewChild(content);
        return panel;
    }

    private static void label(UIElement parent, Label label, Component text, int x, int y, int width, int height, int fontSize) {
        label.setValue(text);
        label.setAllowHitTest(false);
        label.textStyle(style -> style.fontSize(fontSize).textColor(TEXT).textWrap(TextWrap.WRAP).textShadow(false).adaptiveWidth(false).adaptiveHeight(false));
        place(label, x, y, width, height);
        parent.addChild(label);
    }
}
