package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload.PatternUploadActions;
import com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload.PatternUploadUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.window.TerminalSubWindow;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Adapts native encoding menus to the same LDLib2 upload window used by the player terminal. */
@NullMarked
final class PatternEncodingUploadPanel implements PatternUploadActions {

    private final AbstractContainerScreen<?> screen;
    private final PatternEncodingTermMenu menu;
    private final PatternEncodingPreviewMenu preview;
    private final UIElement popup = TerminalUiLayout.popup("upload");
    private final PatternUploadUi view = new PatternUploadUi(popup, this);
    private final TerminalSubWindow window = new TerminalSubWindow(popup, this::close);
    private final ModularUI modularUi;
    private Rect2i bounds = new Rect2i(0, 0, 190, 188);
    private ObjectList<Rect2i> exclusions = ObjectImmutableList.of();
    private @Nullable SyncedPatternProviderList preferenceState;
    private @Nullable PatternEncodingRankingContext preferenceContext;
    private ObjectSet<String> preferenceDigests = new ObjectOpenHashSet<>();
    private boolean visible;
    private boolean pointerOwned;
    private boolean rendering;
    private boolean initialized;

    PatternEncodingUploadPanel(AbstractContainerScreen<?> screen, PatternEncodingTermMenu menu) {
        this.screen = screen;
        this.menu = menu;
        preview = (PatternEncodingPreviewMenu) menu;
        popup.addClass("native-upload-popup");
        UIElement root = new UIElement();
        root.addClass("terminal-root");
        root.setAllowHitTest(false);
        root.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100).paddingAll(0));
        root.addChild(popup);
        TerminalUiLayout.require(popup, "popup_title", Label.class).setText(Component.translatable("gui.data_energistics.player_terminal.upload"));
        TerminalUiLayout.require(popup, "close", UIElement.class).style(style -> style.tooltips(TerminalTooltipLines.split(
                Component.translatable("gui.data_energistics.player_terminal.close"), Component.translatable("gui.data_energistics.player_terminal.hold_to_drag"))));
        modularUi = new ModularUI(UI.of(root, ObjectImmutableList.of(TerminalUiLayout.stylesheet())), menu.getPlayer());
    }

    void init(Rect2i anchor, ObjectList<Rect2i> occupied) {
        if (!initialized) visible = PatternEncodingPreferencesClient.isPreviewPanelPinned() && uploadEnabled();
        initialized = true;
        var saved = PatternEncodingPreferencesClient.previewPanelPosition();
        Rect2i preferred = saved.isPresent() ? PatternEncodingPreviewPlacement.fromPercent(saved.get().xPercent(), saved.get().yPercent(),
                190, 188, screen.width, screen.height) :
                PatternEncodingPreviewPlacement.findBestBounds(anchor, 190, 188, anchor.getY(), 4, -4, 4, screen.width, screen.height, occupied);
        window.place(screen.width, screen.height, preferred, 4, 4);
        bounds = preferred;
        popup.setDisplay(visible);
        modularUi.setScreenAndInit(screen);
        updateBounds();
    }

    void update() {
        if (!uploadEnabled()) close();
        var state = preview.data_energistics$getSyncedPatternProviderState();
        var pattern = preview.data_energistics$getEncodedPatternDefinition();
        view.apply(state, pattern == null ? null : new GenericStack(pattern, 1), true);
        syncPreferences(state);
    }

    void tick() {
        PatternEncodingPreferencesClient.flushDeferredSnapshot(menu);
        modularUi.tick();
        view.tick();
    }

    boolean uploadEnabled() {
        return !(menu instanceof PatternEncodingSourceAware source) || source.data_energistics$isUploadEnabled();
    }

    void open() {
        if (!uploadEnabled()) return;
        visible = true;
        popup.setDisplay(true);
        preview.data_energistics$refreshSyncedPatternProviders();
    }

    void close() {
        visible = false;
        pointerOwned = false;
        window.cancelDrag();
        modularUi.clearFocus();
        popup.setDisplay(false);
        exclusions = ObjectImmutableList.of();
    }

    boolean contains(double x, double y) {
        return visible && bounds.contains((int) x, (int) y);
    }

    ObjectList<Rect2i> exclusions() {
        return visible ? exclusions : ObjectImmutableList.of();
    }

    boolean suppressTooltip(int x, int y) {
        return !rendering && contains(x, y);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int guiLeft, int guiTop) {
        if (!visible) return;
        rendering = true;
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(-guiLeft, -guiTop, PatternEncodingPreviewLayers.PANEL_Z);
            PatternEncodingPreviewLayers.clearCoveredDepth(graphics, exclusions());
            modularUi.getWidget().mouseMoved(mouseX, mouseY);
            modularUi.getWidget().render(graphics, mouseX, mouseY, partialTick);
            updateBounds();
        } finally {
            graphics.pose().popPose();
            graphics.flush();
            rendering = false;
        }
    }

    boolean mouseClicked(double x, double y, int button) {
        if (!contains(x, y)) {
            modularUi.clearFocus();
            return false;
        }
        if (button == 1 && Screen.hasShiftDown() && TerminalUiLayout.require(popup, "close", UIElement.class).isIntersectWithPoint(x, y)) {
            PatternEncodingPreferencesClient.setPreviewPanelPinned(menu, !PatternEncodingPreferencesClient.isPreviewPanelPinned());
            return true;
        }
        pointerOwned = true;
        if (!window.beginDrag(x, y, button)) {
            modularUi.refreshHoveredElementAtScreen((float) x, (float) y);
            modularUi.getWidget().mouseClicked(x, y, button);
        }
        return true;
    }

    boolean mouseReleased(double x, double y, int button) {
        boolean dragging = window.dragging();
        if (window.endDrag()) {
            if (dragging) PatternEncodingPreferencesClient.setPreviewPanelPosition(
                    PatternEncodingPreviewPlacement.horizontalPercent(bounds.getX(), bounds.getWidth(), screen.width),
                    PatternEncodingPreviewPlacement.verticalPercent(bounds.getY(), bounds.getHeight(), screen.height));
            pointerOwned = false;
            return true;
        }
        if (!pointerOwned) return false;
        modularUi.getWidget().mouseReleased(x, y, button);
        pointerOwned = false;
        return true;
    }

    boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (window.drag(x, y)) return true;
        if (!pointerOwned) return false;
        modularUi.getWidget().mouseDragged(x, y, button, dragX, dragY);
        return true;
    }

    boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!contains(x, y)) return false;
        modularUi.refreshHoveredElementAtScreen((float) x, (float) y);
        modularUi.getWidget().mouseScrolled(x, y, scrollX, scrollY);
        return true;
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (!visible) return false;
        if (modularUi.getFocusedElement() != null && modularUi.getWidget().keyPressed(key, scan, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    boolean charTyped(char character, int modifiers) {
        return visible && modularUi.getFocusedElement() != null && modularUi.getWidget().charTyped(character, modifiers);
    }

    @Override
    public void upload(long group, long leaf) {
        if (leaf == 0) preview.data_energistics$transferEncodedPatternToProvider(group);
        else preview.data_energistics$transferEncodedPatternToProviderLeaf(group, leaf);
    }

    @Override
    public void rename(long group, long leaf, String name) {
        if (leaf == 0) preview.data_energistics$renamePatternProvider(group, name);
        else preview.data_energistics$renamePatternProviderLeaf(group, leaf, name);
    }

    @Override
    public void open(long group, long leaf) {
        if (leaf == 0) preview.data_energistics$openPatternProviderMenu(group);
        else preview.data_energistics$openPatternProviderLeafMenu(group, leaf);
    }

    private void updateBounds() {
        int x = Math.round(popup.getPositionX());
        int y = Math.round(popup.getPositionY());
        if (x == bounds.getX() && y == bounds.getY() && !exclusions.isEmpty()) return;
        bounds = new Rect2i(x, y, 190, 188);
        exclusions = ObjectImmutableList.of(bounds);
    }

    private void syncPreferences(SyncedPatternProviderList state) {
        var context = ((PatternEncodingPreferenceMenu) menu).data_energistics$getPreferenceSession().rankingContext();
        if (state == preferenceState && Objects.equals(context, preferenceContext)) return;
        ObjectSet<String> digests = new ObjectOpenHashSet<>();
        for (var group : state.providers()) for (var leaf : group.leaves()) digests.add(leaf.providerDigest());
        boolean changed = preferenceState == null || !Objects.equals(context, preferenceContext) || !digests.equals(preferenceDigests);
        preferenceState = state;
        preferenceContext = context;
        preferenceDigests = digests;
        if (changed) PatternEncodingPreferencesClient.sendSnapshot(menu);
    }
}
