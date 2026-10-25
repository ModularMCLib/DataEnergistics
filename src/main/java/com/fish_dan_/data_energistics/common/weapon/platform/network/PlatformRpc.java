package com.fish_dan_.data_energistics.common.weapon.platform.network;

import com.fish_dan_.data_energistics.client.render.overlay.PlatformOreRenderer;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixReboot;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.OreSurveyDelta;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.ProspectingFilters;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.RadarDelta;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.ShieldVisualSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.WeaponPlatformTicker;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldAppearance;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformHudSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformMenu;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketHandler;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Optional;

/** LDLib2 RPC boundary. Settings are accepted only for the equipped platform's complete menu. */
public final class PlatformRpc {

    public static final String REQUEST = "data_energistics:weapon_platform_request";
    public static final String SNAPSHOT = "data_energistics:weapon_platform_snapshot";
    public static final String RADAR = "data_energistics:weapon_platform_radar";
    public static final String ORES = "data_energistics:weapon_platform_ores";
    public static final String ORE_FILTER = "data_energistics:weapon_platform_ore_filter";
    public static final String SHIELD_APPEARANCE = "data_energistics:weapon_platform_shield_appearance";
    public static final String HUD_SETTINGS = "data_energistics:weapon_platform_hud_settings";
    public static final String SHIELD_VISUAL = "data_energistics:weapon_platform_shield_visual";
    public static final int OPEN = 0, LOCK = 1, ENABLED = 2, AMMUNITION = 3, TARGETS = 4, SELF = 5, TEAM = 6, PROTECTION = 7, ATTACK = 8, HUD = 9;
    public static final int LOCK_TOGGLE = 0, LOCK_SWEEP_START = 1, LOCK_SWEEP_END = 2, LOCK_SWEEP_REMOVE = 3;
    public static final int GUN_INHERIT_TARGETS = 10, GUN_TARGETS = 11;
    public static final int REBOOT = 12;
    public static final int ABILITY = 13, SCAN_RADIUS = 15;
    public static final int INPUT = 16;
    public static final int DOMAIN = 17, DOMAIN_SIZE = 18;
    public static final int FLIGHT_SPEED = 19;
    public static final int PERMANENT_FLIGHT = 20;

    private PlatformRpc() {}

    public static void register() {
        DomainRpc.register();
        RPCPacketDistributor.registerPacket(SHIELD_VISUAL, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> ShieldVisualSnapshot.STREAM_CODEC.encode(buffer, (ShieldVisualSnapshot) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] bytes) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(bytes, buffer -> args[0] = ShieldVisualSnapshot.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) {
                    var snapshot = (ShieldVisualSnapshot) args[0];
                    PlatformClientState.shields.put(snapshot.entity(), snapshot);
                }
            }
        });
        RPCPacketDistributor.registerPacket(ORES, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> OreSurveyDelta.STREAM_CODEC.encode(buffer, (OreSurveyDelta) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] data) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(data, buffer -> args[0] = OreSurveyDelta.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) PlatformOreRenderer.accept((OreSurveyDelta) args[0]);
            }
        });
        RPCPacketDistributor.registerPacket(RADAR, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> RadarDelta.STREAM_CODEC.encode(buffer, (RadarDelta) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] data) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(data, buffer -> args[0] = RadarDelta.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) PlatformClientState.radar((RadarDelta) args[0]);
            }
        });
        RPCPacketDistributor.registerPacket(SNAPSHOT, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> PlatformSnapshot.STREAM_CODEC.encode(buffer, (PlatformSnapshot) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] data) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(data, buffer -> args[0] = PlatformSnapshot.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) PlatformClientState.accept((PlatformSnapshot) args[0]);
            }
        });
    }

    public static void push(ServerPlayer player, PlatformSnapshot snapshot) {
        RPCPacketDistributor.rpcToPlayer(player, SNAPSHOT, snapshot);
    }

    public static void shield(ServerPlayer player) {
        var snapshot = ShieldVisualSnapshot.capture(player);
        RPCPacketDistributor.rpcToTracking(player.serverLevel(), player.chunkPosition(), SHIELD_VISUAL, snapshot);
        RPCPacketDistributor.rpcToPlayer(player, SHIELD_VISUAL, snapshot);
    }

    public static void radar(ServerPlayer player, RadarDelta delta) {
        RPCPacketDistributor.rpcToPlayer(player, RADAR, delta);
    }

    public static void ores(ServerPlayer player, OreSurveyDelta delta) {
        RPCPacketDistributor.rpcToPlayer(player, ORES, delta);
    }

    @RPCPacket(ORE_FILTER)
    public static void oreFilter(RPCSender sender, int index, int operation, String value) {
        ServerPlayer player = sender.asPlayer();
        if (player == null || !WeaponPlatformMenu.isOpen(player) || DomainStasis.frozen(player)) return;
        var runtime = WeaponPlatformTicker.runtime(player);
        if (runtime == null || !ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformAbility.PROSPECTING.research)) return;
        var entries = new ObjectArrayList<>(runtime.platform.getOrDefault(DEDataComponents.PLATFORM_PROSPECTING, ProspectingFilters.DEFAULT).entries());
        if (operation == 0) {
            if (entries.size() == 64) return;
            entries.add(ProspectingFilters.EMPTY);
        } else {
            if (index < 0 || index >= entries.size()) return;
            if (operation == 1) entries.remove(index);
            else {
                if (operation != 2 && operation != 3 || value.length() > 256) return;
                ResourceLocation id = value.isEmpty() ? null : ResourceLocation.tryParse(value);
                if (!value.isEmpty() && id == null) return;
                if (id != null && (operation == 2 ? !BuiltInRegistries.BLOCK.containsKey(id) : BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).isEmpty())) return;
                var previous = entries.get(index);
                if (operation == 2 && previous.tag().isPresent() || operation == 3 && previous.block().isPresent()) return;
                entries.set(index, operation == 2 ? new ProspectingFilters.Entry(Optional.ofNullable(id), Optional.empty()) : new ProspectingFilters.Entry(Optional.empty(), Optional.ofNullable(id)));
            }
        }
        runtime.platform.set(DEDataComponents.PLATFORM_PROSPECTING, new ProspectingFilters(entries));
        push(player, PlatformSnapshot.capture(runtime));
    }

    @RPCPacket(SHIELD_APPEARANCE)
    public static void shieldAppearance(RPCSender sender, int channel, int value) {
        ServerPlayer player = sender.asPlayer();
        if (player == null || DomainStasis.frozen(player)) return;
        var runtime = WeaponPlatformTicker.runtime(player);
        if (runtime == null || !ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformAbility.SHIELD.research)) return;
        var appearance = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD_APPEARANCE, ShieldAppearance.DEFAULT);
        switch (channel) {
            case 0 -> appearance = appearance.toBuilder().color(value).build();
            case 1 -> appearance = appearance.toBuilder().borderColor(value).build();
            case 2 -> {
                if (value < 0 || value > 4) return;
                appearance = appearance.toBuilder().borderWidth(value).build();
            }
            case 3 -> appearance = ShieldAppearance.DEFAULT;
            case 4 -> {
                if (value < 0 || value >= ShieldAppearance.Display.values().length) return;
                appearance = appearance.toBuilder().display(ShieldAppearance.Display.values()[value]).build();
            }
            default -> {
                return;
            }
        }
        runtime.platform.set(DEDataComponents.PLATFORM_SHIELD_APPEARANCE, appearance);
        shield(player);
        push(player, PlatformSnapshot.capture(runtime));
    }

    @RPCPacket(HUD_SETTINGS)
    public static void hudSettings(RPCSender sender, int index, int field, boolean enabled) {
        ServerPlayer player = sender.asPlayer();
        if (player == null || !WeaponPlatformMenu.isOpen(player) || DomainStasis.frozen(player) || index < 0 || index >= HudModule.values().length) return;
        var runtime = WeaponPlatformTicker.runtime(player);
        if (runtime == null || !ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformChipUpgrades.HUD)) return;
        HudModule module = HudModule.values()[index];
        if (field != 0 && (Integer.bitCount(field) != 1 || (field & module.fields) == 0)) return;
        var previous = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_HUD_SETTINGS, PlatformHudSettings.DEFAULT);
        int visible = previous.visible();
        var fields = new IntArrayList(previous.fields());
        if (field == 0) visible = enabled ? visible | 1 << index : visible & ~(1 << index);
        else fields.set(index, enabled ? fields.getInt(index) | field : fields.getInt(index) & ~field);
        runtime.platform.set(DEDataComponents.PLATFORM_HUD_SETTINGS, new PlatformHudSettings(visible, fields));
        push(player, PlatformSnapshot.capture(runtime));
    }

    @RPCPacket(REQUEST)
    public static void request(RPCSender sender, int operation, int slot, int argument) {
        ServerPlayer player = sender.asPlayer();
        if (player == null) return;
        PlatformRuntime runtime = WeaponPlatformTicker.runtime(player);
        if (runtime == null) return;
        if (DomainStasis.frozen(player)) return;
        if (operation == DOMAIN) {
            StasisDomainSavedData.get(player.serverLevel().getServer()).toggle(player);
            push(player, PlatformSnapshot.capture(runtime));
            return;
        }
        if (operation == INPUT) {
            if (argument < 0 || argument > 127) return;
            var motion = player.getData(DEAttachments.PLATFORM_MOTION);
            motion.input = argument;
            motion.inputAt = player.tickCount;
            return;
        }
        if (operation == OPEN) {
            WeaponPlatformMenu.open(player);
            return;
        }
        if (operation == LOCK) {
            switch (argument) {
                case LOCK_TOGGLE -> runtime.toggleLock();
                case LOCK_SWEEP_START, LOCK_SWEEP_REMOVE -> {
                    runtime.sweepingLocks = player.containerMenu == player.inventoryMenu;
                    runtime.removingLocks = argument == LOCK_SWEEP_REMOVE;
                }
                case LOCK_SWEEP_END -> runtime.sweepingLocks = false;
                default -> {
                    return;
                }
            }
        } else {
            if (!WeaponPlatformMenu.isOpen(player) && !(operation == ABILITY && argument == PlatformAbility.PROSPECTING.ordinal())) return;
            if (operation == ABILITY || operation == SCAN_RADIUS || operation == DOMAIN_SIZE || operation == FLIGHT_SPEED || operation == PERMANENT_FLIGHT) {
                var settings = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT);
                var research = ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids();
                if (operation == ABILITY) {
                    if (argument < 0 || argument >= PlatformAbility.values().length) return;
                    PlatformAbility ability = PlatformAbility.values()[argument];
                    if (ability == PlatformAbility.NOCLIP && settings.enabled(ability) && !player.level().noCollision(player, player.getBoundingBox().deflate(0.01))) return;
                    if (!research.contains(ability.research)) return;
                    settings = settings.toBuilder().enabled(settings.enabled() ^ ability.flag).build();
                } else if (operation == FLIGHT_SPEED) {
                    if (argument < 560 || argument > 10000 || !research.contains(PlatformAbility.FLIGHT.research)) return;
                    settings = settings.toBuilder().maximumFlightSpeed(argument / 2000f).build();
                } else if (operation == PERMANENT_FLIGHT) {
                    if (argument < 0 || argument > 1 || !research.contains(PlatformAbility.FLIGHT.research)) return;
                    settings = settings.toBuilder().permanentFlight(argument == 1).build();
                } else if (operation == DOMAIN_SIZE) {
                    if (argument != 3 && argument != 5) return;
                    if (!research.contains(PlatformAbility.DOMAIN.research) || argument == 5 && !research.contains(StasisDomainSavedData.EXPANSION)) return;
                    settings = settings.toBuilder().domainChunks(argument).build();
                } else {
                    if (argument < 16 || argument > 384 || !research.contains(PlatformAbility.PROSPECTING.research)) return;
                    settings = settings.toBuilder().scanRadius(argument).build();
                }
                runtime.platform.set(DEDataComponents.PLATFORM_ABILITIES, settings);
                push(player, PlatformSnapshot.capture(runtime));
                return;
            }
            if (operation == REBOOT) {
                RadixReboot.activate(player);
                push(player, PlatformSnapshot.capture(runtime));
                return;
            }
            if (operation == PROTECTION) runtime.platform.set(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, !runtime.platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
            else if (operation == ATTACK) runtime.platform.set(DEDataComponents.PLATFORM_ATTACK_ENABLED, !runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ATTACK_ENABLED, true));
            else if (operation == HUD) {
                if (!ItemResearchUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformChipUpgrades.HUD)) return;
                runtime.platform.set(DEDataComponents.PLATFORM_HUD_ENABLED, !runtime.platform.getOrDefault(DEDataComponents.PLATFORM_HUD_ENABLED, true));
            } else if (operation == TARGETS) {
                if (argument < 1 || argument > 16 || Integer.bitCount(argument) != 1) return;
                runtime.platform.set(DEDataComponents.PLATFORM_TARGETS, runtime.platform.getOrDefault(DEDataComponents.PLATFORM_TARGETS, GunSettings.HOSTILE) ^ argument);
            } else {
                if (slot < 0 || slot >= runtime.inventory.capacity()) return;
                var gun = runtime.inventory.getStackInSlot(slot);
                if (gun.isEmpty()) return;
                GunSettings settings = gun.getOrDefault(DEDataComponents.GUN_SETTINGS, GunSettings.DEFAULT);
                GunForm form = GunForm.of(gun);
                GunSettings updated;
                switch (operation) {
                    case ENABLED -> updated = settings.toBuilder().enabled(!settings.enabled()).build();
                    case AMMUNITION -> {
                        if (form == GunForm.MEDICAL || argument < 1 || argument > 4) return;
                        GunAmmunition ammunition = GunAmmunition.values()[argument];
                        updated = settings.toBuilder().ammunition(ammunition == settings.ammunition() ? GunAmmunition.AE : ammunition).build();
                    }
                    case SELF -> {
                        if (form != GunForm.MEDICAL) return;
                        updated = settings.toBuilder().supportSelf(!settings.supportSelf()).build();
                    }
                    case TEAM -> {
                        if (form != GunForm.MEDICAL) return;
                        updated = settings.toBuilder().supportTeam(!settings.supportTeam()).build();
                    }
                    case GUN_INHERIT_TARGETS -> {
                        if (form == GunForm.MEDICAL) return;
                        updated = settings.toBuilder().inheritTargets(!settings.inheritTargets())
                                .targetMask(!settings.targetsInitialized() ? runtime.platform.getOrDefault(DEDataComponents.PLATFORM_TARGETS, GunSettings.HOSTILE) : settings.targetMask())
                                .targetsInitialized(true).build();
                    }
                    case GUN_TARGETS -> {
                        if (form == GunForm.MEDICAL || settings.inheritTargets() || argument < 1 || argument > 16 || Integer.bitCount(argument) != 1) return;
                        updated = settings.toBuilder().targetMask(settings.targetMask() ^ argument).build();
                    }
                    default -> {
                        return;
                    }
                }
                gun.set(DEDataComponents.GUN_SETTINGS, updated);
                runtime.inventory.setStackInSlot(slot, gun);
            }
        }
        push(player, PlatformSnapshot.capture(runtime));
    }
}
