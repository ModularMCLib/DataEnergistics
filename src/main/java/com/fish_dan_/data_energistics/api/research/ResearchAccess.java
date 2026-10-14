package com.fish_dan_.data_energistics.api.research;

import com.fish_dan_.data_energistics.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.research.persistence.ResearchSavedData;
import com.fish_dan_.data_energistics.research.team.ResearchTeams;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Server-thread queries for content gates. Item upgrade queries are also safe on the client. */
public final class ResearchAccess {

    private ResearchAccess() {}

    /** Resolves current memberships, including the optional station; null means no station context. */
    public static ObjectSet<UUID> subjects(ServerPlayer player, @Nullable UUID station) {
        ResearchSavedData data = ResearchSavedData.get(player.server);
        ObjectSet<UUID> subjects = new ObjectOpenHashSet<>();
        subjects.add(data.subject("player:" + player.getUUID(), player.getUUID()));
        for (UUID team : ResearchTeams.subjectIds(player)) subjects.add(data.subject("team:" + team, team));
        if (station != null) subjects.add(data.subject("station:" + station, station));
        return subjects;
    }

    /** A stable subject query for machines without an online player; no state is changed. */
    public static boolean isUnlocked(MinecraftServer server, UUID subject, ResourceLocation technology) {
        return ResearchSavedData.get(server).unlocked(subject).contains(technology);
    }

    /** Checks the union of the player's current subjects, optionally including one research station. */
    public static boolean isUnlocked(ServerPlayer player, @Nullable UUID station, ResourceLocation technology) {
        ResearchSavedData data = ResearchSavedData.get(player.server);
        return subjects(player, station).stream().anyMatch(subject -> data.unlocked(subject).contains(technology));
    }

    /** Returns only the upgrades persisted on this item, independent of player/team membership. */
    public static boolean hasUpgrade(ItemStack stack, ResourceLocation upgrade) {
        return ItemResearchUpgrades.of(stack).ids().contains(upgrade);
    }
}
