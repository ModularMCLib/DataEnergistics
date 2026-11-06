package com.fish_dan_.data_energistics.core.mixin.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts a Mixin to startup environments; every declared restriction must hold. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface RequireEnvironment {

    /** Required environments; use DEVELOPMENT and DATAGEN together for development data generation only. */
    Environment[] value();

    /** Startup facts, independent of the physical client/server distribution selected by the Mixin JSON. */
    enum Environment {
        PRODUCTION,
        DEVELOPMENT,
        DATAGEN
    }
}
