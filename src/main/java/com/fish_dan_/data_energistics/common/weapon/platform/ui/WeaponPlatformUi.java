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
    private final ScrollerView root = new ScrollerView();
    private final UIElement frame = new UIElement();
    private final UIElement[] rows = new UIElement[8];
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
    private PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;
    private int selected;

    private WeaponPlatformUi(Player player, ItemStack platform) {
        this.player = player;
        root.scrollerStyle(style -> style.mode(ScrollerMode.BOTH).adaptiveHeight(false).adaptiveWidth(false));
        root.addScrollViewChild(frame);
        frame.layout(style -> style.width(600).height(400));
        frame.style(style -> style.backgroundTexture(new GuiTextureGroup(new ColorRectTexture(0xff17242c), new ColorBorderTexture(-1, 0xff527b86))));
        label(frame, title, Component.translatable("item.data_energistics.digital_weapon_platform"), 12, 10, 440, 18, ACCENT);
        label(frame, network, Component.empty(), 12, 30, 575, 18, TEXT);
        IItemHandlerModifiable inventory = player.level().isClientSide ? new ItemStackHandler(9) : new WeaponPlatformInventory(platform);
        for (int slot = 0; slot < 8; slot++) {
            int index = slot;
            UIElement row = rows[slot] = new UIElement();
            at(row, 12, 62 + slot * 28, 140, 26);
            row.style(style -> style.backgroundTexture(new ColorRectTexture(0xff263b46)));
            ItemSlot item = new ItemSlot(new ItemHandlerCopySlot(inventory, slot, 0, 0));
            SharedBackpackStyles.standalone(item);
            at(item, 3, 3, 20, 20);
            row.addChild(item);
            Button select = new Button();
            at(select, 26, 2, 111, 22);
            select.setOnClick(event -> {
                selected = index;
                apply(snapshot);
            });
            rowLabels[slot] = select.text;
            row.addChild(select);
            frame.addChild(row);
        }
        label(frame, name, Component.empty(), 164, 58, 264, 18, ACCENT);
        preview.style(style -> style.backgroundTexture(model));
        at(preview, 248, 84, 84, 84);
        frame.addChild(preview);
        for (int index = 0; index < 4; index++) {
            int id = index + 1;
            Button button = ammunition[index] = button(frame, "ammunition." + GunAmmunition.values()[id].name().toLowerCase(Locale.ROOT),
                    166 + index % 2 * 176, 89 + index / 2 * 49, 84, 30, PlatformRpc.AMMUNITION, id);
            button.text.textStyle(style -> style.fontSize(9));
        }
        label(frame, thermal, Component.empty(), 166, 176, 264, 18, TEXT);
        enabled = button(frame, "enabled", 166, 200, 126, 22, PlatformRpc.ENABLED, 0);
        supportSelf = button(frame, "support_self", 166, 228, 126, 22, PlatformRpc.SELF, 0);
        supportTeam = button(frame, "support_team", 300, 228, 126, 22, PlatformRpc.TEAM, 0);
        String[] categories = { "players", "hostile", "friendly", "neutral", "other" };
        for (int index = 0; index < targets.length; index++) targets[index] = button(frame, "targets." + categories[index],
                166 + index % 3 * 88, 228 + index / 3 * 25, 82, 22, PlatformRpc.TARGETS, 1 << index);
        ScrollerView text = new ScrollerView();
        text.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).adaptiveHeight(false));
        at(text, 440, 170, 148, 122);
        description.layout(style -> style.width(134).minHeight(114));
        description.textStyle(style -> style.fontSize(9).textWrap(TextWrap.WRAP).adaptiveWidth(false).adaptiveHeight(true).textColor(TEXT).textShadow(false));
        text.addScrollViewChild(description);
        frame.addChild(text);
        ItemSlot chip = new ItemSlot(new ItemHandlerCopySlot(inventory, WeaponPlatformInventory.CHIP_SLOT, 0, 0));
        SharedBackpackStyles.standalone(chip);
        at(chip, 444, 62, 24, 24);
        frame.addChild(chip);
        label(frame, new Label(), Component.translatable("gui.data_energistics.weapon_platform.chip"), 474, 62, 110, 24, ACCENT);
        label(frame, research, Component.empty(), 442, 92, 144, 72, TEXT);
        protection = button(frame, "protection", 440, 298, 146, 22, PlatformRpc.PROTECTION, 0);
        label(frame, new Label(), Component.translatable("gui.data_energistics.weapon_platform.backpack"), 205, 300, 190, 16, TEXT);
        InventorySlots backpack = new InventorySlots();
        SharedBackpackStyles.inventory(backpack);
        at(backpack, 205, 320, 190, 76);
        frame.addChild(backpack);
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
        int width = Math.min(608, Math.max(1, viewport.getWidth() - 12));
        int height = Math.min(408, Math.max(1, viewport.getHeight() - 12));
        root.layout(style -> style.width(width).height(height));
        return Size.of(width, height);
    }

    private void apply(PlatformSnapshot value) {
        snapshot = value;
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
