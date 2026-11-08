package com.fish_dan_.data_energistics.client.hud.weapon_platform.information;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One connection session, unaffected by the lifetime of a player entity, dimension or HUD screen. */
public final class TacticalHudClock {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static long connectedAt;

    private TacticalHudClock() {}

    public static void connected() {
        connectedAt = System.nanoTime();
    }

    public static void disconnected() {
        connectedAt = 0;
    }

    public static String realTime() {
        return LocalTime.now().format(CLOCK);
    }

    public static String gameTime(long dayTime) {
        int minutes = Math.floorMod(dayTime, 24000) * 1440 / 24000;
        return String.format(Locale.ROOT, "%02d:%02d", (minutes / 60 + 6) % 24, minutes % 60);
    }

    public static String sessionTime() {
        long minutes = connectedAt == 0 ? 0 : (System.nanoTime() - connectedAt) / 60_000_000_000L;
        return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60);
    }
}
