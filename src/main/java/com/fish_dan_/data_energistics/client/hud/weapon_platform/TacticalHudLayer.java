package com.fish_dan_.data_energistics.client.hud.weapon_platform;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalCompass;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalTargetFrames;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalWeaponGuides;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.GunHeatBar;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
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
    private final Button[] gunButtons = new Button[8];
    private final Button[] ammunition = new Button[4];
    private final Button[] categories = new Button[5];
    private final Button[] fields = new Button[7];
    private @Nullable LocalPlayer owner;
    private @Nullable ModularUI ui;
    private UIElement root = new UIElement();
    private UIElement toolbar = new UIElement();
    private UIElement editor = new UIElement();
    private Label network = new Label();
    private Label selectedName = new Label();
    private Button enabled = new Button();
    private Button supportSelf = new Button();
    private Button supportTeam = new Button();
    private Button protection = new Button();
    private Button moduleSelector = new Button();
    private Button visibility = new Button();
    private Label styleValues = new Label();
    private PlatformSnapshot displayed = PlatformSnapshot.EMPTY;
    private HudModule editingModule = HudModule.WEAPONS;
    private int selected;
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
            ui = ModularUI.of(UI.of(root, this::resize), player);
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
            int slot = index;
            Button row = gunButtons[index] = button(weapons, "", 4, 16 + index * 30, 200, 29, () -> {
                selected = slot;
                rebuild = true;
            });
            row.noText();
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
        network = label(networkPanel, Component.empty(), 6, 14, 318, 32);
        UIElement controls = module(HudModule.CONTROLS, new UIElement());
        selectedName = label(controls, Component.empty(), 6, 17, 188, 27);
        enabled = action(controls, "enabled", 6, 45, 92, PlatformRpc.ENABLED, 0);
        protection = action(controls, "protection", 102, 45, 92, PlatformRpc.PROTECTION, 0);
        for (int index = 0; index < 4; index++) ammunition[index] = action(controls,
                "ammunition." + GunAmmunition.values()[index + 1].name().toLowerCase(Locale.ROOT), 6 + index % 2 * 96, 72 + index / 2 * 25, 92, PlatformRpc.AMMUNITION, index + 1);
        String[] names = { "players", "hostile", "friendly", "neutral", "other" };
        for (int index = 0; index < 5; index++) categories[index] = action(controls, "targets." + names[index], 6 + index % 2 * 96, 126 + index / 2 * 25, 92, PlatformRpc.TARGETS, 1 << index);
        supportSelf = action(controls, "support_self", 6, 202, 92, PlatformRpc.SELF, 0);
        supportTeam = action(controls, "support_team", 102, 202, 92, PlatformRpc.TEAM, 0);
        toolbar = new UIElement();
        toolbar.style(style -> style.zIndex(30));
        button(toolbar, "hud.data_energistics.weapon_platform.appearance", 0, 0, 90, 19, () -> {
            showEditor = !showEditor;
            preferencesRevision = -1;
        });
        action(toolbar, "open", 94, 0, 90, PlatformRpc.OPEN, 0);
        root.addChild(toolbar);
        editor = new UIElement();
        editor.style(style -> style.backgroundTexture(WeaponPlatformStyle.surface(WeaponPlatformStyle.SURFACE)).zIndex(40));
        root.addChild(editor);
        moduleSelector = button(editor, "", 5, 5, 180, 20, () -> {
            editingModule = HudModule.values()[(editingModule.ordinal() + 1) % HudModule.values().length];
            preferencesRevision = -1;
        });
        styleValues = label(editor, Component.empty(), 5, 28, 180, 30);
        visibility = button(editor, "", 5, 60, 85, 19, () -> changeStyle(0));
        button(editor, "hud.data_energistics.weapon_platform.background", 95, 60, 90, 19, () -> changeStyle(1));
        button(editor, "hud.data_energistics.weapon_platform.alpha_minus", 5, 83, 85, 19, () -> changeStyle(2));
        button(editor, "hud.data_energistics.weapon_platform.alpha_plus", 95, 83, 90, 19, () -> changeStyle(3));
        button(editor, "hud.data_energistics.weapon_platform.scale_minus", 5, 106, 85, 19, () -> changeStyle(4));
        button(editor, "hud.data_energistics.weapon_platform.scale_plus", 95, 106, 90, 19, () -> changeStyle(5));
        for (int index = 0; index < fields.length; index++) {
            int field = index;
            fields[index] = button(editor, "", 5 + index % 2 * 90, 130 + index / 2 * 22, 85, 19, () -> {
                HudModuleStyle style = TacticalHudPreferences.get(editingModule);
                TacticalHudPreferences.set(editingModule, new HudModuleStyle(style.x(), style.y(), style.scale(), style.background(), style.fields() ^ 1 << field, style.visible()));
            });
        }
        button(editor, "hud.data_energistics.weapon_platform.reset", 5, 222, 85, 19, () -> TacticalHudPreferences.reset(editingModule));
        button(editor, "gui.done", 95, 222, 90, 19, () -> {
            showEditor = false;
            preferencesRevision = -1;
        });
    }

    private UIElement module(HudModule module, UIElement element) {
        modules.put(module, element);
        boolean world = module == HudModule.TARGETS || module == HudModule.GUIDES;
        element.style(style -> style.zIndex(world ? -10 : 1));
        Label handle = label(element, Component.translatable("hud.data_energistics.weapon_platform.module." + module.name().toLowerCase(Locale.ROOT)), 4, module == HudModule.GUIDES ? 16 : 2, module == HudModule.TARGETS ? 120 : 140, 12);
        handles.put(module, handle);
        WindowDragHelper.setDragMove(handle, element, event -> interactive, event -> {
            HudModuleStyle old = TacticalHudPreferences.get(module);
            float actualScale = moduleScale(module, element.getSizeWidth(), element.getSizeHeight(), old.scale());
            double availableX = world ? Math.max(1, width) : Math.max(1, width - element.getSizeWidth() * actualScale);
            double availableY = world ? Math.max(1, height) : Math.max(1, height - element.getSizeHeight() * actualScale);
            TacticalHudPreferences.set(module, new HudModuleStyle(Math.clamp(element.getLayoutX() / availableX, 0, 1), Math.clamp(element.getLayoutY() / availableY, 0, 1), old.scale(), old.background(), old.fields(), old.visible()));
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
        if (rebuild || displayed != snapshot) {
            if (displayed.guns().size() != snapshot.guns().size()) preferencesRevision = -1;
            if (selected >= snapshot.capacity()) selected = 0;
            displayed = snapshot;
            rebuild = false;
            for (int index = 0; index < gunLabels.length; index++) {
                gunButtons[index].setDisplay(false);
                gunModels[index].setItems(ItemStack.EMPTY);
                heatBars[index].setHeat(0, false);
            }
            GunSnapshot active = null;
            HudModuleStyle listStyle = TacticalHudPreferences.get(HudModule.WEAPONS);
            int row = 0;
            for (GunSnapshot gun : snapshot.guns()) {
                gunButtons[gun.slot()].setDisplay(true);
                at(gunButtons[gun.slot()], 4, 16 + row++ * 30, 200, 29);
                gunModels[gun.slot()].setItems(gun.item());
                heatBars[gun.slot()].setDisplay(listStyle.shows(HudModuleStyle.HEAT));
                heatBars[gun.slot()].setHeat(gun.heat(), gun.overheated());
                if (gun.slot() == selected) active = gun;
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
            var networkText = Component.translatable("gui.data_energistics.weapon_platform." + (snapshot.failed() ? "error" : snapshot.connected() ? "connected" : "disconnected"));
            if (snapshot.connected()) {
                var status = snapshot.network();
                networkText.append(" · ").append(Component.translatable("hud.data_energistics.weapon_platform.quantum_link", status.frequency()));
                networkText.append("\n").append(Component.translatable("hud.data_energistics.weapon_platform.energy",
                        GenericStackDisplayHelper.formatCompactAmount((long) status.stored()), GenericStackDisplayHelper.formatCompactAmount((long) status.capacity())));
                networkText.append("\n").append(Component.translatable("hud.data_energistics.weapon_platform.energy_flow",
                        GenericStackDisplayHelper.formatCompactAmount((long) status.input()), GenericStackDisplayHelper.formatCompactAmount((long) status.usage())));
            }
            network.setValue(networkText);
            selectedName.setValue(active == null ? Component.translatable("gui.data_energistics.weapon_platform.select") : Component.translatable("gun_form.data_energistics." + GunForm.of(active.item()).id));
            enabled.setActive(active != null);
            enabled.setText(Component.translatable("gui.data_energistics.weapon_platform." + (active != null && active.settings().enabled() ? "disable" : "enable")));
            protection.setText(Component.translatable("gui.data_energistics.weapon_platform.protection").append(snapshot.friendlyProtection() ? " ✓" : ""));
            boolean medical = active != null && GunForm.of(active.item()) == GunForm.MEDICAL;
            supportSelf.setDisplay(medical);
            supportTeam.setDisplay(medical);
            supportSelf.setText(Component.translatable("gui.data_energistics.weapon_platform.support_self").append(medical && active.settings().supportSelf() ? " ✓" : ""));
            supportTeam.setText(Component.translatable("gui.data_energistics.weapon_platform.support_team").append(medical && active.settings().supportTeam() ? " ✓" : ""));
            for (int index = 0; index < 4; index++) {
                ammunition[index].setDisplay(active != null && !medical && (index < 2 || ItemResearchUpgrades.of(active.item()).ids().contains(BiochipResearch.AMMUNITION)));
                int color = active != null && active.settings().ammunition().ordinal() == index + 1 ? WeaponPlatformStyle.TEXT : WeaponPlatformStyle.MUTED;
                ammunition[index].text.textStyle(style -> style.textColor(color));
            }
            for (int index = 0; index < 5; index++) {
                categories[index].setDisplay(true);
                int color = (snapshot.targetMask() & 1 << index) != 0 ? WeaponPlatformStyle.TEXT : WeaponPlatformStyle.MUTED;
                categories[index].text.textStyle(style -> style.textColor(color));
            }
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
                    case WEAPONS -> 208;
                    case NETWORK -> 330;
                    case CONTROLS -> 200;
                    case TARGETS, GUIDES -> Math.max(1, width);
                };
                int h = switch (module) {
                    case COMPASS -> 48;
                    case WEAPONS -> 18 + snapshot.guns().size() * 30;
                    case NETWORK -> 50;
                    case CONTROLS -> 228;
                    case TARGETS, GUIDES -> Math.max(1, height);
                };
                float scale = moduleScale(module, w, h, style.scale());
                boolean world = module == HudModule.TARGETS || module == HudModule.GUIDES;
                at(element, (int) ((world ? width : width - w * scale) * style.x()), (int) ((world ? height : height - h * scale) * style.y()), w, h);
                element.style(value -> value.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
                if (!world) element.style(value -> value.backgroundTexture(new ColorRectTexture(style.background())));
                element.setDisplay(style.visible() && (module != HudModule.CONTROLS || interactive) && (module != HudModule.WEAPONS || !snapshot.guns().isEmpty()));
                handles.get(module).setDisplay(interactive);
            }
            toolbar.setDisplay(interactive);
            at(toolbar, (int) (width * 0.015), (int) (height * 0.015), 188, 20);
            float toolbarScale = moduleScale(HudModule.CONTROLS, 188, 20, 1);
            toolbar.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(toolbarScale)));
            editor.setDisplay(interactive && showEditor);
            float editorScale = moduleScale(HudModule.CONTROLS, 190, 248, 1);
            at(editor, Math.max(0, (int) ((width - 190 * editorScale) / 2)), (int) ((height - 248 * editorScale) / 2), 190, 248);
            editor.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(editorScale)));
            HudModuleStyle value = TacticalHudPreferences.get(editingModule);
            moduleSelector.setText(Component.translatable("hud.data_energistics.weapon_platform.module." + editingModule.name().toLowerCase(Locale.ROOT)));
            styleValues.setValue(Component.translatable("hud.data_energistics.weapon_platform.style_values", Math.round(value.scale() * 100), value.background() >>> 24));
            visibility.setText(Component.translatable("hud.data_energistics.weapon_platform." + (value.visible() ? "hide" : "show")));
            String[] fieldNames = { "name", "distance", "health", "binary", "heat", "ammunition", "target" };
            if (editingModule == HudModule.GUIDES) fieldNames = new String[] { "laser", "trajectory", "impact" };
            for (int index = 0; index < fields.length; index++) {
                fields[index].setDisplay(index < fieldNames.length);
                if (index < fieldNames.length) fields[index].setText(Component.translatable("hud.data_energistics.weapon_platform.field." + fieldNames[index]).append(value.shows(1 << index) ? " ✓" : ""));
            }
        }
    }

    private void changeStyle(int operation) {
        HudModuleStyle old = TacticalHudPreferences.get(editingModule);
        int background = old.background();
        float scale = old.scale();
        boolean visible = old.visible();
        switch (operation) {
            case 0 -> visible = !visible;
            case 1 -> background = (background & 0xff000000) | switch (background & 0xffffff) {
                case 0xe3e3ea -> 0xa7adbf;
                case 0xa7adbf -> 0xcbccd4;
                default -> 0xe3e3ea;
            };
            case 2, 3 -> background = Math.clamp((background >>> 24) + (operation == 2 ? -20 : 20), 0, 255) << 24 | background & 0xffffff;
            case 4, 5 -> scale = Math.clamp(scale + (operation == 4 ? -0.1f : 0.1f), 0.5f, 2f);
            default -> throw new IllegalArgumentException("Unknown HUD style control");
        }
        TacticalHudPreferences.set(editingModule, new HudModuleStyle(old.x(), old.y(), scale, background, old.fields(), visible));
    }

    private Button action(UIElement parent, String key, int x, int y, int width, int operation, int argument) {
        return button(parent, "gui.data_energistics.weapon_platform." + key, x, y, width, 21,
                () -> RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, selected, argument));
    }

    private static Button button(UIElement parent, String key, int x, int y, int width, int height, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.text.textStyle(style -> style.fontSize(8).textColor(WeaponPlatformStyle.TEXT).textShadow(false));
        button.buttonStyle(style -> style.baseTexture(WeaponPlatformStyle.surface(WeaponPlatformStyle.SECTION)).hoverTexture(WeaponPlatformStyle.surface(WeaponPlatformStyle.SURFACE)));
        button.setOnClick(event -> action.run());
        at(button, x, y, width, height);
        parent.addChild(button);
        return button;
    }

    private static Label label(UIElement parent, Component text, int x, int y, int width, int height) {
        Label label = new Label();
        label.setValue(text);
        label.textStyle(style -> style.fontSize(8).textColor(WeaponPlatformStyle.TEXT).textShadow(false).adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.WRAP));
        at(label, x, y, width, height);
        parent.addChild(label);
        return label;
    }

    private static void at(UIElement element, int x, int y, int width, int height) {
        element.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }

    private float moduleScale(HudModule module, float moduleWidth, float moduleHeight, float zoom) {
        if (module == HudModule.TARGETS || module == HudModule.GUIDES) return 1;
        return Math.min(zoom * Math.min(width / 854f, height / 480f), Math.min(width / moduleWidth, height / moduleHeight));
    }
}
