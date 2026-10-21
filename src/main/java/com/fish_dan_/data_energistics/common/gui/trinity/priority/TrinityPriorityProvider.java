package com.fish_dan_.data_energistics.common.gui.trinity.priority;

import com.fish_dan_.data_energistics.api.gui.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.api.gui.host.window.HostSubUi;
import com.fish_dan_.data_energistics.api.gui.host.window.HostSubUiContext;
import com.fish_dan_.data_energistics.api.gui.host.window.HostSubUiProvider;
import com.fish_dan_.data_energistics.api.gui.host.window.HostSubUiRoot;
import com.fish_dan_.data_energistics.api.gui.priority.PriorityControl;
import com.fish_dan_.data_energistics.common.gui.trinity.layout.TrinityUiNbtLayouts;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.LongPredicate;

/**
 * Adapts the reusable priority editor to one generation-aware Trinity hosted action.
 */
@ApiStatus.Internal
public final class TrinityPriorityProvider implements HostSubUiProvider {

    private final HostUiKey key;
    private final String windowId;
    private final Component title;
    private final Component firstHint;
    private final Component secondHint;
    private final IDataProvider<Integer> priority;
    private final IntSupplier modifierMask;
    private final BiPredicate<Long, PriorityControl.Operation> submit;
    private final LongPredicate pending;

    public TrinityPriorityProvider(HostUiKey key,
                                   String windowId,
                                   Component title,
                                   Component firstHint,
                                   Component secondHint,
                                   IDataProvider<Integer> priority,
                                   IntSupplier modifierMask,
                                   BiPredicate<Long, PriorityControl.Operation> submit,
                                   LongPredicate pending) {
        if (windowId.isBlank()) {
            throw new IllegalArgumentException("Trinity priority window id must not be blank");
        }
        this.key = key;
        this.windowId = windowId;
        this.title = title;
        this.firstHint = firstHint;
        this.secondHint = secondHint;
        this.priority = priority;
        this.modifierMask = modifierMask;
        this.submit = submit;
        this.pending = pending;
    }

    @Override
    public HostUiKey key() {
        return this.key;
    }

    @Override
    public HostSubUi create(HostSubUiContext context) {
        if (!this.key.equals(context.key())) {
            throw new IllegalArgumentException("Trinity priority provider received the wrong host context");
        }
        HostSubUiRoot root = context.createRoot();
        TrinityUiNbtLayouts.init("priority", root);
        PriorityControl.builder(root, this.windowId)
                .labels(this.title, this.firstHint, this.secondHint)
                .state(
                        () -> this.priority.getValue(),
                        this.modifierMask,
                        context::canSendServerAction,
                        () -> this.pending.test(context.generation()))
                .actions(
                        operation -> this.submit.test(context.generation(), operation),
                        context::requestClose)
                .build();
        return new HostSubUi(root, root);
    }
}
