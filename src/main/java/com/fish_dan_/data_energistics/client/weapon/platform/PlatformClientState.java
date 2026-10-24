package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.TacticalHudInteractionScreen;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.TacticalHudLayer;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.RadarDelta;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.TargetSnapshot;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.client.Minecraft;

/** Client-thread snapshot shared by menu-independent HUD controls. Cleared on disconnect and equipment loss. */
public final class PlatformClientState {

    public static PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;
    private static final Int2ObjectLinkedOpenHashMap<TargetSnapshot> RADAR = new Int2ObjectLinkedOpenHashMap<>();
    public static ObjectList<TargetSnapshot> targets = ObjectList.of();

    private PlatformClientState() {}

    public static void accept(PlatformSnapshot value) {
        snapshot = value;
        if (!value.present()) { RADAR.clear(); targets = ObjectList.of(); }
    }

    public static void radar(RadarDelta delta) {
        if (delta.reset()) RADAR.clear();
        for (int id : delta.removed()) RADAR.remove(id);
        for (TargetSnapshot target : delta.changed()) RADAR.put(target.entity(), target);
        targets = new ObjectArrayList<>(RADAR.values());
    }

    public static void openAppearance() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !snapshot.hud()) return;
        client.player.closeContainer();
        TacticalHudLayer.openAppearance();
        client.setScreen(new TacticalHudInteractionScreen());
    }
}
