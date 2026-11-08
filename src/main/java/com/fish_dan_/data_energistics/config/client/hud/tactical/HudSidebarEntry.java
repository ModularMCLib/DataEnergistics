package com.fish_dan_.data_energistics.config.client.hud.tactical;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * A client-local sidebar member, identified by platform slot or information widget rather than screen position.
 * Immutable entries may be retained while hidden; only the client UI edits their owning layout.
 */
public sealed interface HudSidebarEntry permits HudSidebarEntry.Weapon, HudSidebarEntry.Information {

    /** Decodes external slot bounds and excludes sidebar containers from nesting inside themselves. */
    Codec<HudSidebarEntry> CODEC = Codec.either(Codec.intRange(0, 7), TacticalHudWidget.CODEC.validate(widget -> widget.weapons() ? DataResult.error(() -> "A sidebar cannot contain another sidebar") : DataResult.success(widget)))
            .xmap(value -> value.map(Weapon::new, Information::new), entry -> switch (entry) {
                case Weapon(int slot) -> Either.left(slot);
                case Information(TacticalHudWidget widget) -> Either.right(widget);
            });

    /** The zero-based inventory slot, already bounded to 0 through 7 at the snapshot/configuration boundary. */
    record Weapon(int slot) implements HudSidebarEntry {}

    /** A non-container widget, shared with its freely placed presentation and appearance settings. */
    record Information(TacticalHudWidget widget) implements HudSidebarEntry {}
}
