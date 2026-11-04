package org.jtools.doccomparator.compare;

import java.util.List;

/** The pairings of one kind of story compared — "Body", "Headers and footers", "Footnotes and endnotes". */
public record StoryComparison(String title, List<Pairing> pairings) {

    public StoryComparison {
        pairings = List.copyOf(pairings);
    }

    /** The pairings that show a difference; a moved paragraph counts once on each side. */
    public long differences() {
        return pairings.stream().filter(Pairing::differs).count();
    }
}
