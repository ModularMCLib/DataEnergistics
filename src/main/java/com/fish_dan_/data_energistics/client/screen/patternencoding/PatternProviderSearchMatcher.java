package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.util.TextSearch;

/** Applies one normalized query independently to each provider search field. */
final class PatternProviderSearchMatcher {

    private PatternProviderSearchMatcher() {}

    static boolean matches(Iterable<String> terms, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        for (String term : terms) {
            if (TextSearch.matchesNormalized(term, normalizedQuery)) {
                return true;
            }
        }
        return false;
    }
}
