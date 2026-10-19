package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload.PatternUploadActions;
import com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload.PatternUploadWindow;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Attaches an upload child window to the existing encoding screen, never replacing or reopening it. */
public final class PatternEncodingUploadClient implements PatternUploadActions {

    private static @Nullable PatternEncodingUploadClient current;
    private final PatternEncodingTermScreen<?> screen;
    private final PatternEncodingTermMenu menu;
    private final PatternEncodingPreviewMenu preview;
    private @Nullable PatternUploadWindow window;
    private Rect2i anchor;
    private ObjectList<Rect2i> occupied;
    private @Nullable SyncedPatternProviderList preferenceState;
    private @Nullable PatternEncodingRankingContext preferenceContext;
    private ObjectSet<String> preferenceDigests = new ObjectOpenHashSet<>();

    private PatternEncodingUploadClient(PatternEncodingTermScreen<?> screen, Rect2i anchor, ObjectList<Rect2i> occupied) {
        this.screen = screen;
        menu = screen.getMenu();
        preview = (PatternEncodingPreviewMenu) menu;
        this.anchor = anchor;
        this.occupied = occupied;
        PatternEncodingPreferencesClient.initializeMenu(menu);
    }

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PatternEncodingTermScreen<?> screen)
            ((PatternEncodingScreenControls) screen).data_energistics$initializeEncodingControls();
    }

    public static void attach(PatternEncodingTermScreen<?> screen, Rect2i anchor, ObjectList<Rect2i> occupied) {
        if (current != null && current.screen == screen) {
            current.anchor = anchor;
            current.occupied = occupied;
            if (current.window != null && current.window.visible()) current.show();
            return;
        }
        clear();
        current = new PatternEncodingUploadClient(screen, anchor, occupied);
        if (PatternEncodingPreferencesClient.isPreviewPanelPinned() && enabled(screen.getMenu())) current.show();
    }

    /** Left click continues through the real AE button; right click retains the existing encode gesture. */
    public static boolean encodeClicked(PatternEncodingTermMenu menu, int button) {
        PatternEncodingUploadClient upload = current;
        if (upload == null || upload.menu != menu || !enabled(menu) || button < 0 || button > 1) return false;
        if (Screen.hasShiftDown()) {
            upload.hide();
            return true;
        }
        if (upload.window == null || !upload.window.visible()) upload.show();
        if (button == 1) upload.menu.encode();
        return button == 1;
    }

    public static boolean enabled(PatternEncodingTermMenu menu) {
        return ((PatternEncodingSourceAware) menu).data_energistics$isUploadEnabled();
    }

    public static boolean contains(Screen screen, double x, double y) {
        PatternEncodingUploadClient upload = active(screen);
        return upload != null && upload.window != null && upload.window.contains(x, y);
    }

    public static ObjectList<Rect2i> exclusions(Screen screen) {
        PatternEncodingUploadClient upload = active(screen);
        return upload != null && upload.window != null ? upload.window.exclusions() : ObjectImmutableList.of();
    }

    public static void clear() {
        if (current != null && current.window != null) current.window.dispose();
        current = null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        PatternEncodingUploadClient upload = current;
        if (upload == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != upload.screen || minecraft.player == null || minecraft.player.containerMenu != upload.menu) {
            clear();
            return;
        }
        PatternEncodingPreferencesClient.flushDeferredSnapshot(upload.menu);
        if (!enabled(upload.menu)) upload.hide();
        if (upload.window != null && upload.window.visible()) upload.update();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void render(ScreenEvent.Render.Post event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload == null || upload.window == null || !upload.window.visible()) return;
        var graphics = event.getGuiGraphics();
        upload.window.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
        if (upload.window.contains(event.getMouseX(), event.getMouseY()) && !upload.menu.getCarried().isEmpty()) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 1000);
                graphics.renderItem(upload.menu.getCarried(), event.getMouseX() - 8, event.getMouseY() - 8);
                graphics.renderItemDecorations(Minecraft.getInstance().font, upload.menu.getCarried(), event.getMouseX() - 8, event.getMouseY() - 8);
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload == null || upload.window == null || TerminalOverlayClient.contains(event.getScreen(), event.getMouseX(), event.getMouseY())) return;
        if (event.getButton() == 0 && Screen.hasShiftDown() && upload.window.overHandle(event.getMouseX(), event.getMouseY())) {
            PatternEncodingPreferencesClient.setPreviewPanelPinned(upload.menu, !PatternEncodingPreferencesClient.isPreviewPanelPinned());
            event.setCanceled(true);
        } else if (upload.window.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload != null && upload.window != null && upload.window.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload != null && upload.window != null && upload.window.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload != null && upload.window != null && upload.window.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload != null && upload.window != null && upload.window.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload != null && upload.window != null && upload.window.keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        PatternEncodingUploadClient upload = active(event.getScreen());
        if (upload != null && upload.window != null && upload.window.charTyped(event.getCodePoint(), event.getModifiers())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tooltip(RenderTooltipEvent.Pre event) {
        Screen screen = Minecraft.getInstance().screen;
        PatternEncodingUploadClient upload = active(screen);
        if (upload != null && upload.window != null && !TerminalOverlayClient.contains(upload.screen, event.getX(), event.getY()) &&
                upload.window.suppressesTooltip(event.getX(), event.getY()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (active(event.getScreen()) != null) clear();
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

    private void show() {
        if (window == null) {
            window = new PatternUploadWindow(menu.getPlayer(), this, this::hide, geometry -> PatternEncodingPreferencesClient.setPreviewPanelGeometry(
                    geometry.xPercent(), geometry.yPercent(), geometry.width(), geometry.height()));
            PatternEncodingPreferencesClient.previewPanelSize().ifPresent(size -> window.restoreSize(screen, size.width(), size.height()));
        }
        update();
        var saved = PatternEncodingPreferencesClient.previewPanelPosition();
        Rect2i preferred = saved.map(previewPanelPosition -> PatternEncodingPreviewPlacement.fromPercent(previewPanelPosition.xPercent(), previewPanelPosition.yPercent(),
                window.width(), window.height(), screen.width, screen.height)).orElseGet(() -> PatternEncodingPreviewPlacement.findBestBounds(anchor, window.width(), window.height(), anchor.getY(),
                        4, -4, 4, screen.width, screen.height, occupied));
        window.show(screen, preferred);
    }

    private void hide() {
        if (window != null) window.hide();
    }

    private void update() {
        var state = preview.data_energistics$getSyncedPatternProviderState();
        var pattern = preview.data_energistics$getEncodedPatternDefinition();
        if (window != null) window.apply(state, pattern == null ? null : new GenericStack(pattern, 1));
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

    private static @Nullable PatternEncodingUploadClient active(@Nullable Screen screen) {
        return current != null && current.screen == screen ? current : null;
    }
}
