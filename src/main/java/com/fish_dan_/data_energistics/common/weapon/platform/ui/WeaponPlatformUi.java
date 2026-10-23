package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerCopySlot;
import net.neoforged.neoforge.items.ItemStackHandler;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Locale;
import java.util.function.Supplier;

/** Both-side LDLib2 slot tree, with a large scrolling explanation panel and the shared native backpack. */
public final class WeaponPlatformUi {

    private static final int TEXT = 0xffd9e9eb;
    private static final int ACCENT = 0xff7be5d0;
    private final Player player;
    private final UIElement root = new UIElement();
    private final UIElement weaponRows = new UIElement();
    private final UIElement configuration = new UIElement();
    private final UIElement information = new UIElement();
    private final ScrollerView weaponsPanel = scrollPanel(weaponRows);
    private final ScrollerView configurationPanel = scrollPanel(configuration);
    private final ScrollerView informationPanel = scrollPanel(information);
    private final UIElement[] rows = new UIElement[8];
    private final Button[] selectors = new Button[8];
    private final TextElement[] rowLabels = new TextElement[8];
    private final Button[] ammunition = new Button[4];
    private final Button[] targets = new Button[5];
    private final Label title = new Label();
    private final Label network = new Label();
    private final Label name = new Label();
    private final Label thermal = new Label();
    private final Label description = new Label();
    private final Label research = new Label();
    private final ItemStackTexture model = new ItemStackTexture();
    private final Button enabled;
    private final Button supportSelf;
    private final Button supportTeam;
    private final Button protection;
    private final UIElement preview = new UIElement();
    private final ItemSlot chip;
    private final Label chipLabel = new Label();
    private final Label backpackLabel = new Label();
    private final InventorySlots backpack = new InventorySlots();
    private PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;
    private int selected;

    private WeaponPlatformUi(Player player, ItemStack platform) {
        this.player = player;
        root.style(style -> style.backgroundTexture(new GuiTextureGroup(new ColorRectTexture(0xff17242c), new ColorBorderTexture(-1, 0xff527b86))));
        label(root, title, Component.translatable("item.data_energistics.digital_weapon_platform"), 8, 5, 300, 14, ACCENT);
        label(root, network, Component.empty(), 8, 20, 300, 14, TEXT);
        root.addChildren(weaponsPanel, configurationPanel, informationPanel);
        IItemHandlerModifiable inventory = player.level().isClientSide ? new ItemStackHandler(9) : new WeaponPlatformInventory(platform);
        for (int slot = 0; slot < 8; slot++) {
            int index = slot;
            UIElement row = rows[slot] = new UIElement();
            at(row, 0, slot * 26, 100, 24);
            row.style(style -> style.backgroundTexture(new ColorRectTexture(0xff263b46)));
            ItemSlot item = new ItemSlot(new ItemHandlerCopySlot(inventory, slot, 0, 0));
            SharedBackpackStyles.standalone(item);
            at(item, 2, 2, 20, 20);
            row.addChild(item);
            Button select = selectors[slot] = new Button();
            at(select, 26, 2, 111, 22);
            select.setOnClick(event -> {
                selected = index;
                apply(snapshot);
            });
            rowLabels[slot] = select.text;
            select.text.textStyle(style -> style.fontSize(8).textColor(TEXT).textShadow(false));
            row.addChild(select);
            weaponRows.addChild(row);
        }
        label(configuration, name, Component.empty(), 0, 0, 190, 18, ACCENT);
        preview.style(style -> style.backgroundTexture(model));
        at(preview, 248, 84, 84, 84);
        configuration.addChild(preview);
        for (int index = 0; index < 4; index++) {
            int id = index + 1;
            Button button = ammunition[index] = button(configuration, "ammunition." + GunAmmunition.values()[id].name().toLowerCase(Locale.ROOT),
                    166 + index % 2 * 176, 89 + index / 2 * 49, 84, 30, PlatformRpc.AMMUNITION, id);
            button.text.textStyle(style -> style.fontSize(9));
        }
        label(configuration, thermal, Component.empty(), 0, 96, 190, 26, TEXT);
        enabled = button(configuration, "enabled", 0, 124, 90, 20, PlatformRpc.ENABLED, 0);
        supportSelf = button(configuration, "support_self", 0, 150, 90, 20, PlatformRpc.SELF, 0);
        supportTeam = button(configuration, "support_team", 96, 150, 90, 20, PlatformRpc.TEAM, 0);
        String[] categories = { "players", "hostile", "friendly", "neutral", "other" };
        for (int index = 0; index < targets.length; index++) targets[index] = button(configuration, "targets." + categories[index],
                166 + index % 3 * 88, 228 + index / 3 * 25, 82, 22, PlatformRpc.TARGETS, 1 << index);
        description.layout(style -> style.marginTop(6).minHeight(110));
        description.textStyle(style -> style.fontSize(9).textWrap(TextWrap.WRAP).adaptiveWidth(false).adaptiveHeight(true).textColor(TEXT).textShadow(false));
        chip = new ItemSlot(new ItemHandlerCopySlot(inventory, WeaponPlatformInventory.CHIP_SLOT, 0, 0));
        SharedBackpackStyles.standalone(chip);
        at(chip, 444, 62, 24, 24);
        root.addChild(chip);
        label(root, chipLabel, Component.translatable("gui.data_energistics.weapon_platform.chip"), 0, 0, 100, 24, ACCENT);
        research.textStyle(style -> style.fontSize(9).textWrap(TextWrap.WRAP).adaptiveWidth(false).adaptiveHeight(true).textColor(TEXT).textShadow(false));
        information.addChildren(research, description);
        protection = button(root, "protection", 0, 0, 120, 20, PlatformRpc.PROTECTION, 0);
        label(root, backpackLabel, Component.translatable("gui.data_energistics.weapon_platform.backpack"), 0, 0, 190, 12, TEXT);
        SharedBackpackStyles.inventory(backpack);
        root.addChild(backpack);
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
        int width = Math.min(620, viewport.getWidth() - 16);
        int height = Math.min(400, viewport.getHeight() - 16);
        int rightWidth = Math.max(108, width * 31 / 100);
        int listWidth = Math.max(64, width * 22 / 100);
        int rightX = width - rightWidth - 8;
        int centerX = listWidth + 14;
        int centerWidth = rightX - centerX - 6;
        int bodyHeight = height - 128;
        root.layout(style -> style.width(width).height(height));
        at(title, 8, 5, width - 16, 14);
        at(network, 8, 20, width - 16, 14);
        at(weaponsPanel, 6, 38, listWidth, bodyHeight);
        at(configurationPanel, centerX, 38, centerWidth, bodyHeight);
        at(informationPanel, rightX, 67, rightWidth, height - 98);
        int rowWidth = listWidth - 10;
        weaponRows.layout(style -> style.width(rowWidth).height(snapshot.capacity() * 26));
        for (int slot = 0; slot < rows.length; slot++) {
            at(rows[slot], 0, slot * 26, rowWidth, 24);
            at(selectors[slot], 25, 1, rowWidth - 26, 22);
        }
        int contentWidth = centerWidth - 10;
        configuration.layout(style -> style.width(contentWidth).height(218));
        at(name, 0, 0, contentWidth, 20);
        int side = Math.min(66, Math.max(42, (contentWidth - 48) / 2));
        at(preview, (contentWidth - 48) / 2, 30, 48, 48);
        for (int index = 0; index < ammunition.length; index++) at(ammunition[index], index % 2 == 0 ? 0 : contentWidth - side, 24 + index / 2 * 33, side, 27);
        at(thermal, 0, 88, contentWidth, 30);
        at(enabled, 0, 122, contentWidth, 20);
        int half = (contentWidth - 4) / 2;
        at(supportSelf, 0, 148, half, 20);
        at(supportTeam, half + 4, 148, half, 20);
        for (int index = 0; index < targets.length; index++) at(targets[index], index % 2 * (half + 4), 148 + index / 2 * 23, half, 20);
        at(chip, rightX + 2, 38, 24, 24);
        at(chipLabel, rightX + 30, 38, rightWidth - 30, 24);
        information.layout(style -> style.width(rightWidth - 10));
        research.layout(style -> style.width(rightWidth - 10));
        description.layout(style -> style.width(rightWidth - 10));
        at(protection, rightX, height - 25, rightWidth, 20);
        int backpackX = Math.max(8, (rightX - 166) / 2);
        at(backpackLabel, backpackX, height - 86, 166, 12);
        at(backpack, backpackX, height - 73, 162, 72);
        return Size.of(width, height);
    }

    private void apply(PlatformSnapshot value) {
        snapshot = value;
        weaponRows.layout(style -> style.height(value.capacity() * 26));
        network.setValue(Component.translatable("gui.data_energistics.weapon_platform." + (value.failed() ? "error" : value.connected() ? "connected" : "disconnected")));
        GunSnapshot active = null;
        for (int slot = 0; slot < rows.length; slot++) {
            rows[slot].setDisplay(slot < value.capacity());
            rowLabels[slot].setText(Component.literal((slot + 1) + "  ").append(Component.translatable("gui.data_energistics.weapon_platform.empty")));
        }
        for (GunSnapshot gun : value.guns()) {
            rowLabels[gun.slot()].setText(Component.literal((gun.slot() + 1) + "  ").append(Component.translatable("gun_form.data_energistics." + GunForm.of(gun.item()).id)));
            rowLabels[gun.slot()].textStyle(style -> style.textColor(gun.settings().enabled() ? ACCENT : 0xff82969e).fontSize(9));
            if (gun.slot() == selected) active = gun;
        }
        if (selected >= value.capacity()) selected = 0;
        name.setValue(active == null ? Component.translatable("gui.data_energistics.weapon_platform.select") : Component.translatable("gun_form.data_energistics." + GunForm.of(active.item()).id));
        model.setItems(active == null ? ItemStack.EMPTY : active.item());
        boolean medical = active != null && GunForm.of(active.item()) == GunForm.MEDICAL;
        enabled.setActive(active != null);
        enabled.setText(Component.translatable("gui.data_energistics.weapon_platform." + (active != null && active.settings().enabled() ? "disable" : "enable")));
        supportSelf.setDisplay(medical);
        supportTeam.setDisplay(medical);
        supportSelf.setText(Component.translatable("gui.data_energistics.weapon_platform.support_self").append(medical && active.settings().supportSelf() ? " ✓" : ""));
        supportTeam.setText(Component.translatable("gui.data_energistics.weapon_platform.support_team").append(medical && active.settings().supportTeam() ? " ✓" : ""));
        for (int index = 0; index < targets.length; index++) {
            targets[index].setDisplay(active != null && !medical);
            int color = active != null && (active.settings().targets() & 1 << index) != 0 ? ACCENT : 0xff82969e;
            targets[index].text.textStyle(style -> style.textColor(color));
        }
        for (int index = 0; index < ammunition.length; index++) {
            boolean expanded = active != null && ItemResearchUpgrades.of(active.item()).ids().contains(BiochipResearch.AMMUNITION);
            ammunition[index].setDisplay(active != null && !medical && (index < 2 || expanded));
            int color = active != null && active.settings().ammunition().ordinal() == index + 1 ? ACCENT : TEXT;
            ammunition[index].text.textStyle(style -> style.textColor(color));
        }
        thermal.setValue(active == null ? Component.empty() : Component.translatable("gui.data_energistics.weapon_platform.heat", (int) active.heat()).append(" · ").append(Component.translatable("gui.data_energistics.weapon_platform.state." + active.state())));
        description.setValue(active == null ? Component.translatable("gui.data_energistics.weapon_platform.instructions") : Component.translatable("gui.data_energistics.weapon_platform.description." + GunForm.of(active.item()).id));
        research.setValue(Component.translatable("gui.data_energistics.weapon_platform.research", value.capacity(), BiochipResearch.cooling(value.chip()), value.hud() ? "✓" : "—"));
        protection.setText(Component.translatable("gui.data_energistics.weapon_platform.protection").append(value.friendlyProtection() ? " ✓" : ""));
    }

    private static ScrollerView scrollPanel(UIElement content) {
        ScrollerView panel = new ScrollerView();
        panel.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).adaptiveHeight(false).adaptiveWidth(false));
        panel.viewPort.layout(style -> style.paddingAll(2));
        panel.viewPort.style(style -> style.backgroundTexture(new ColorRectTexture(0x40101920)));
        panel.addScrollViewChild(content);
        return panel;
    }

    private Button button(UIElement parent, String key, int x, int y, int width, int height, int operation, int argument) {
        Button button = new Button();
        button.setText(Component.translatable("gui.data_energistics.weapon_platform." + key));
        button.text.textStyle(style -> style.textColor(TEXT).textShadow(false).fontSize(9).textAlignHorizontal(Horizontal.CENTER));
        button.buttonStyle(style -> style.baseTexture(new ColorRectTexture(0xff314954)).hoverTexture(new ColorRectTexture(0xff47636d)).pressedTexture(new ColorRectTexture(0xff1a333b)));
        button.setOnClick(event -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, selected, argument);
        });
        at(button, x, y, width, height);
        parent.addChild(button);
        return button;
    }

    private static void label(UIElement parent, Label label, Component text, int x, int y, int width, int height, int color) {
        label.setValue(text);
        label.textStyle(style -> style.fontSize(9).textColor(color).textWrap(TextWrap.WRAP).textShadow(false).adaptiveWidth(false).adaptiveHeight(false));
        at(label, x, y, width, height);
        parent.addChild(label);
    }

    private static void at(UIElement element, int x, int y, int width, int height) {
        element.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }
}
