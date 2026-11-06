package com.fish_dan_.data_energistics.core.mixin.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires a loaded mod in the specified Maven version interval, without linking any of its classes. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface RequireModVersion {

    /** Mod whose startup version is tested; a missing mod always disables the Mixin. */
    String modId();

    /** Lower bound, or an empty string for no lower bound. */
    String min() default "";

    /** Upper bound, or an empty string for no upper bound. */
    String max() default "";

    /** Whether a version equal to the lower bound is accepted. */
    boolean minInclusive() default true;

    /** Whether a version equal to the upper bound is accepted. */
    boolean maxInclusive() default true;
}
