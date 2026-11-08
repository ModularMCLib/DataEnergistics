package com.fish_dan_.data_energistics.client.hud.weapon_platform.information;

import com.fish_dan_.data_energistics.config.client.ClientConfigurations;
import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalPlaytimeHistory;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Client-thread connection time, including menus and pauses, independent of player entities and HUD visibility. */
public final class TacticalPlaytime {

    private static final long CHECKPOINT_NANOS = 60_000_000_000L;
    private static final Object2LongMap<String> ELAPSED_MILLIS = new Object2LongOpenHashMap<>(ClientConfigurations.TACTICAL_HUD.get().playtime().elapsedMillis());
    private static @Nullable Session session;
    private static long checkpointAt;

    private TacticalPlaytime() {}

    /** Starts only on a connection login; a respawn or dimension change does not start a new session. */
    public static void connected(String profile) {
        disconnected();
        long now = System.nanoTime();
        session = new Session(profile, ELAPSED_MILLIS.getLong(profile), now);
        checkpointAt = now;
    }

    /** Flushes the active connection before releasing it; repeated disconnect notifications are harmless. */
    public static void disconnected() {
        if (session == null) return;
        save(session, System.nanoTime());
        session = null;
    }

    /** Called by the client tick before pause and HUD checks, limiting crash loss to the checkpoint interval. */
    public static void tick() {
        if (session == null) return;
        long now = System.nanoTime();
        if (now - checkpointAt < CHECKPOINT_NANOS) return;
        checkpointAt = now;
        save(session, now);
    }

    public static long elapsedSeconds() {
        return session == null ? 0 : session.elapsedMillis(System.nanoTime()) / 1000;
    }

    private static void save(Session current, long now) {
        ELAPSED_MILLIS.put(current.profile(), current.elapsedMillis(now));
        // Retain the in-memory history if persistence fails; JsonConfiguration reports the file error.
        var configuration = ClientConfigurations.TACTICAL_HUD;
        configuration.save(configuration.get().withPlaytime(new TacticalPlaytimeHistory(ELAPSED_MILLIS)));
    }

    private record Session(String profile, long previousMillis, long startedAtNanos) {

        private long elapsedMillis(long now) {
            return previousMillis + (now - startedAtNanos) / 1_000_000L;
        }
    }
}
