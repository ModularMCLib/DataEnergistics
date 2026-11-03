package com.fish_dan_.data_energistics.integration.library.sodium.domain;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Keeps cached command ownership on the render thread; domain membership is resolved for each draw. */
final class DomainDrawRanges {

    private final ObjectArrayList<Section> sections = new ObjectArrayList<>();
    private final ObjectArrayList<Range> ranges = new ObjectArrayList<>();

    void clear() {
        sections.clear();
        ranges.clear();
    }

    void section(int start, long chunk) {
        if (!sections.isEmpty() && sections.getLast().start == start) sections.removeLast();
        if (sections.isEmpty() || sections.getLast().chunk != chunk) sections.add(new Section(start, chunk));
    }

    /** The returned ranges are reused by the next draw and preserve the original translucent order. */
    ObjectList<Range> resolve(int commandCount, Long2ObjectFunction<@Nullable StasisDomain> covering) {
        ranges.clear();
        if (sections.isEmpty() || commandCount == 0) return ranges;

        var first = sections.getFirst();
        int start = first.start;
        long chunk = first.chunk;
        var domain = covering.get(chunk);
        for (int index = 1; index < sections.size(); index++) {
            var section = sections.get(index);
            if (section.start == commandCount) break;
            var nextDomain = covering.get(section.chunk);
            if (!Objects.equals(domain, nextDomain)) {
                ranges.add(new Range(start, section.start - start, chunk, domain));
                start = section.start;
                chunk = section.chunk;
                domain = nextDomain;
            }
        }
        ranges.add(new Range(start, commandCount - start, chunk, domain));
        return ranges;
    }

    private record Section(int start, long chunk) {}

    record Range(int start, int count, long chunk, @Nullable StasisDomain domain) {}
}
