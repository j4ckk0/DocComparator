
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
 * DocComparator, the program: its window around the comparison view (doccomparator-ui), its menus, settings and
 * looks. The distributions link it into their runtime image, with the two other modules, where Java's AOT cache can
 * hold their classes (a jar's are tied to its file's time, which a zip does not keep across time zones). Its jar runs
 * on the class path as well, where this file is not read.
 */
module org.jtools.doccomparator {
    requires org.jtools.doccomparator.ui;
    requires com.formdev.flatlaf;

    // Launcher's package: the module is then resolved whenever the runtime starts, and Launcher loaded from it
    exports org.jtools.doccomparator;

    // no name ever looked up on the network, as on the class path (META-INF/services)
    provides java.net.spi.InetAddressResolverProvider with org.jtools.doccomparator.NoNetworkResolver;

    provides org.jtools.doccomparator.i18n.I18nTexts with org.jtools.doccomparator.texts.AppTexts;
}
