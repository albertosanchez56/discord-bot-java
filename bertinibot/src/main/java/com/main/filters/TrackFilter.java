package com.main.filters;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Filter that blocks track titles containing any of the configured terms.
 *
 * Terms are case-insensitive and matched with {@code contains}. Configure via
 * the {@code BLOCKED_TITLES} env var (comma-separated). Empty/unset means
 * nothing is blocked.
 */
public final class TrackFilter {

    private final List<String> blockedLower;

    public TrackFilter(String csv) {
        if (csv == null || csv.isBlank()) {
            this.blockedLower = Collections.emptyList();
        } else {
            this.blockedLower = Stream.of(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .toList();
        }
    }

    public boolean isBlocked(String title) {
        if (title == null || blockedLower.isEmpty()) return false;
        String lower = title.toLowerCase();
        for (String term : blockedLower) {
            if (lower.contains(term)) return true;
        }
        return false;
    }

    public List<String> blockedTerms() {
        return Collections.unmodifiableList(blockedLower);
    }
}
