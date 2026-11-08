package com.fish_dan_.data_energistics.core.mixins.normal.appeng.helpers;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Provides the server-side interface inventory operations needed by the Data Sanctum interface. */
@Mixin(InterfaceLogic.class)
@NullMarked
public interface InterfaceLogicAccessor {

    /** Refreshes stored pattern inputs before the Data Sanctum checks native work. */
    @Invoker("updateStorage")
    boolean dataEnergistics$invokeUpdateStorage();

    /** Reports whether AE2 has pending work in this interface logic. */
    @Invoker("hasWorkToDo")
    boolean dataEnergistics$invokeHasWorkToDo();

    /** Replaces the native inventory when the Data Sanctum changes its slot capacity. */
    @Accessor("upgrades")
    @Mutable
    void dataEnergistics$setUpgradesField(IUpgradeInventory upgrades);

    /** Notifies AE2 after the Data Sanctum replaces the upgrade inventory. */
    @Invoker("onUpgradesChanged")
    void dataEnergistics$invokeOnUpgradesChanged();
}
