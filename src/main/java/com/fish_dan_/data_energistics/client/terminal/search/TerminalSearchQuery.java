package com.fish_dan_.data_energistics.client.terminal.search;

import com.fish_dan_.data_energistics.util.TextSearch;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.util.Platform;

import net.minecraft.tags.TagKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Compiles operators before normalizing individual text operands, retaining JECh's optional client matching.
 */
@NullMarked
public final class TerminalSearchQuery implements Predicate<AEKey> {

    private final ObjectList<ObjectList<Predicate<AEKey>>> alternatives = new ObjectArrayList<>();
    private final Object2ObjectOpenHashMap<AEKey, String> names = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<AEKey, ObjectList<String>> tooltips = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, String> modNames = new Object2ObjectOpenHashMap<>();

    public TerminalSearchQuery(String query) {
        for (String clause : query.toLowerCase(Locale.ROOT).split("\\|", -1)) {
            ObjectList<Predicate<AEKey>> terms = new ObjectArrayList<>();
            for (String term : clause.trim().split("\\s+")) {
                if (!term.isEmpty()) terms.add(term(term));
            }
            if (!terms.isEmpty()) alternatives.add(terms);
        }
    }

    @Override
    public boolean test(AEKey key) {
        if (alternatives.isEmpty()) return true;
        for (ObjectList<Predicate<AEKey>> clause : alternatives) {
            boolean matched = true;
            for (Predicate<AEKey> term : clause) {
                if (!term.test(key)) {
                    matched = false;
                    break;
                }
            }
            if (matched) return true;
        }
        return false;
    }

    /**
     * Cached localized names serve both matching and name sorting for this query's language snapshot.
     */
    public String displayName(AEKey key) {
        return names.computeIfAbsent(key, (AEKey candidate) -> candidate.getDisplayName().getString());
    }

    private Predicate<AEKey> term(String term) {
        char operator = term.charAt(0);
        String operand = "@#*$".indexOf(operator) >= 0 ? term.substring(1) : term;
        String normalized = TextSearch.normalize(operand);
        return switch (operator) {
            case '@' -> key -> key.getModId().toLowerCase(Locale.ROOT).contains(operand) ||
                    TextSearch.matchesNormalized(modNames.computeIfAbsent(key.getModId(), (String id) -> Objects.requireNonNullElse(Platform.getModName(id), id)), normalized);
            case '*' -> key -> key.getId().toString().toLowerCase(Locale.ROOT).contains(operand);
            case '#' -> tagTerm(operand);
            case '$' -> key -> tooltip(key).stream().anyMatch(line -> TextSearch.matchesNormalized(line, normalized));
            default -> key -> key.getId().toString().toLowerCase(Locale.ROOT).contains(operand) ||
                    TextSearch.matchesNormalized(displayName(key), normalized);
        };
    }

    private static Predicate<AEKey> tagTerm(String operand) {
        Reference2ObjectOpenHashMap<AEKeyType, ObjectList<TagKey<?>>> matchingTags = new Reference2ObjectOpenHashMap<>();
        return key -> {
            ObjectList<TagKey<?>> tags = matchingTags.computeIfAbsent(key.getType(), (AEKeyType type) -> type.getTagNames()
                    .filter(tag -> tag.location().toString().toLowerCase(Locale.ROOT).contains(operand))
                    .collect(ObjectArrayList::new, ObjectArrayList::add, ObjectArrayList::addAll));
            for (TagKey<?> tag : tags) if (key.isTagged(tag)) return true;
            return false;
        };
    }

    private ObjectList<String> tooltip(AEKey key) {
        return tooltips.computeIfAbsent(key, (AEKey candidate) -> {
            ObjectList<String> lines = new ObjectArrayList<>();
            for (var line : AEKeyRendering.getTooltip(candidate)) lines.add(line.getString());
            return lines;
        });
    }
}
