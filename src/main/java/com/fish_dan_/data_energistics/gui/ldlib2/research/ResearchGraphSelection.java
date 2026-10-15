package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.WeakHashMap;

/** Client-thread selection memory per viewer and station; weak viewer keys release disconnected sessions. */
final class ResearchGraphSelection {

    private static final WeakHashMap<Player, Object2ObjectMap<GlobalPos, ResearchGraphSelection>> VIEWERS = new WeakHashMap<>();
    private final Object2ObjectMap<ResearchScope, ResourceLocation> selectedNodes = new Object2ObjectArrayMap<>();
    private ResearchScope scope = ResearchScope.TECHNOLOGY;

    static ResearchGraphSelection forMenu(ResearchStationMenu menu) {
        if (menu.isServerSide()) return new ResearchGraphSelection();
        Player player = menu.getPlayer();
        GlobalPos station = GlobalPos.of(player.level().dimension(), menu.host().getBlockPos());
        return VIEWERS.computeIfAbsent(player, ignored -> new Object2ObjectOpenHashMap<>())
                .computeIfAbsent(station, ignored -> new ResearchGraphSelection());
    }

    ResearchScope scope() {
        return scope;
    }

    void scope(ResearchScope scope) {
        this.scope = scope;
    }

    @Nullable
    ResourceLocation selected() {
        return selectedNodes.containsKey(scope) ? selectedNodes.get(scope) : null;
    }

    void select(@Nullable ResourceLocation id) {
        if (id == null) selectedNodes.remove(scope);
        else selectedNodes.put(scope, id);
    }
}
