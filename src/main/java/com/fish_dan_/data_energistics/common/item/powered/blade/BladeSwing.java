package com.fish_dan_.data_energistics.common.item.powered.blade;

/** The client and server use the same strike timing for animation and damage. */
public enum BladeSwing {

    FOREHAND(2, 5, 10),
    BACKHAND(2, 5, 10),
    DIAGONAL(2, 5, 10),
    HEAVY(4, 8, 14);

    private final int impactTick;
    private final int repeatTick;
    private final int duration;

    BladeSwing(int impactTick, int repeatTick, int duration) {
        this.impactTick = impactTick;
        this.repeatTick = repeatTick;
        this.duration = duration;
    }

    public int impactTick() {
        return impactTick;
    }

    public int repeatTick() {
        return repeatTick;
    }

    public int duration() {
        return duration;
    }

    BladeSwing next() {
        return switch (this) {
            case FOREHAND -> BACKHAND;
            case BACKHAND -> DIAGONAL;
            case DIAGONAL, HEAVY -> FOREHAND;
        };
    }
}
