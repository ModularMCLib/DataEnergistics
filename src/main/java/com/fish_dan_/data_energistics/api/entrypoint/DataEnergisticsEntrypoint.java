package com.fish_dan_.data_energistics.api.entrypoint;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one public Data Energistics plugin entrypoint for common or client setup.
 *
 * <p>
 * The annotation carries class-loading prerequisites and the registration phase. A single plugin can register any
 * number of typed
 * extensions through {@link DataEnergisticsPlugin#register(DataEnergisticsRegistry)} after every required mod is
 * known to be loaded.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataEnergisticsEntrypoint {

    /**
     * Selects the client registration phase. The scanner skips these classes before class loading during
     * common setup. Client entries implement DataEnergisticsClientPlugin and run during queued client setup.
     */
    boolean clientOnly() default false;

    /**
     * MOD IDs that must be loaded before the annotated class may be resolved.
     *
     * <p>
     * The scanner reads this value directly from bytecode scan data, so optional integration classes can reference
     * absent-mod types without triggering class loading.
     * </p>
     *
     * @return required loaded mod IDs
     */
    String[] requiredMods() default {};
}
