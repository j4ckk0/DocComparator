package org.jtools.doccomparator.compare;

/*-
 * #%L
 * DocComparator
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.List;
import java.util.stream.Collectors;

/**
 * A phrase of the comparison — a property's name, a value it gives — kept as data, which ComparisonWords says in the
 * language in use: a text of the i18n files by its key, with its arguments; a value as the document has it; a
 * property by its name in Word; or several of them one after the other. Two are equal when they say the same in every
 * language. Its toString is the same in every language: keys and Word's names, as the tests read them.
 */
public sealed interface Phrase {

    /** What stands for a value a side does not have: "(none)". */
    Phrase NONE = of("value.none");

    /** The text of this key, its {0}, {1}… these arguments: values as they are, or other phrases. */
    static Phrase of(String key, Object... arguments) {
        return new Text(key, List.of(arguments));
    }

    /** A value as the document has it ("center", "Heading 1"), said the same in every language. */
    static Phrase verbatim(String value) {
        return new Verbatim(value);
    }

    /** A property by its name in Word ("ind.left"), which the view says as a reader does ("indent left"). */
    static Phrase property(String wordName) {
        return new Property(wordName);
    }

    /** These, one after the other. */
    static Phrase joined(Phrase... parts) {
        return new Joined(List.of(parts));
    }

    record Text(String key, List<Object> arguments) implements Phrase {
        @Override
        public String toString() {
            return arguments.isEmpty() ? key
                    : key + arguments.stream().map(String::valueOf).collect(Collectors.joining(", ", "(", ")"));
        }
    }

    record Verbatim(String value) implements Phrase {
        @Override
        public String toString() {
            return value;
        }
    }

    record Property(String wordName) implements Phrase {
        @Override
        public String toString() {
            return wordName;
        }
    }

    record Joined(List<Phrase> parts) implements Phrase {
        @Override
        public String toString() {
            return parts.stream().map(String::valueOf).collect(Collectors.joining());
        }
    }
}
