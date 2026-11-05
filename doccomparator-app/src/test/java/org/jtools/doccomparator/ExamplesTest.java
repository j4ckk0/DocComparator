package org.jtools.doccomparator;

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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The examples of the libraries (examples/) still compile against them: they are what a newcomer copies first. */
class ExamplesTest {

    @Test
    void everyExampleCompiles(@TempDir Path out) throws IOException {
        List<String> sources;
        try (Stream<Path> files = Files.list(Path.of("..", "examples"))) {
            sources = files.filter(file -> file.toString().endsWith(".java")).map(Path::toString).sorted().toList();
        }
        assertFalse(sources.isEmpty(), "no example found");
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new ArrayList<>(List.of("-proc:none", "-d", out.toString(), "-cp",
                System.getProperty("java.class.path")));
        arguments.addAll(sources);
        int status = javac.run(null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, status, "the examples do not compile against the libraries: see the errors above");
    }
}
