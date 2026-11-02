package com.fish_dan_.data_energistics.common.plane.model;

public enum AreaPlaneVariant {

    LARGE_FORMATION(3, Operation.FORMATION),
    EXTREMELY_LARGE_FORMATION(5, Operation.FORMATION),
    LARGE_ANNIHILATION(3, Operation.ANNIHILATION),
    EXTREMELY_LARGE_ANNIHILATION(5, Operation.ANNIHILATION);

    private final int width;
    private final Operation operation;

    AreaPlaneVariant(int width, Operation operation) {
        this.width = width;
        this.operation = operation;
    }

    public int width() {
        return width;
    }

    public int slots() {
        return width * width;
    }

    public Operation operation() {
        return operation;
    }

    public enum Operation {
        FORMATION,
        ANNIHILATION
    }
}
