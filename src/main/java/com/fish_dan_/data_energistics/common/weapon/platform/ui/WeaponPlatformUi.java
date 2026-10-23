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
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
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
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
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
    private final UIElement configurationHost = new UIElement();
    private final UIElement[] rows = new UIElement[8];
    private final Button[] selectors = new Button[8];
    private final GunHeatBar[] heat = new GunHeatBar[8];
    private final Button[] targets = new Button[5];
    private final Label network = new Label();
    private final Label thermal = new Label();
    private final Label description = new Label();
    private final Label research = new Label();
    private final Button enabled;
    private final Button supportSelf;
    private final Button supportTeam;
    private final Button protection;
    private final Button display;
    private WeaponConfigurationPanel configuration = new WeaponConfigurationPanel();
    private Item configuredItem = Items.AIR;
    private PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;
    private int selected;

    private WeaponPlatformUi(Player player, ItemStack platform) {
        this.player = player;
        place(canvas, 0, 0, 256, 207);
        canvas.style(style -> style.backgroundTexture(surface(SURFACE)));
        root.addChild(canvas);
        panel(canvas, 5, 20, 165, 100, SECTION);
        panel(canvas, 40, 27, 130, 93, SECTION);
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
        place(weapons, 9, 23, 31, 95);
        weaponRows.layout(style -> style.width(31).height(48));
        canvas.addChild(weapons);
        IItemHandlerModifiable inventory = player.level().isClientSide ? new ItemStackHandler(9) : new WeaponPlatformInventory(platform);
        for (int slot = 0; slot < rows.length; slot++) {
            int index = slot;
            UIElement row = rows[slot] = new UIElement();
            place(row, 0, slot * 24, 31, 23);
            row.style(style -> style.backgroundTexture(surface(SECTION)));
            GunHeatBar bar = heat[slot] = new GunHeatBar();
            place(bar, 1, 2, 6, 18);
            row.addChild(bar);
            ItemSlot item = new ItemSlot(new ItemHandlerCopySlot(inventory, slot, 0, 0));
            SharedBackpackStyles.standalone(item);
            place(item, 8, 2, 18, 18);
            row.addChild(item);
            Button select = selectors[slot] = new Button();
            select.setText(Component.literal("›"));
            select.text.textStyle(style -> style.fontSize(7).textColor(TEXT).textShadow(false));
            select.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY).hoverTexture(surface(SURFACE)));
            select.setOnClick(event -> { selected = index; apply(snapshot); });
            place(select, 26, 1, 5, 21);
            row.addChild(select);
            weaponRows.addChild(row);
        }
        place(configurationHost, 40, 30, 126, 82);
        configurationHost.addChild(configuration);
        canvas.addChild(configurationHost);
        label(canvas, thermal, Component.empty(), 44, 111, 122, 7, 6);

        ItemSlot chip = new ItemSlot(new ItemHandlerCopySlot(inventory, WeaponPlatformInventory.CHIP_SLOT, 0, 0));
        SharedBackpackStyles.standalone(chip);
        place(chip, 225, 13, 18, 18);
        chip.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.chip")));
        canvas.addChild(chip);
        ScrollerView settings = scrollPanel(information);
        place(settings, 175, 36, 65, 160);
        information.layout(style -> style.width(62));
        for (Label label : new Label[] {research, description}) {
            label.textStyle(style -> style.fontSize(7).textWrap(TextWrap.WRAP).adaptiveWidth(false).adaptiveHeight(true).textColor(TEXT).textShadow(false));
            label.layout(style -> style.width(62).marginBottom(5));
            information.addChild(label);
        }
        enabled = button("enabled", PlatformRpc.ENABLED, 0);
        supportSelf = button("support_self", PlatformRpc.SELF, 0);
        supportTeam = button("support_team", PlatformRpc.TEAM, 0);
        String[] categories = {"players", "hostile", "friendly", "neutral", "other"};
        for (int index = 0; index < targets.length; index++) targets[index] = button("targets." + categories[index], PlatformRpc.TARGETS, 1 << index);
        protection = button("protection", PlatformRpc.PROTECTION, 0);
        display = button("display_settings", -1, 0);
        display.setOnClick(event -> { if (player.level().isClientSide) PlatformClientState.openAppearance(); });
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
            selectors[slot].style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.empty")));
        }
        for (GunSnapshot gun : value.guns()) {
            if (gun.slot() == selected) active = gun;
            heat[gun.slot()].setHeat(gun.heat(), gun.overheated());
            selectors[gun.slot()].style(style -> style.tooltips(gun.item().getHoverName()));
        }
        for (int slot = 0; slot < rows.length; slot++) {
            int border = slot == selected ? EDGE : 0x00696d88;
            rows[slot].style(style -> style.overlayTexture(new ColorBorderTexture(-1, border)));
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
        enabled.setText(Component.translatable("gui.data_energistics.weapon_platform." + (active != null && active.settings().enabled() ? "disable" : "enable")));
        supportSelf.setDisplay(medical);
        supportTeam.setDisplay(medical);
        supportSelf.setText(Component.translatable("gui.data_energistics.weapon_platform.support_self").append(medical && active.settings().supportSelf() ? " ✓" : ""));
        supportTeam.setText(Component.translatable("gui.data_energistics.weapon_platform.support_team").append(medical && active.settings().supportTeam() ? " ✓" : ""));
        for (int index = 0; index < targets.length; index++) {
            int color = (value.targetMask() & 1 << index) != 0 ? TEXT : MUTED;
            targets[index].text.textStyle(style -> style.textColor(color));
        }
        display.setActive(value.hud());
        thermal.setValue(active == null ? Component.empty() : Component.translatable("gui.data_energistics.weapon_platform.heat", (int) active.heat()).append(" · ").append(Component.translatable("gui.data_energistics.weapon_platform.state." + active.state())));
        description.setValue(active == null ? Component.translatable("gui.data_energistics.weapon_platform.instructions") : active.item().getHoverName().copy().append("\n\n").append(Component.translatable("gui.data_energistics.weapon_platform.description." + GunForm.of(active.item()).id)));
        research.setValue(Component.translatable("gui.data_energistics.weapon_platform.research", value.capacity(), BiochipResearch.cooling(value.chip()), value.hud() ? "✓" : "—"));
        protection.setText(Component.translatable("gui.data_energistics.weapon_platform.protection").append(value.friendlyProtection() ? " ✓" : ""));
    }

    private Button button(String key, int operation, int argument) {
        Button button = new Button();
        Component text = Component.translatable("gui.data_energistics.weapon_platform." + key);
        button.setText(text);
        button.text.textStyle(style -> style.textColor(TEXT).textShadow(false).fontSize(7).textAlignHorizontal(Horizontal.CENTER));
        button.style(style -> style.tooltips(text));
        button.buttonStyle(style -> style.baseTexture(surface(SECTION)).hoverTexture(surface(SURFACE)));
        button.layout(style -> style.width(62).height(16).marginBottom(3));
        button.setOnClick(event -> { if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, selected, argument); });
        information.addChild(button);
        return button;
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
        label.textStyle(style -> style.fontSize(fontSize).textColor(TEXT).textWrap(TextWrap.WRAP).textShadow(false).adaptiveWidth(false).adaptiveHeight(false));
        place(label, x, y, width, height);
        parent.addChild(label);
    }
}
