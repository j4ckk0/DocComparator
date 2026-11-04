package org.jtools.doccomparator.docx;

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

/**
 * A link from one part of a .docx to another, by id: its type (a URI ending in what it links to — "/styles",
 * "/header"…) and its target, a part name inside the file or, when external, the link as written (a path, a URL),
 * which nothing opens.
 */
record Relationship(String id, String type, String target, boolean external) {

    /** Of this kind: "styles", "header", "officeDocument"… — the end of the type, whichever variant of the format. */
    boolean isA(String kind) {
        return type.endsWith("/" + kind);
    }

    boolean isPart() {
        return !external;
    }
}
