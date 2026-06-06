package com.main.filters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Filter that blocks tracks based on their title and author.
 *
 * Configuration via the {@code BLOCKED_TITLES} env var (or
 * {@code blocked.titles} property) as a comma-separated list of <em>rules</em>.
 * Each rule is one or more tokens joined by {@code +} (AND): a track is
 * blocked when its {@code "title | author"} haystack contains <strong>all</strong>
 * tokens of <strong>any</strong> rule. Matching is case-insensitive.
 *
 * <pre>
 *   roxanne+arizona            -&gt; blocks Roxanne by Arizona Zervas
 *                                 (does NOT block Roxanne by The Police)
 *   despacito,baby shark       -&gt; blocks any title containing those substrings
 *   roxanne+arizona,baby shark -&gt; both rules at once
 * </pre>
 *
 * Empty/unset means nothing is blocked.
 */
public final class TrackFilter {

    /** Each inner list is one rule = AND of its lowercase tokens. */
    private final List<List<String>> rules;

    public TrackFilter(String csv) {
        if (csv == null || csv.isBlank()) {
            this.rules = Collections.emptyList();
            return;
        }
        List<List<String>> parsed = new ArrayList<>();
        for (String rawRule : csv.split(",")) {
            List<String> tokens = new ArrayList<>();
            for (String token : rawRule.split("\\+")) {
                String t = token.trim().toLowerCase();
                if (!t.isEmpty()) tokens.add(t);
            }
            if (!tokens.isEmpty()) parsed.add(List.copyOf(tokens));
        }
        this.rules = List.copyOf(parsed);
    }

    public boolean isBlocked(String title, String author) {
        if (rules.isEmpty()) return false;
        String haystack = ((title == null ? "" : title) + " | "
                         + (author == null ? "" : author)).toLowerCase();
        for (List<String> rule : rules) {
            boolean allMatch = true;
            for (String token : rule) {
                if (!haystack.contains(token)) { allMatch = false; break; }
            }
            if (allMatch) return true;
        }
        return false;
    }

    /** Returns the rules in display form ("token1+token2"). */
    public List<String> blockedTerms() {
        List<String> out = new ArrayList<>(rules.size());
        for (List<String> rule : rules) out.add(String.join("+", rule));
        return Collections.unmodifiableList(out);
    }
}
