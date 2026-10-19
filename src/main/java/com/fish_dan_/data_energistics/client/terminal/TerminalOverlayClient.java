package com.fish_dan_.data_energistics.client.terminal;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalOverlayUi;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.TerminalViewerScreens;
import com.fish_dan_.data_energistics.menu.terminal.TerminalAeMenuLocator;
import com.fish_dan_.data_energistics.network.terminal.TerminalRequestPayload;
import com.fish_dan_.data_energistics.network.terminal.TerminalSnapshotPayload;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.me.crafting.CraftErrorScreen;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.UUID;

/**
 * Independent screen overlay. It never replaces a host menu, its modular UI, or its carried stack.
 */
@NullMarked
public final class TerminalOverlayClient {

    private static @Nullable TerminalOverlayClient current;
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private UUID session = UUID.randomUUID();
    private final TerminalClientPreferences preferences = TerminalClientPreferences.load();
    private @Nullable TerminalOverlayUi composition;
    private @Nullable ModularUI modularUi;
    private CompoundTag lastSnapshot = new CompoundTag();
    private ObjectList<Rect2i> exclusions = ObjectImmutableList.of();
    private long sequence;
    private long appliedSequence = -1;
    private int ticks;
    private int lastRequestTick;
    private long lastGeometryPublication;
    private boolean pendingGeometry;
    private boolean rendering;
    private boolean keyboardOwned;
    private boolean pointerOwned;
    private int pointerButton;
    private boolean suspended;
    private boolean restoring;
    private boolean returnRequested;
    private boolean viewingRecipes;
    private @Nullable AbstractContainerScreen<?> nativeScreen;
    private @Nullable AbstractContainerMenu nativeMenu;
    private int width;
    private int height;
    private double mouseX;
    private double mouseY;

    private TerminalOverlayClient(AbstractContainerScreen<?> screen) {
        this.screen = screen;
        menu = screen.getMenu();
        PacketDistributor.sendToServer(new TerminalRequestPayload(menu.containerId, session, 0, "open", new CompoundTag()));
    }

    /**
     * Applies only authenticated native-menu navigation phases; server confirmation alone restores the saved host.
     */
    public static void navigation(int hostContainerId, UUID sessionId, String phase) {
        TerminalOverlayClient overlay = current;
        if (overlay == null || overlay.menu.containerId != hostContainerId || !overlay.session.equals(sessionId))
            return;
        switch (phase) {
            case "suspend" -> {
                if (overlay.suspended || !overlay.valid()) return;
                overlay.clearInputCapture();
                overlay.suspended = true;
                overlay.returnRequested = false;
                overlay.nativeMenu = null;
                overlay.nativeScreen = null;
            }
            case "resume" -> {
                if (!overlay.suspended) return;
                Minecraft minecraft = Minecraft.getInstance();
                if (!overlay.suspendedHostValid()) {
                    clear();
                    return;
                }
                overlay.restoring = true;
                try {
                    Objects.requireNonNull(minecraft.player).containerMenu = overlay.menu;
                    minecraft.setScreen(overlay.screen);
                    if (minecraft.screen != overlay.screen) {
                        clear();
                        return;
                    }
                    if (overlay.composition != null && overlay.modularUi != null) {
                        overlay.composition.geometry(overlay.screen.width, overlay.screen.height);
                        overlay.modularUi.setScreenAndInit(overlay.screen);
                        overlay.width = overlay.screen.width;
                        overlay.height = overlay.screen.height;
                    }
                    overlay.clearInputCapture();
                    overlay.suspended = false;
                    overlay.returnRequested = false;
                    overlay.nativeMenu = null;
                    overlay.nativeScreen = null;
                    overlay.ticks = 0;
                    overlay.lastRequestTick = 0;
                    overlay.pendingGeometry = false;
                    overlay.publishGeometry(true);
                } finally {
                    overlay.restoring = false;
                }
            }
            case "abort" -> {
                if (overlay.suspended) clear();
            }
            default -> throw new IllegalArgumentException("Unknown terminal navigation phase: " + phase);
        }
    }

    /**
     * Called on the main client thread by the payload handler. Stale sessions cannot affect new screens.
     */
    public static void receive(TerminalSnapshotPayload payload) {
        TerminalOverlayClient overlay = current;
        if (overlay == null || overlay.menu.containerId != payload.containerId() || !overlay.session.equals(payload.sessionId()) ||
                payload.sequence() < overlay.appliedSequence || payload.sequence() > overlay.sequence || !overlay.valid())
            return;
        overlay.appliedSequence = payload.sequence();
        CompoundTag snapshot = payload.snapshot().copy();
        snapshot.remove("sequence");
        if (snapshot.getString("reason").equals("fault")) {
            CompoundTag retained = overlay.lastSnapshot.copy();
            retained.merge(snapshot);
            if (!retained.contains("workspace")) retained.putString("workspace", "crafting");
            snapshot = retained;
        }
        if (!snapshot.getBoolean("enabled")) {
            overlay.releaseUi();
            overlay.lastSnapshot = snapshot.copy();
            return;
        }
        if (overlay.composition == null) {
            overlay.composition = new TerminalOverlayUi(overlay.preferences, overlay::send, TerminalOverlayClient::openLargeTerminal);
            overlay.modularUi = new ModularUI(overlay.composition.ui, Minecraft.getInstance().player);
            overlay.composition.geometry(overlay.screen.width, overlay.screen.height);
            overlay.modularUi.setScreenAndInit(overlay.screen);
            overlay.width = overlay.screen.width;
            overlay.height = overlay.screen.height;
            overlay.composition.apply(snapshot);
            overlay.publishGeometry(true);
        } else if (!snapshot.equals(overlay.lastSnapshot)) {
            overlay.composition.apply(snapshot);
        }
        overlay.lastSnapshot = snapshot.copy();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (current != null && current.suspended) {
            if (!current.suspendedHostValid()) clear();
            return;
        }
        if (current != null && !current.valid()) {
            current.close();
            current = null;
        }
        if (current == null && minecraft.player != null && minecraft.screen instanceof AbstractContainerScreen<?> screen &&
                screen.getMenu() == minecraft.player.containerMenu)
            current = new TerminalOverlayClient(screen);
        if (current != null && ++current.ticks - current.lastRequestTick >= 40) {
            if (current.lastSnapshot.getString("reason").equals("locked")) {
                current.session = UUID.randomUUID();
                current.sequence = 0;
                current.appliedSequence = -1;
                current.lastRequestTick = current.ticks;
                PacketDistributor.sendToServer(new TerminalRequestPayload(current.menu.containerId, current.session, 0, "open", new CompoundTag()));
            } else current.send("refresh", new CompoundTag());
        }
        if (current == null || current.modularUi == null || current.composition == null) return;
        current.modularUi.tick();
        current.composition.tick();
        if (!minecraft.isWindowActive() && current.pointerOwned) {
            current.modularUi.getWidget().mouseReleased(current.mouseX, current.mouseY, current.pointerButton);
            current.composition.endDrag();
            current.composition.cancelStorageInput();
            current.pointerOwned = false;
            current.keyboardOwned = false;
            current.modularUi.clearFocus();
            current.publishGeometry(true);
        }
        if (current.screen.width != current.width || current.screen.height != current.height) {
            current.width = current.screen.width;
            current.height = current.screen.height;
            current.composition.geometry(current.width, current.height);
            current.modularUi.init(current.width, current.height);
            current.publishGeometry(true);
        }
        if (current.pendingGeometry) current.publishGeometry(false);
    }

    public static ObjectList<Rect2i> exclusionAreas(Screen screen) {
        return current != null && !current.suspended && current.screen == screen ? current.exclusions : ObjectImmutableList.of();
    }

    public static ObjectList<TerminalOverlayUi.GhostTarget> ghostTargets(Screen screen) {
        InputTarget target = active(screen);
        return target == null ? ObjectImmutableList.of() : target.composition.ghostTargets();
    }

    public static void drop(Screen screen, TerminalOverlayUi.GhostTarget target, GenericStack stack) {
        InputTarget active = active(screen);
        if (active != null) active.composition.drop(target, stack);
    }

    public static void clear() {
        if (current != null) {
            current.releaseUi();
            current = null;
        }
    }

    private static void openLargeTerminal() {
        // TODO: Bind the mod's large-terminal entry when that feature is implemented; this gesture is reserved for it.
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("gui.data_energistics.player_terminal.large_terminal_unavailable"), true);
    }

    private boolean valid() {
        Minecraft minecraft = Minecraft.getInstance();
        return !suspended && minecraft.player != null && (minecraft.screen == screen || viewingRecipes && TerminalViewerScreens.contains(minecraft.screen)) && minecraft.player.containerMenu == menu;
    }

    public static @Nullable TerminalWorkspace encodingWorkspace(AbstractContainerMenu menu) {
        TerminalOverlayClient overlay = current;
        return overlay != null && overlay.menu == menu && overlay.valid() && overlay.composition != null ? overlay.composition.encodingWorkspace() : null;
    }

    public static void transferRecipe(AbstractContainerMenu menu, CompoundTag data) {
        TerminalOverlayClient overlay = current;
        if (overlay != null && encodingWorkspace(menu) != null) overlay.send("recipe_transfer", data);
    }

    /** Redirects only the player's inventory quick move while the expanded terminal targets its connected network. */
    public static boolean quickMoveToNetwork(int containerId, int slotId, int button, ClickType clickType, Player player) {
        if (clickType != ClickType.QUICK_MOVE || (button != 0 && button != 1)) return false;
        TerminalOverlayClient overlay = current;
        if (overlay == null || !overlay.valid() || overlay.menu.containerId != containerId || overlay.composition == null ||
                !overlay.composition.quickMoveToNetwork() || slotId < 0 || slotId >= overlay.menu.slots.size())
            return false;
        Slot slot = overlay.menu.getSlot(slotId);
        if (!TerminalNetworkStorage.isPlayerInventorySlot(player, slot) || !slot.isActive() || !slot.mayPickup(player)) return false;
        if (!slot.hasItem()) return true;
        if (GenericStack.isWrapped(slot.getItem())) return false;
        CompoundTag data = new CompoundTag();
        data.putInt("slot", slotId);
        data.put("key", Objects.requireNonNull(AEItemKey.of(slot.getItem())).toTagGeneric(player.registryAccess()));
        overlay.send("inventory_insert", data);
        return true;
    }

    private boolean suspendedHostValid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.level == null || minecraft.getConnection() == null)
            return false;
        if (nativeScreen == null) return minecraft.screen == screen && minecraft.player.containerMenu == menu;
        if (minecraft.screen == null && minecraft.player.containerMenu == minecraft.player.inventoryMenu) return true;
        return minecraft.screen == nativeScreen && minecraft.player.containerMenu == nativeMenu;
    }

    private void clearInputCapture() {
        if (modularUi != null) {
            if (pointerOwned) modularUi.getWidget().mouseReleased(-1, -1, pointerButton);
            modularUi.clearFocus();
        }
        if (composition != null) {
            composition.endDrag();
            composition.cancelStorageInput();
        }
        pointerOwned = false;
        keyboardOwned = false;
    }

    private void send(String action, CompoundTag data) {
        if (valid()) {
            lastRequestTick = ticks;
            PacketDistributor.sendToServer(new TerminalRequestPayload(menu.containerId, session, ++sequence, action, data));
        }
    }

    private void close() {
        if (Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new TerminalRequestPayload(menu.containerId, session, ++sequence, "close", new CompoundTag()));
        preferences.save();
        releaseUi();
    }

    private void releaseUi() {
        clearInputCapture();
        if (composition != null) composition.removed();
        if (modularUi != null) modularUi.onRemoved();
        modularUi = null;
        composition = null;
        exclusions = ObjectImmutableList.of();
        keyboardOwned = false;
        pointerOwned = false;
        suspended = false;
        nativeMenu = null;
        nativeScreen = null;
    }

    private void publishGeometry(boolean immediately) {
        if (composition == null) return;
        long now = System.nanoTime();
        if (!immediately && now - lastGeometryPublication < 100_000_000L) {
            pendingGeometry = true;
            return;
        }
        exclusions = new ObjectImmutableList<>(composition.rectangles());
        lastGeometryPublication = now;
        pendingGeometry = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(ScreenEvent.Render.Post event) {
        TerminalOverlayClient overlay = current;
        if (overlay == null || overlay.suspended || event.getScreen() != overlay.screen || overlay.modularUi == null || overlay.composition == null)
            return;
        overlay.mouseX = event.getMouseX();
        overlay.mouseY = event.getMouseY();
        overlay.rendering = true;
        var pose = event.getGuiGraphics().pose();
        pose.pushPose();
        pose.translate(0, 0, 600);
        try {
            overlay.modularUi.getWidget().mouseMoved(event.getMouseX(), event.getMouseY());
            overlay.modularUi.getWidget().render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
            if (overlay.composition.contains(event.getMouseX(), event.getMouseY()) && !overlay.menu.getCarried().isEmpty()) {
                event.getGuiGraphics().renderItem(overlay.menu.getCarried(), event.getMouseX() - 8, event.getMouseY() - 8);
                event.getGuiGraphics().renderItemDecorations(Minecraft.getInstance().font, overlay.menu.getCarried(), event.getMouseX() - 8, event.getMouseY() - 8);
            }
        } finally {
            pose.popPose();
            overlay.rendering = false;
        }
        if (overlay.composition.takeGeometryDirty()) overlay.publishGeometry(!overlay.composition.adjusting());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (Minecraft.getInstance().options.keyInventory.matchesMouse(event.getButton()) && closeNativeScreen(event.getScreen())) {
            event.setCanceled(true);
            return;
        }
        InputTarget target = active(event.getScreen());
        if (target == null) return;
        TerminalOverlayClient overlay = target.overlay;
        overlay.mouseX = event.getMouseX();
        overlay.mouseY = event.getMouseY();
        if (DEKeyMappings.MOVE_PLAYER_TERMINAL_TO_MOUSE.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            moveToMouse(target);
            event.setCanceled(true);
            return;
        }
        if (!target.composition.contains(event.getMouseX(), event.getMouseY())) {
            overlay.keyboardOwned = false;
            target.modularUi.clearFocus();
            return;
        }
        overlay.keyboardOwned = true;
        overlay.pointerOwned = true;
        overlay.pointerButton = event.getButton();
        if (!target.composition.beginDrag(event.getMouseX(), event.getMouseY(), event.getButton())) {
            target.modularUi.refreshHoveredElementAtScreen((float) event.getMouseX(), (float) event.getMouseY());
            target.modularUi.getWidget().mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        InputTarget target = active(event.getScreen());
        if (target == null) return;
        TerminalOverlayClient overlay = target.overlay;
        boolean storageReleased = target.composition.releaseStorage(event.getMouseX(), event.getMouseY(), event.getButton());
        if (target.composition.endDrag()) {
            overlay.pointerOwned = false;
            overlay.publishGeometry(true);
            event.setCanceled(true);
            return;
        }
        if (storageReleased || overlay.pointerOwned || target.composition.contains(event.getMouseX(), event.getMouseY())) {
            target.modularUi.getWidget().mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
            overlay.pointerOwned = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        InputTarget target = active(event.getScreen());
        if (target == null) return;
        TerminalOverlayClient overlay = target.overlay;
        if (target.composition.drag(event.getMouseX(), event.getMouseY())) {
            overlay.publishGeometry(false);
            event.setCanceled(true);
        } else if (overlay.pointerOwned) {
            target.modularUi.getWidget().mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        InputTarget target = active(event.getScreen());
        if (target == null || !target.composition.contains(event.getMouseX(), event.getMouseY()))
            return;
        target.modularUi.getWidget().mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if ((event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(event.getKeyCode(), event.getScanCode())) &&
                closeNativeScreen(event.getScreen())) {
            event.setCanceled(true);
            return;
        }
        InputTarget target = active(event.getScreen());
        if (target == null) return;
        if (DEKeyMappings.MOVE_PLAYER_TERMINAL_TO_MOUSE.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            moveToMouse(target);
            event.setCanceled(true);
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE && target.composition.closePopup()) {
            target.overlay.publishGeometry(true);
            event.setCanceled(true);
            return;
        }
        if (target.modularUi.getFocusedElement() == null && Minecraft.getInstance().options.keyPickItem.matches(event.getKeyCode(), event.getScanCode()) &&
                target.composition.pickStorage(target.overlay.mouseX, target.overlay.mouseY)) {
            event.setCanceled(true);
            return;
        }
        if (target.overlay.keyboardOwned && target.modularUi.getWidget().keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyRelease(ScreenEvent.KeyReleased.Pre event) {
        InputTarget target = active(event.getScreen());
        if (target == null) return;
        if (target.overlay.keyboardOwned && target.modularUi.getWidget().keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        InputTarget target = active(event.getScreen());
        if (target == null) return;
        if (target.overlay.keyboardOwned && target.modularUi.getWidget().charTyped(event.getCodePoint(), event.getModifiers()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tooltip(RenderTooltipEvent.Pre event) {
        TerminalOverlayClient overlay = current;
        if (overlay != null && (overlay.suspended || Minecraft.getInstance().screen != overlay.screen)) return;
        if (overlay != null) {
            Minecraft minecraft = Minecraft.getInstance();
            overlay.mouseX = minecraft.mouseHandler.xpos() * overlay.screen.width / minecraft.getWindow().getScreenWidth();
            overlay.mouseY = minecraft.mouseHandler.ypos() * overlay.screen.height / minecraft.getWindow().getScreenHeight();
        }
        if (overlay != null && overlay.composition != null && !overlay.rendering && overlay.composition.contains(overlay.mouseX, overlay.mouseY))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void opening(ScreenEvent.Opening event) {
        TerminalOverlayClient overlay = current;
        if (overlay != null && !overlay.suspended) {
            overlay.viewingRecipes = TerminalViewerScreens.contains(event.getNewScreen());
            if (overlay.viewingRecipes) overlay.clearInputCapture();
        }
        if (overlay == null || !overlay.suspended || overlay.restoring) return;
        Screen next = event.getNewScreen();
        if (next instanceof AbstractContainerScreen<?> nativeScreen && TerminalAeMenuLocator.matches(nativeScreen.getMenu(), overlay.session) &&
                Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu == nativeScreen.getMenu()) {
            overlay.nativeScreen = nativeScreen;
            overlay.nativeMenu = nativeScreen.getMenu();
            return;
        }
        // A server-side AE2 invalidation sends a close before the authenticated parent-resume phase.
        if (next == null && overlay.nativeScreen != null && Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player.containerMenu == Minecraft.getInstance().player.inventoryMenu)
            return;
        if (next != overlay.nativeScreen) clear();
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (current != null && current.suspended) {
            if (current.restoring || event.getScreen() == current.screen && current.nativeScreen != null ||
                    event.getScreen() instanceof AbstractContainerScreen<?> nativeScreen && TerminalAeMenuLocator.matches(nativeScreen.getMenu(), current.session))
                return;
            clear();
            return;
        }
        if (current != null && current.screen == event.getScreen()) {
            if (current.viewingRecipes) return;
            current.close();
            current = null;
        }
    }

    private static @Nullable InputTarget active(Screen screen) {
        TerminalOverlayClient overlay = current;
        return overlay != null && !overlay.suspended && overlay.screen == screen && overlay.modularUi != null && overlay.composition != null ?
                new InputTarget(overlay, overlay.composition, overlay.modularUi) : null;
    }

    private static void moveToMouse(InputTarget target) {
        Minecraft minecraft = Minecraft.getInstance();
        TerminalOverlayClient overlay = target.overlay;
        overlay.mouseX = minecraft.mouseHandler.xpos() * overlay.screen.width / minecraft.getWindow().getScreenWidth();
        overlay.mouseY = minecraft.mouseHandler.ypos() * overlay.screen.height / minecraft.getWindow().getScreenHeight();
        target.composition.moveToMouse(overlay.mouseX, overlay.mouseY);
        overlay.clearInputCapture();
        overlay.publishGeometry(true);
    }

    /**
     * Captures a complete, initialized UI for one synchronous client input event.
     */
    private record InputTarget(TerminalOverlayClient overlay, TerminalOverlayUi composition, ModularUI modularUi) {}

    private static boolean closeNativeScreen(Screen screen) {
        TerminalOverlayClient overlay = current;
        if (overlay == null || !overlay.suspended || overlay.nativeScreen != screen) return false;
        if (overlay.nativeMenu instanceof CraftingStatusMenu || overlay.nativeMenu instanceof CraftAmountMenu)
            return requestTerminalReturn(screen);
        if ((screen instanceof CraftConfirmScreen || screen instanceof CraftErrorScreen) && overlay.nativeMenu instanceof CraftConfirmMenu confirm) {
            confirm.goBack();
            return true;
        }
        // Existing Trinity and plan-tree screens keep their own popup and return handling.
        return false;
    }

    /**
     * Client-thread Back hook; only this exact native menu visit may restore the saved terminal parent.
     */
    public static boolean requestTerminalReturn(@Nullable Screen screen) {
        if (screen == null) return false;
        TerminalOverlayClient overlay = current;
        if (overlay == null || !overlay.suspended || overlay.nativeScreen != screen || !overlay.suspendedHostValid())
            return false;
        if (!overlay.returnRequested) {
            overlay.returnRequested = true;
            PacketDistributor.sendToServer(new TerminalRequestPayload(Objects.requireNonNull(overlay.nativeMenu).containerId, overlay.session,
                    ++overlay.sequence, "native_return", new CompoundTag()));
        }
        return true;
    }
}
