package org.jtools.doccomparator.texts;

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

import java.io.IOException;
import java.nio.file.Path;
import org.jtools.doccomparator.testing.TextsCheck;
import org.junit.jupiter.api.Test;

/** The texts of this jar, the program's window's (TextsCheck): those of the jars below it found beside them. */
class AppTextsTest {

    @Test
    void theseTextsAreWhole() throws IOException {
        TextsCheck.check(new AppTexts(), Path.of("src", "main", "java"));
    }
}
