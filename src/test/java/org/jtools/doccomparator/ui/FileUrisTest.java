package org.jtools.doccomparator.ui;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileUrisTest {

    @Test
    void aUriListFromALinuxDesktopIsItsFiles() {
        assertEquals(List.of(Path.of("/home/me/My report.docx"), Path.of("/tmp/été.docx")),
                FileUris.paths("file:///home/me/My%20report.docx\r\nfile:///tmp/%C3%A9t%C3%A9.docx\r\n"));
    }

    @Test
    void commentsOtherSchemesAndOtherHostsAreLeftOut() {
        assertEquals(List.of(Path.of("/a.docx"), Path.of("/b.docx")), FileUris.paths("""
                # a comment
                https://example.org/c.docx
                file://fileserver/share/d.docx
                file://localhost/a.docx
                file:/b.docx
                """));
    }

    @Test
    void aBadEscapeIsKeptAsItIs() {
        assertEquals("100%", FileUris.decode("100%"));
        assertEquals("a%zzb", FileUris.decode("a%zzb"));
    }
}
