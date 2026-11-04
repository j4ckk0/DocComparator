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
import org.jtools.doccomparator.i18n.I18n;

/**
 * Something the comparison says — a property's name, a value it gives — kept as data until it is shown, then said in
 * the language in use: a text of the i18n files with its arguments, a value as the document has it, a property by its
 * name in Word, or several of them one after the other. Two are equal when they say the same in every language.
 */
public sealed interface Said {

    /** What stands for a value a side does not have: "(none)". */
    Said NONE = of("value.none");

    /** Says it in the language in use. */
    String say();

    /** The text of this key, its {0}, {1}… these arguments: values as they are, or other things said. */
    static Said of(String key, Object... arguments) {
        return new Text(key, List.of(arguments));
    }

    /** A value as the document has it ("center", "Heading 1"), said the same in every language. */
    static Said verbatim(String value) {
        return new Verbatim(value);
    }

    /** A property by its name in Word ("ind.left"), said as a reader says it (PropertyNames). */
    static Said property(String wordName) {
        return new Property(wordName);
    }

    /** These, one after the other. */
    static Said joined(Said... parts) {
        return new Joined(List.of(parts));
    }

    record Text(String key, List<Object> arguments) implements Said {
        @Override
        public String say() {
            return I18n.text(key, arguments.stream().map(a -> a instanceof Said said ? said.say() : a).toArray());
        }
    }

    record Verbatim(String value) implements Said {
        @Override
        public String say() {
            return value;
        }
    }

    record Property(String wordName) implements Said {
        @Override
        public String say() {
            return PropertyNames.of(wordName);
        }
    }

    record Joined(List<Said> parts) implements Said {
        @Override
        public String say() {
            StringBuilder said = new StringBuilder();
            parts.forEach(part -> said.append(part.say()));
            return said.toString();
        }
    }
}
