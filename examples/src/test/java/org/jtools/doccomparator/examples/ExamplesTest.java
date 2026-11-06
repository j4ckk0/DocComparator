package org.jtools.doccomparator.examples;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The examples run as their usage lines say, on the samples: what they print is what a developer will see first.
 * EmbedPanel opens a window, so it is only compiled.
 */
class ExamplesTest {

    /** The samples, seen from this module's folder, where the tests run. */
    private static final Path SAMPLES = Path.of("..", "samples");

    @FunctionalInterface
    private interface Program {
        void run() throws Exception;
    }

    /** What the program prints, System.out held for the time it runs. */
    private static String output(Program program) throws Exception {
        PrintStream console = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            program.run();
        } finally {
            System.setOut(console);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void compareFiles() throws Exception {
        String out = output(() -> CompareFiles.main(new String[] {
                SAMPLES.resolve("procedure-revA.docx").toString(), SAMPLES.resolve("procedure-revB.docx").toString()}));
        assertTrue(out.startsWith("== "), "the parts that differ, each under its heading: " + out);
    }

    @Test
    void compareFilesTheSame() throws Exception {
        String file = SAMPLES.resolve("procedure-revA.docx").toString();
        String out = output(() -> CompareFiles.main(new String[] {file, file}));
        assertTrue(out.strip().equals("The same."), out);
    }

    @Test
    void csvReader(@TempDir Path folder) throws Exception {
        Path before = Files.writeString(folder.resolve("before.csv"), "item,price\npump,120\nvalve,45\n");
        Path after = Files.writeString(folder.resolve("after.csv"), "item,price\npump,125\nvalve,45\n");
        String out = output(() -> CsvReader.main(new String[] {before.toString(), after.toString()}));
        assertTrue(out.contains("120 → 125"), "the cell that changed: " + out);
        assertTrue(out.strip().endsWith("Done."), out);
    }
}
