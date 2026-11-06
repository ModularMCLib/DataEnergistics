package com.fish_dan_.data_energistics.core.mixin.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires every named mod before this Mixin is considered; read from bytecode without loading the class. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface RequireMods {

    /** Nonempty mod IDs, all of which must be present in the startup mod list. */
    String[] value();
}
