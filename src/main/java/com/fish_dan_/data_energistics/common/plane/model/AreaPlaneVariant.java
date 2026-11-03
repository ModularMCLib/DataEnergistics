package com.fish_dan_.data_energistics.common.plane.model;

import lombok.Getter;

@Getter
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

    public int getFaceCellCount() {
        return width * width;
    }

    public int getWorkingCellCount() {
        return width * width * width;
    }

    public int getConfigurationSlots() {
        return operation == Operation.FORMATION ? getWorkingCellCount() : getFaceCellCount();
    }

    public enum Operation {
        FORMATION,
        ANNIHILATION
    }
}
