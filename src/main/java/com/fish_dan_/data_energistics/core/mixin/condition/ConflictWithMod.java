package com.fish_dan_.data_energistics.core.mixin.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Disables this Mixin if any named mod replaces or conflicts with its target behavior. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ConflictWithMod {

    /** Nonempty mod IDs, none of which may be present at startup. */
    String[] value();
}
