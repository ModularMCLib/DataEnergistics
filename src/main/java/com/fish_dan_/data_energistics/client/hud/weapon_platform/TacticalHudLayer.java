package com.fish_dan_.data_energistics.client.hud.weapon_platform;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.gui.style.DEStylesheets;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.navigation.TacticalNavigationHeader;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalOreLabel;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalTargetFrames;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalWeaponGuides;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.sidebar.TacticalHudSidebars;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.ui.TacticalHudAppearancePanel;
import com.fish_dan_.data_energistics.client.render.overlay.PlatformOreRenderer;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.config.client.hud.HudModuleStyle;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;

/** LDLib2 owns both the persistent HUD tree and its interactive controls, layout and hit testing. */
public final class TacticalHudLayer implements ModularHudLayer {

    private static final TacticalHudLayer INSTANCE = new TacticalHudLayer();
    private final EnumMap<HudModule, UIElement> modules = new EnumMap<>(HudModule.class);
    private final EnumMap<HudModule, Label> handles = new EnumMap<>(HudModule.class);
    private TacticalNavigationHeader navigation = new TacticalNavigationHeader();
    private TacticalHudSidebars weapons = new TacticalHudSidebars();
    private @Nullable LocalPlayer owner;
    private @Nullable ModularUI ui;
    private UIElement root = new UIElement();
    private UIElement toolbar = new UIElement();
    private UIElement editor = new UIElement();
    private PlatformSnapshot displayed = PlatformSnapshot.EMPTY;
    private int width, height;
    private long preferencesRevision = -1;
    private boolean showEditor;
    private boolean interactive;

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
        if (client.player == null || client.options.hideGui || client.screen != null || !PlatformClientState.snapshot.hud() || !PlatformClientState.snapshot.hudEnabled()) return null;
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
            ui = ModularUI.of(UI.of(root, ObjectList.of(DEStylesheets.WEAPON_PLATFORM.get()), viewport -> resize(viewport, player)), player);
            ui.setTickWhileRending(true);
            root.addEventListener(UIEvents.TICK, event -> refresh(player));
        }
        refresh(player);
        return ui;
    }

    private void build() {
        module(HudModule.GUIDES, new TacticalWeaponGuides());
        module(HudModule.TARGETS, new TacticalTargetFrames());
        module(HudModule.ORES, new TacticalOreLabel());
        navigation = new TacticalNavigationHeader();
        root.addChild(navigation);
        weapons = new TacticalHudSidebars();
        root.addChild(weapons);
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

    private void module(HudModule module, UIElement element) {
        modules.put(module, element);
        boolean world = module == HudModule.TARGETS || module == HudModule.GUIDES;
        element.style(style -> style.zIndex(world ? -10 : 1));
        Label handle = label(element, Component.translatable("hud.data_energistics.weapon_platform.module." + module.getSerializedName()), module == HudModule.GUIDES ? 16 : 2,
                switch (module) {
                    case WEAPONS -> 124;
                    case TARGETS -> 120;
                    default -> 140;
                });
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
    }

    private Size resize(Size viewport, LocalPlayer player) {
        width = viewport.width;
        height = viewport.height;
        root.layout(style -> style.width(width).height(height));
        preferencesRevision = -1;
        refresh(player);
        return viewport;
    }

    private void refresh(LocalPlayer player) {
        boolean interaction = Minecraft.getInstance().screen instanceof TacticalHudInteractionScreen;
        if (interaction != interactive) {
            interactive = interaction;
            preferencesRevision = -1;
        }
        PlatformSnapshot snapshot = PlatformClientState.snapshot;
        navigation.refresh(snapshot, player, width, height);
        weapons.refresh(snapshot, player, interactive, width, height);
        modules.get(HudModule.ORES).setVisible(interactive || !PlatformOreRenderer.label().getString().isEmpty());
        if (preferencesRevision != TacticalHudPreferences.getRevision() || !displayed.systems().hud().equals(snapshot.systems().hud())) {
            preferencesRevision = -1;
            for (var entry : modules.entrySet()) colorText(entry.getValue(), TacticalHudPreferences.get(entry.getKey()).textColor());
        }
        displayed = snapshot;
        if (preferencesRevision != TacticalHudPreferences.getRevision()) {
            preferencesRevision = TacticalHudPreferences.getRevision();
            for (var entry : modules.entrySet()) {
                HudModule module = entry.getKey();
                UIElement element = entry.getValue();
                HudModuleStyle style = TacticalHudPreferences.get(module);
                int w = switch (module) {
                    case ORES -> 200;
                    case TARGETS, GUIDES -> Math.max(1, width);
                    default -> throw new IllegalStateException("Only projected HUD modules use free placement");
                };
                int h = switch (module) {
                    case ORES -> 18;
                    case TARGETS, GUIDES -> Math.max(1, height);
                    default -> throw new IllegalStateException("Only projected HUD modules use free placement");
                };
                boolean world = module == HudModule.TARGETS || module == HudModule.GUIDES;
                float scale = world ? 1 : viewportScale(w, h, style.scale());
                at(element, (int) ((world ? width : width - w * scale) * style.x()), (int) ((world ? height : height - h * scale) * style.y()), w, h);
                element.style(value -> value.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
                if (!world) element.style(value -> value.backgroundTexture(GuiTextureGroup.of(new ColorRectTexture(style.background()),
                        style.borderWidth() == 0 ? IGuiTexture.EMPTY : new ColorBorderTexture(-style.borderWidth(), style.borderColor()))));
                element.setDisplay(style.visible());
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

    private static Label label(UIElement parent, Component text, int y, int width) {
        Label label = new Label();
        label.setValue(text);
        label.addClass("platform-hud-label");
        at(label, 4, y, width, 12);
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
