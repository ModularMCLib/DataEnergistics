package com.fish_dan_.data_energistics.common.terminal.player.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.util.Platform;

import net.minecraft.tags.TagKey;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Compiles terminal name, mod, identifier and tag terms once per query, without loading AE2 client classes.
 */
final class TerminalResourceSearch {

    private TerminalResourceSearch() {
    }

    static Predicate<AEKey> compile(String query) {
        ObjectList<Predicate<AEKey>> alternatives = new ObjectArrayList<>();
        for (String clause : query.toLowerCase(Locale.ROOT).split("\\|", -1)) {
            ObjectList<Predicate<AEKey>> terms = new ObjectArrayList<>();
            for (String term : clause.trim().split("\\s+")) {
                if (!term.isEmpty()) terms.add(term(term));
            }
            alternatives.add(key -> terms.stream().allMatch(term -> term.test(key)));
        }
        return key -> alternatives.stream().anyMatch(clause -> clause.test(key));
    }

    private static Predicate<AEKey> term(String term) {
        if (term.startsWith("@")) {
            String text = term.substring(1);
            return key -> key.getModId().contains(text) || Platform.getModName(key.getModId()).toLowerCase(Locale.ROOT).contains(text);
        }
        if (term.startsWith("*")) return key -> key.getId().toString().contains(term.substring(1));
        if (term.startsWith("#")) {
            String text = term.substring(1);
            var tags = new Reference2ObjectOpenHashMap<AEKeyType, ObjectList<TagKey<?>>>();
            return key -> {
                if (!tags.containsKey(key.getType())) tags.put(key.getType(), key.getType().getTagNames()
                        .filter(tag -> tag.location().toString().contains(text))
                        .collect(ObjectArrayList::new, ObjectArrayList::add, ObjectArrayList::addAll));
                return tags.get(key.getType()).stream().anyMatch(key::isTagged);
            };
        }
        return key -> key.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(term) || key.getId().toString().contains(term);
    }
}
