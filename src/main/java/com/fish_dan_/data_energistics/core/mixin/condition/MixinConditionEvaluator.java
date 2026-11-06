package com.fish_dan_.data_energistics.core.mixin.condition;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

/** Evaluates only condition metadata from ASM nodes; it never initializes or reflects on a Mixin class. */
public final class MixinConditionEvaluator {

    private static final Logger LOGGER = LogManager.getLogger("DataEnergisticsMixinConditions");
    private static final String REQUIRE_MODS = Type.getDescriptor(RequireMods.class);
    private static final String REQUIRE_VERSION = Type.getDescriptor(RequireModVersion.class);
    private static final String REQUIRE_ENVIRONMENT = Type.getDescriptor(RequireEnvironment.class);
    private static final String CONFLICT = Type.getDescriptor(ConflictWithMod.class);
    private static final String ENVIRONMENT = Type.getDescriptor(RequireEnvironment.Environment.class);

    private final MixinLoadingEnvironment environment;

    public MixinConditionEvaluator(MixinLoadingEnvironment environment) {
        this.environment = environment;
    }

    /** ANDs every known condition. Unreadable declarations allow injection; confirmed exclusions still win. */
    public boolean shouldApply(ClassNode mixinClass) {
        return matchesAnnotations(mixinClass.name, mixinClass.invisibleAnnotations) & matchesAnnotations(mixinClass.name, mixinClass.visibleAnnotations);
    }

    private boolean matchesAnnotations(String className, @Nullable List<AnnotationNode> annotations) {
        if (annotations == null) {
            return true;
        }
        boolean enabled = true;
        for (AnnotationNode annotation : annotations) {
            try {
                enabled &= matches(annotation);
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                LOGGER.warn("Could not evaluate {} on {}; allowing this condition", annotation.desc, className, exception);
            }
        }
        return enabled;
    }

    private boolean matches(AnnotationNode annotation) {
        if (REQUIRE_MODS.equals(annotation.desc)) {
            for (String modId : strings(annotation)) {
                if (!this.environment.modVersions().containsKey(modId)) return false;
            }
        } else if (REQUIRE_VERSION.equals(annotation.desc)) {
            return matchesVersion(annotation);
        } else if (CONFLICT.equals(annotation.desc)) {
            for (String modId : strings(annotation)) {
                if (this.environment.modVersions().containsKey(modId)) return false;
            }
        } else if (REQUIRE_ENVIRONMENT.equals(annotation.desc)) {
            return matchesEnvironment(annotation);
        }
        return true;
    }

    private boolean matchesVersion(AnnotationNode annotation) {
        String modId = string(annotation, "modId", null);
        if (modId.isBlank()) {
            throw invalid(annotation, "modId");
        }
        String min = string(annotation, "min", "");
        String max = string(annotation, "max", "");
        boolean minInclusive = bool(annotation, "minInclusive");
        boolean maxInclusive = bool(annotation, "maxInclusive");
        DefaultArtifactVersion lower = min.isEmpty() ? null : new DefaultArtifactVersion(min);
        DefaultArtifactVersion upper = max.isEmpty() ? null : new DefaultArtifactVersion(max);
        if (lower != null && upper != null) {
            int comparison = lower.compareTo(upper);
            if (comparison > 0 || comparison == 0 && (!minInclusive || !maxInclusive)) {
                throw new IllegalArgumentException("Empty mod version interval in " + annotation.desc);
            }
        }
        if (!this.environment.modVersions().containsKey(modId)) {
            return false;
        }
        String version = this.environment.modVersions().get(modId);
        DefaultArtifactVersion actual = new DefaultArtifactVersion(version);
        if (lower != null) {
            int comparison = actual.compareTo(lower);
            if (comparison < 0 || comparison == 0 && !minInclusive) {
                return false;
            }
        }
        if (upper != null) {
            int comparison = actual.compareTo(upper);
            if (comparison > 0 || comparison == 0 && !maxInclusive) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesEnvironment(AnnotationNode annotation) {
        Object value = value(annotation, "value", null);
        if (!(value instanceof List<?> environments) || environments.isEmpty()) {
            throw invalid(annotation, "value");
        }
        boolean enabled = true;
        for (Object entry : environments) {
            if (!(entry instanceof String[] enumeration) || enumeration.length != 2 || !ENVIRONMENT.equals(enumeration[0])) {
                throw invalid(annotation, "value");
            }
            enabled &= switch (RequireEnvironment.Environment.valueOf(enumeration[1])) {
                case PRODUCTION -> this.environment.production();
                case DEVELOPMENT -> !this.environment.production();
                case DATAGEN -> this.environment.datagen();
            };
        }
        return enabled;
    }

    private static List<String> strings(AnnotationNode annotation) {
        Object value = value(annotation, "value", null);
        if (!(value instanceof List<?> entries) || entries.isEmpty()) {
            throw invalid(annotation, "value");
        }
        ObjectArrayList<String> result = new ObjectArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof String text) || text.isBlank()) {
                throw invalid(annotation, "value");
            }
            result.add(text);
        }
        return result;
    }

    private static String string(AnnotationNode annotation, String name, @Nullable String defaultValue) {
        Object value = value(annotation, name, defaultValue);
        if (!(value instanceof String text)) {
            throw invalid(annotation, name);
        }
        return text;
    }

    private static boolean bool(AnnotationNode annotation, String name) {
        Object value = value(annotation, name, true);
        if (!(value instanceof Boolean result)) {
            throw invalid(annotation, name);
        }
        return result;
    }

    private static @Nullable Object value(AnnotationNode annotation, String name, @Nullable Object defaultValue) {
        if (annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (name.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        return defaultValue;
    }

    private static IllegalArgumentException invalid(AnnotationNode annotation, String name) {
        return new IllegalArgumentException("Invalid Mixin condition member " + annotation.desc + "." + name);
    }
}
