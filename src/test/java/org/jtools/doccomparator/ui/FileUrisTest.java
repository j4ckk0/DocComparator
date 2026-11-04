package org.jtools.doccomparator.ui;

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
