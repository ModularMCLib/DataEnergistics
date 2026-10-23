package com.fish_dan_.data_energistics.client.hud.weapon_platform;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.gui.style.DEStylesheets;
import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalCompass;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalTargetFrames;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalWeaponGuides;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.ui.TacticalHudAppearancePanel;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.GunHeatBar;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;

/** LDLib2 owns both the persistent HUD tree and its interactive controls, layout and hit testing. */
public final class TacticalHudLayer implements ModularHudLayer {

    private static final TacticalHudLayer INSTANCE = new TacticalHudLayer();
    private final EnumMap<HudModule, UIElement> modules = new EnumMap<>(HudModule.class);
    private final EnumMap<HudModule, Label> handles = new EnumMap<>(HudModule.class);
    private final Label[] gunLabels = new Label[8];
    private final GunHeatBar[] heatBars = new GunHeatBar[8];
    private final ItemStackTexture[] gunModels = new ItemStackTexture[8];
    private final UIElement[] gunRows = new UIElement[8];
    private @Nullable LocalPlayer owner;
    private @Nullable ModularUI ui;
    private UIElement root = new UIElement();
    private UIElement toolbar = new UIElement();
    private UIElement editor = new UIElement();
    private Label network = new Label();
    private PlatformSnapshot displayed = PlatformSnapshot.EMPTY;
    private int width, height;
    private long preferencesRevision = -1;
    private boolean showEditor;
    private boolean interactive;
    private boolean rebuild = true;

    private TacticalHudLayer() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Data_Energistics.id("tactical_weapon_hud"), INSTANCE);
    }

    public static ModularUI interactiveUi() {
        return INSTANCE.ensureUi();
    }

    public static void openAppearance() {
        INSTANCE.showEditor = true;
        INSTANCE.preferencesRevision = -1;
    }

    @Override
    public @Nullable ModularUI getModularUI() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || client.screen != null || !PlatformClientState.snapshot.hud()) return null;
        return ensureUi();
    }

    private ModularUI ensureUi() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("A tactical HUD requires an attached client player");
        if (ui == null || owner != player) {
            if (ui != null) ui.onRemoved();
            owner = player;
            root = new UIElement();
            modules.clear();
            handles.clear();
            displayed = PlatformSnapshot.EMPTY;
            preferencesRevision = -1;
            build();
            ui = ModularUI.of(UI.of(root, ObjectList.of(DEStylesheets.WEAPON_PLATFORM.get()), this::resize), player);
            ui.setTickWhileRending(true);
            root.addEventListener(UIEvents.TICK, event -> refresh());
        }
        refresh();
        return ui;
    }

    private void build() {
        module(HudModule.GUIDES, new TacticalWeaponGuides());
        module(HudModule.TARGETS, new TacticalTargetFrames());
        module(HudModule.COMPASS, new TacticalCompass());
        UIElement weapons = module(HudModule.WEAPONS, new UIElement());
        for (int index = 0; index < 8; index++) {
            UIElement row = gunRows[index] = new UIElement();
            row.addClass("platform-hud-weapon-row");
            row.setAllowHitTest(false);
            at(row, 4, 16 + index * 30, 200, 29);
            weapons.addChild(row);
            ItemStackTexture model = gunModels[index] = new ItemStackTexture();
            UIElement preview = new UIElement();
            preview.style(style -> style.backgroundTexture(model));
            preview.setAllowHitTest(false);
            at(preview, 2, 5, 20, 20);
            row.addChild(preview);
            GunHeatBar heat = heatBars[index] = new GunHeatBar();
            at(heat, 25, 5, 6, 18);
            row.addChild(heat);
            Label label = gunLabels[index] = label(row, Component.empty(), 36, 2, 161, 26);
            label.setAllowHitTest(false);
        }
        UIElement networkPanel = module(HudModule.NETWORK, new UIElement());
        network = label(networkPanel, Component.empty(), 6, 14, 196, 32);
        toolbar = new UIElement();
        toolbar.addClass("platform-hud-toolbar");
        button(toolbar, "hud.data_energistics.weapon_platform.appearance", "platform-hud-appearance", () -> {
            showEditor = !showEditor;
            preferencesRevision = -1;
        });
        button(toolbar, "gui.data_energistics.weapon_platform.open", "platform-hud-open-platform",
                () -> RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.OPEN, 0, 0));
        root.addChild(toolbar);
        editor = new TacticalHudAppearancePanel(() -> {
            showEditor = false;
            preferencesRevision = -1;
        });
        root.addChild(editor);
    }

    private UIElement module(HudModule module, UIElement element) {
        modules.put(module, element);
        boolean world = module == HudModule.TARGETS || module == HudModule.GUIDES;
        element.style(style -> style.zIndex(world ? -10 : 1));
        Label handle = label(element, Component.translatable("hud.data_energistics.weapon_platform.module." + module.name().toLowerCase(Locale.ROOT)), 4, module == HudModule.GUIDES ? 16 : 2, module == HudModule.TARGETS ? 120 : 140, 12);
        handles.put(module, handle);
        WindowDragHelper.setDragMove(handle, element, event -> interactive, event -> {
            HudModuleStyle old = TacticalHudPreferences.get(module);
            float actualScale = world ? 1 : viewportScale(element.getSizeWidth(), element.getSizeHeight(), old.scale());
            double availableX = world ? Math.max(1, width) : Math.max(1, width - element.getSizeWidth() * actualScale);
            double availableY = world ? Math.max(1, height) : Math.max(1, height - element.getSizeHeight() * actualScale);
            TacticalHudPreferences.set(module, old.toBuilder().x(Math.clamp(element.getLayoutX() / availableX, 0, 1))
                    .y(Math.clamp(element.getLayoutY() / availableY, 0, 1)).build());
        });
        root.addChild(element);
        return element;
    }

    private Size resize(Size viewport) {
        width = viewport.width;
        height = viewport.height;
        root.layout(style -> style.width(width).height(height));
        preferencesRevision = -1;
        refresh();
        return viewport;
    }

    private void refresh() {
        boolean interaction = Minecraft.getInstance().screen instanceof TacticalHudInteractionScreen;
        if (interaction != interactive) {
            interactive = interaction;
            preferencesRevision = -1;
        }
        PlatformSnapshot snapshot = PlatformClientState.snapshot;
        if (preferencesRevision != TacticalHudPreferences.revision()) {
            rebuild = true;
            for (var entry : modules.entrySet()) colorText(entry.getValue(), TacticalHudPreferences.get(entry.getKey()).textColor());
        }
        if (rebuild || displayed != snapshot) {
            if (displayed.guns().size() != snapshot.guns().size()) preferencesRevision = -1;
            displayed = snapshot;
            rebuild = false;
            for (int index = 0; index < gunLabels.length; index++) {
                gunRows[index].setDisplay(false);
                gunModels[index].setItems(ItemStack.EMPTY);
                heatBars[index].setHeat(0, false);
            }
            HudModuleStyle listStyle = TacticalHudPreferences.get(HudModule.WEAPONS);
            int row = 0;
            for (GunSnapshot gun : snapshot.guns()) {
                gunRows[gun.slot()].setDisplay(true);
                at(gunRows[gun.slot()], 4, 16 + row++ * 30, 200, 29);
                gunModels[gun.slot()].setItems(gun.item());
                heatBars[gun.slot()].setDisplay(listStyle.shows(HudModuleStyle.HEAT));
                heatBars[gun.slot()].setHeat(gun.heat(), gun.overheated());
                var text = Component.literal((gun.slot() + 1) + " ");
                if (listStyle.shows(HudModuleStyle.NAME)) text.append(gun.item().getHoverName());
                if (gun.overheated()) text.append(" ").append(Component.translatable("gui.data_energistics.weapon_platform.state.overheated"));
                if (listStyle.shows(HudModuleStyle.AMMUNITION)) {
                    GunForm form = GunForm.of(gun.item());
                    GunAmmunition ammo = GunAmmunition.values()[gun.actualAmmunition()];
                    long shots = (long) (snapshot.network().stored() / form.energy);
                    if (ammo.key != null) {
                        long special = snapshot.network().ammunition().getLong(ammo.ordinal() - 1) / ammo.amount;
                        if (special == 0 && form != GunForm.MEDICAL) ammo = GunAmmunition.AE;
                        else shots = Math.min(shots, special);
                    }
                    text.append("\n").append(Component.translatable("hud.data_energistics.weapon_platform.ammunition." + ammo.name().toLowerCase(Locale.ROOT)))
                            .append(" · ").append(Component.translatable("hud.data_energistics.weapon_platform.shots", GenericStackDisplayHelper.formatCompactAmount(shots)));
                }
                gunLabels[gun.slot()].setValue(text);
            }
            var networkStyle = TacticalHudPreferences.get(HudModule.NETWORK);
            var lines = new ObjectArrayList<Component>(3);
            if (networkStyle.shows(HudModuleStyle.CONNECTION)) {
                var connection = Component.translatable("gui.data_energistics.weapon_platform.network_short." + (snapshot.failed() ? "error" : snapshot.connected() ? "connected" : "disconnected"));
                if (snapshot.connected()) connection.append(" · ").append(Component.translatable("hud.data_energistics.weapon_platform.quantum_link", snapshot.network().frequency()));
                lines.add(connection);
            }
            if (snapshot.connected()) {
                var status = snapshot.network();
                if (networkStyle.shows(HudModuleStyle.ENERGY)) lines.add(Component.translatable("hud.data_energistics.weapon_platform.energy",
                        GenericStackDisplayHelper.formatCompactAmount((long) status.stored()), GenericStackDisplayHelper.formatCompactAmount((long) status.capacity())));
                if (networkStyle.shows(HudModuleStyle.FLOW)) lines.add(Component.translatable("hud.data_energistics.weapon_platform.energy_flow",
                        GenericStackDisplayHelper.formatCompactAmount((long) status.input()), GenericStackDisplayHelper.formatCompactAmount((long) status.usage())));
            }
            var networkText = Component.empty();
            for (Component line : lines) {
                if (!networkText.getSiblings().isEmpty()) networkText.append("\n");
                networkText.append(line);
            }
            network.setValue(networkText);
        }
        if (preferencesRevision != TacticalHudPreferences.revision()) {
            preferencesRevision = TacticalHudPreferences.revision();
            rebuild = true;
            for (var entry : modules.entrySet()) {
                HudModule module = entry.getKey();
                UIElement element = entry.getValue();
                HudModuleStyle style = TacticalHudPreferences.get(module);
                int w = switch (module) {
                    case COMPASS -> 260;
                    case WEAPONS, NETWORK -> 208;
                    case TARGETS, GUIDES -> Math.max(1, width);
                };
                int h = switch (module) {
                    case COMPASS -> 48;
                    case WEAPONS -> 18 + snapshot.guns().size() * 30;
                    case NETWORK -> 50;
                    case TARGETS, GUIDES -> Math.max(1, height);
                };
                boolean world = module == HudModule.TARGETS || module == HudModule.GUIDES;
                float scale = world ? 1 : viewportScale(w, h, style.scale());
                at(element, (int) ((world ? width : width - w * scale) * style.x()), (int) ((world ? height : height - h * scale) * style.y()), w, h);
                element.style(value -> value.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
                if (!world) element.style(value -> value.backgroundTexture(GuiTextureGroup.of(new ColorRectTexture(style.background()),
                        style.borderWidth() == 0 ? IGuiTexture.EMPTY : new ColorBorderTexture(-style.borderWidth(), style.borderColor()))));
                element.setDisplay(style.visible() && (module != HudModule.WEAPONS || !snapshot.guns().isEmpty()));
                handles.get(module).setDisplay(interactive);
            }
            toolbar.setDisplay(interactive);
            at(toolbar, (int) (width * 0.015), (int) (height * 0.015), 188, 20);
            float toolbarScale = viewportScale(188, 20, 1);
            toolbar.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(toolbarScale)));
            editor.setDisplay(interactive && showEditor);
            float editorScale = viewportScale(TacticalHudAppearancePanel.WIDTH, TacticalHudAppearancePanel.HEIGHT, 1);
            at(editor, Math.max(0, (int) ((width - TacticalHudAppearancePanel.WIDTH * editorScale) / 2)),
                    (int) ((height - TacticalHudAppearancePanel.HEIGHT * editorScale) / 2), TacticalHudAppearancePanel.WIDTH, TacticalHudAppearancePanel.HEIGHT);
            editor.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(editorScale)));
        }
    }

    private static void colorText(UIElement element, int color) {
        if (element instanceof Label label) label.textStyle(style -> style.textColor(color));
        for (UIElement child : element.getChildren()) colorText(child, color);
    }

    private static void button(UIElement parent, String key, String styleClass, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.addClass("platform-action");
        button.addClass("platform-hud-action");
        button.addClass(styleClass);
        button.setOnClick(event -> action.run());
        parent.addChild(button);
    }

    private static Label label(UIElement parent, Component text, int x, int y, int width, int height) {
        Label label = new Label();
        label.setValue(text);
        label.addClass("platform-hud-label");
        at(label, x, y, width, height);
        parent.addChild(label);
        return label;
    }

    private static void at(UIElement element, int x, int y, int width, int height) {
        element.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }

    private float viewportScale(float moduleWidth, float moduleHeight, float zoom) {
        return Math.min(zoom * Math.min(width / 854f, height / 480f), Math.min(width / moduleWidth, height / moduleHeight));
    }
}
