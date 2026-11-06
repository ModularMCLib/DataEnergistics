package com.fish_dan_.data_energistics.integration.map.ftbchunks.client;

import org.jspecify.annotations.NullMarked;

/** Client-thread drag-state access used to distinguish map selection from dragging. */
@NullMarked
public interface LargeMapScreenAccessor {

    /** Returns the native drag sentinel without changing mouse state. */
    int dataEnergistics$getGrabbed();
}
