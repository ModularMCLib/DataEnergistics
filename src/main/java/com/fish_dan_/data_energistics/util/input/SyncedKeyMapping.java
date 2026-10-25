package com.fish_dan_.data_energistics.util.input;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.network.packets.input.KeyStatePayload;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * GTM-style common key registration, synchronization and player/global callbacks. Register on
 * both sides in the same order; callbacks run on their game thread. Server actions must validate
 * the client's input against the player's current equipment and permissions.
 */
public final class SyncedKeyMapping {

    private static final Int2ObjectMap<SyncedKeyMapping> KEY_MAPPINGS = new Int2ObjectLinkedOpenHashMap<>();
    @Getter
    private final int syncId;
    private final WeakHashMap<ServerPlayer, Boolean> serverMapping = new WeakHashMap<>();
    private final WeakHashMap<ServerPlayer, ObjectSet<KeyPressedListener>> playerListeners = new WeakHashMap<>();
    private final ObjectSet<KeyPressedListener> globalListeners = new ObjectLinkedOpenHashSet<>();
    @OnlyIn(Dist.CLIENT)
    private @Nullable KeyChordMapping keyMapping;
    @OnlyIn(Dist.CLIENT)
    private boolean clientDown;
    @OnlyIn(Dist.CLIENT) private boolean blockedUntilRelease;
    private final ObjectList<ClientKeyListener> clientListeners = new ObjectArrayList<>();
    private final ObjectList<Consumer<Minecraft>> clientTicks = new ObjectArrayList<>();
    private final ObjectList<ScreenKeyListener> screenListeners = new ObjectArrayList<>();
    @OnlyIn(Dist.CLIENT)
    private @Nullable Predicate<Minecraft> captureInput;
    @OnlyIn(Dist.CLIENT)
    private static @Nullable LocalPlayer lastPlayer;

    private SyncedKeyMapping(String name, IKeyConflictContext context, int modifiers, boolean mouse, int keyCode, String category) {
        if (Data_Energistics.isClientSide() && !Data_Energistics.isDataGen()) {
            this.keyMapping = (KeyChordMapping) createKeyMapping(name, context, modifiers, mouse, keyCode, category);
        }
        this.syncId = KEY_MAPPINGS.size();
        KEY_MAPPINGS.put(this.syncId, this);
    }

    /** Registers a configurable keyboard key in this mod's controls category, as in GTM. */
    public static SyncedKeyMapping createConfigurable(String name, IKeyConflictContext context, int keyCode) {
        return createConfigurable(name, context, keyCode, "keybind.categories.data_energistics");
    }

    /** The category overload also allows another mod to register a binding in its own category. */
    public static SyncedKeyMapping createConfigurable(String name, IKeyConflictContext context, int keyCode, String category) {
        return new SyncedKeyMapping(name, context, 0, false, keyCode, category);
    }

    public static SyncedKeyMapping createMouse(String name, IKeyConflictContext context, int modifiers, int button) {
        return createMouse(name, context, modifiers, button, "keybind.categories.data_energistics");
    }

    public static SyncedKeyMapping createMouse(String name, IKeyConflictContext context, int modifiers, int button, String category) {
        return new SyncedKeyMapping(name, context, modifiers, true, button, category);
    }

    /** Associates only the options migration name; new declarations use the regular overloads. */
    public SyncedKeyMapping migrateFrom(String legacyName) {
        if (Data_Energistics.isClientSide() && !Data_Energistics.isDataGen()) client().setLegacyName(legacyName);
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    private static Object createKeyMapping(String name, IKeyConflictContext context, int modifiers, boolean mouse, int keyCode, String category) {
        return new KeyChordMapping(name, context, modifiers, mouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM, keyCode, category);
    }

    public static @Nullable SyncedKeyMapping getFromSyncId(int id) {
        return KEY_MAPPINGS.get(id);
    }

    /** Local state; false on a dedicated server. */
    public boolean isKeyDown() {
        return Data_Energistics.isClientSide() && isKeyDownClient();
    }

    public boolean isKeyDown(Player player) {
        return player instanceof ServerPlayer serverPlayer ? this.serverMapping.getOrDefault(serverPlayer, false) : isKeyDown();
    }

    @OnlyIn(Dist.CLIENT)
    private boolean isKeyDownClient() {
        return this.clientDown;
    }

    @OnlyIn(Dist.CLIENT)
    private KeyChordMapping client() {
        if (this.keyMapping == null) throw new IllegalStateException("Key mapping not created: " + this.syncId);
        return this.keyMapping;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean isActiveAndMatches(InputConstants.Key key) {
        return client().isActiveAndMatches(key);
    }

    @OnlyIn(Dist.CLIENT)
    public Component getTranslatedKeyMessage() {
        return client().getTranslatedKeyMessage();
    }

    public SyncedKeyMapping registerGlobalListener(KeyPressedListener listener) {
        this.globalListeners.add(listener);
        return this;
    }

    public void removeGlobalListener(KeyPressedListener listener) {
        this.globalListeners.remove(listener);
    }

    public SyncedKeyMapping registerPlayerListener(ServerPlayer player, KeyPressedListener listener) {
        this.playerListeners.computeIfAbsent(player, ignored -> new ObjectLinkedOpenHashSet<>()).add(listener);
        return this;
    }

    public void removePlayerListener(ServerPlayer player, KeyPressedListener listener) {
        ObjectSet<KeyPressedListener> listeners = this.playerListeners.get(player);
        if (listeners != null && listeners.remove(listener) && listeners.isEmpty()) this.playerListeners.remove(player);
    }

    @OnlyIn(Dist.CLIENT)
    public SyncedKeyMapping registerClientListener(ClientKeyListener listener) {
        this.clientListeners.add(listener);
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    public void removeClientListener(ClientKeyListener listener) {
        this.clientListeners.remove(listener);
    }

    @OnlyIn(Dist.CLIENT)
    public SyncedKeyMapping registerClientTickListener(Consumer<Minecraft> listener) {
        this.clientTicks.add(listener);
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    public SyncedKeyMapping registerScreenListener(ScreenKeyListener listener) {
        this.screenListeners.add(listener);
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    public void removeScreenListener(ScreenKeyListener listener) {
        this.screenListeners.remove(listener);
    }

    @OnlyIn(Dist.CLIENT)
    public SyncedKeyMapping captureInputWhen(Predicate<Minecraft> predicate) {
        this.captureInput = predicate;
        return this;
    }

    /** Called by the validated state packet on the server thread; repeat states are ignored. */
    public void serverActivate(boolean down, ServerPlayer player) {
        boolean previous = this.serverMapping.getOrDefault(player, false);
        if (previous == down) return;
        if (down) this.serverMapping.put(player, true);
        else this.serverMapping.remove(player);
        ObjectSet<KeyPressedListener> listeners = this.playerListeners.get(player);
        if (listeners != null) for (KeyPressedListener listener : new ObjectArrayList<>(listeners)) listener.onKeyPressed(player, this, down);
        for (KeyPressedListener listener : new ObjectArrayList<>(this.globalListeners)) listener.onKeyPressed(player, this, down);
    }

    @OnlyIn(Dist.CLIENT)
    public static void onRegisterKeyBinds(RegisterKeyMappingsEvent event) {
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) event.register(mapping.client());
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(ClientTickEvent.Pre event) {
        sample(null, false);
        Minecraft client = Minecraft.getInstance();
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) {
            for (Consumer<Minecraft> tick : mapping.clientTicks) tick.accept(client);
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_REPEAT) sample(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction() == GLFW.GLFW_PRESS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @OnlyIn(Dist.CLIENT)
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        sample(key, event.getAction() == GLFW.GLFW_PRESS);
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null) return;
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) {
            if (mapping.captureInput != null && mapping.isActiveAndMatches(key) && mapping.captureInput.test(client)) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @OnlyIn(Dist.CLIENT)
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Minecraft client = Minecraft.getInstance();
        double x = client.mouseHandler.xpos() * event.getScreen().width / client.getWindow().getScreenWidth();
        double y = client.mouseHandler.ypos() * event.getScreen().height / client.getWindow().getScreenHeight();
        if (dispatchScreen(event.getScreen(), InputConstants.getKey(event.getKeyCode(), event.getScanCode()), x, y)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @OnlyIn(Dist.CLIENT)
    public static void onScreenMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (dispatchScreen(event.getScreen(), InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getMouseX(), event.getMouseY())) event.setCanceled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean dispatchScreen(Screen screen, InputConstants.Key key, double x, double y) {
        if (screen instanceof KeyBindsScreen) return false;
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) {
            if (!mapping.isActiveAndMatches(key)) continue;
            mapping.blockedUntilRelease = true;
            for (ScreenKeyListener listener : new ObjectArrayList<>(mapping.screenListeners)) {
                if (listener.onKeyPressed(screen, mapping, x, y)) return true;
            }
        }
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    private static void sample(InputConstants.@Nullable Key eventKey, boolean eventDown) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != lastPlayer) {
            releaseClient(client);
            lastPlayer = client.player;
        }
        boolean available = client.player != null && client.getConnection() != null && client.isWindowActive() && !client.isPaused() && client.screen == null;
        Int2BooleanMap changes = new Int2BooleanOpenHashMap();
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) {
            boolean physicallyDown = mapping.client().isPhysicallyDown(eventKey, eventDown);
            if (!physicallyDown) mapping.blockedUntilRelease = false;
            else if (!available) mapping.blockedUntilRelease = true;
            boolean down = available && !mapping.blockedUntilRelease && physicallyDown;
            if (mapping.clientDown == down) continue;
            mapping.clientDown = down;
            changes.put(mapping.syncId, down);
        }
        // Update all states before callbacks so callbacks can inspect other keys from this event.
        for (var entry : changes.int2BooleanEntrySet()) {
            SyncedKeyMapping mapping = KEY_MAPPINGS.get(entry.getIntKey());
            for (ClientKeyListener listener : new ObjectArrayList<>(mapping.clientListeners)) listener.onKeyPressed(client, mapping, entry.getBooleanValue());
        }
        if (!changes.isEmpty() && client.getConnection() != null) PacketDistributor.sendToServer(new KeyStatePayload(changes));
    }

    @OnlyIn(Dist.CLIENT)
    private static void releaseClient(Minecraft client) {
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) {
            if (!mapping.clientDown) continue;
            mapping.clientDown = false;
            for (ClientKeyListener listener : new ObjectArrayList<>(mapping.clientListeners)) listener.onKeyPressed(client, mapping, false);
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        releaseClient(Minecraft.getInstance());
        lastPlayer = null;
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) releasePlayer(player);
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) releasePlayer(player);
    }

    private static void releasePlayer(ServerPlayer player) {
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) {
            mapping.serverActivate(false, player);
            mapping.playerListeners.remove(player);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        for (SyncedKeyMapping mapping : KEY_MAPPINGS.values()) {
            mapping.serverMapping.clear();
            mapping.playerListeners.clear();
        }
    }
}
