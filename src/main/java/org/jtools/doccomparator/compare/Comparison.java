package org.jtools.doccomparator.compare;

import java.util.List;
import org.jtools.doccomparator.document.WordDocument;

/**
 * Two documents compared: each kind of story, row by row, and what differs of the documents themselves — template,
 * style definitions, sections — as the options compare them.
 */
public record Comparison(WordDocument left, WordDocument right, CompareOptions options, List<StoryComparison> stories,
                         List<PropertyDifference> documentDifferences) {

    public Comparison {
        stories = List.copyOf(stories);
        documentDifferences = List.copyOf(documentDifferences);
    }

    /** Whether nothing compared differs. */
    public boolean same() {
        return documentDifferences.isEmpty() && stories.stream().allMatch(story -> story.differences() == 0);
    }
}
