package com.fish_dan_.data_energistics.client.hud.weapon_platform.information;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One connection session, unaffected by the lifetime of a player entity, dimension or HUD screen. */
public final class TacticalHudClock {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
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
        // Tick zero is 06:00 on the first date; the calendar rolls over at midnight.
        int ticks = Math.floorMod(dayTime, 24000) + 6000;
        long days = Math.floorDiv(dayTime, 24000) + ticks / 24000;
        int dayOfYear = Math.floorMod(days, 360);
        int minutes = ticks % 24000 * 1440 / 24000;
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d", Math.floorDiv(days, 360) + 1,
                dayOfYear / 30 + 1, dayOfYear % 30 + 1, minutes / 60, minutes % 60);
    }

    public static String sessionTime() {
        long seconds = connectedAt == 0 ? 0 : (System.nanoTime() - connectedAt) / 1_000_000_000L;
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
