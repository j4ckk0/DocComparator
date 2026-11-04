/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtools.doccomparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatLaf;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DocComparator never reaches the network (README, "No network"): no class of the program may name what the JDK
 * reaches it with. Read from the compiled classes rather than the sources, so that nothing escapes through an
 * import, a fully qualified name or a method reference. The one library shipped, FlatLaf, is read too; the enforcer
 * rule of pom.xml keeps out any other.
 */
class NoNetworkTest {

    private static final Path CLASSES = Path.of("target", "classes");

    /** The JDK's packages that reach the network, as class files spell them. */
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "java/net/",                              // sockets, URL connections, the HTTP client, name lookups
            "javax/net/",                             // TLS sockets
            "jdk/net/", "sun/net/", "com/sun/net/",   // the JDK's socket options, its internal clients, its HTTP server
            "java/rmi/", "javax/rmi/",                // remote objects
            "javax/naming/",                          // JNDI: LDAP, DNS
            "javax/management/remote/");              // JMX over the network

    /**
     * Classes elsewhere that reach the network, hand over to a program that may, or make an XML parser — which fetches
     * the DTDs and entities a document names unless told not to: XmlCursor makes the program's only one.
     */
    private static final List<Pattern> FORBIDDEN_CLASSES = Stream.of(
                    "java/awt/Desktop",                        // browse() and mail() start the browser and the mail client
                    "java/lang/ProcessBuilder",                // another program would do what this one may not
                    "javax/xml/parsers/DocumentBuilderFactory",
                    "javax/xml/parsers/SAXParserFactory",
                    "org/xml/sax/helpers/XMLReaderFactory",
                    "javax/xml/transform/TransformerFactory",
                    "javax/xml/validation/SchemaFactory",
                    "javax/xml/xpath/XPathFactory",
                    "javax/xml/catalog/CatalogManager")
            .map(name -> Pattern.compile(Pattern.quote(name) + "(?!\\w)"))
            .toList();

    /** Methods of classes that are otherwise allowed. */
    private static final List<String> FORBIDDEN_METHODS = List.of(
            "java/lang/Runtime.exec",                          // as ProcessBuilder
            "java/lang/System.load",                           // native code, which this test cannot read
            "java/lang/System.loadLibrary",
            "java/lang/Runtime.load",
            "java/lang/Runtime.loadLibrary",
            "javax/xml/stream/XMLInputFactory.newInstance",    // an XML parser, as above
            "javax/xml/stream/XMLInputFactory.newFactory",
            "javax/xml/stream/XMLInputFactory.newDefaultFactory");

    /** A class that may name what the rule forbids elsewhere, as its purpose is to keep the network out. */
    private record Exemption(String className, List<String> names, List<String> methods) {

        boolean covers(String classFile) {
            return classFile.equals(className + ".class") || classFile.startsWith(className + "$");
        }
    }

    private static final Exemption NO_EXEMPTION = new Exemption("", List.of(), List.of());

    private static final String RESOLVER = "org/jtools/doccomparator/NoNetworkResolver";

    private static final List<Exemption> EXEMPTIONS = List.of(
            // refuses every name lookup: names the types it implements (the longest first: each is removed in turn)
            new Exemption(RESOLVER, List.of(
                    "java/net/spi/InetAddressResolverProvider$Configuration",
                    "java/net/spi/InetAddressResolverProvider",
                    "java/net/spi/InetAddressResolver$LookupPolicy",
                    "java/net/spi/InetAddressResolver",
                    "java/net/UnknownHostException",
                    "java/net/InetAddress"), List.of()),
            // makes the program's only XML parser, set to fetch nothing
            new Exemption("org/jtools/doccomparator/docx/XmlCursor", List.of(), List.of(
                    "javax/xml/stream/XMLInputFactory.newDefaultFactory")));

    @Test
    void noClassOfTheProgramReachesTheNetwork() throws IOException {
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(CLASSES)) {
            classFiles = files.filter(file -> file.toString().endsWith(".class")).sorted().toList();
        }
        assertFalse(classFiles.isEmpty(), CLASSES + " holds no class: the test would pass reading nothing");

        List<String> uses = new ArrayList<>();
        for (Path classFile : classFiles) {
            String name = CLASSES.relativize(classFile).toString().replace('\\', '/');
            uses.addAll(networkUses(name, ClassFileNames.read(classFile)));
        }
        assertEquals(List.of(), uses, "what reaches the network (README, \"No network\")");
    }

    /**
     * The one library shipped (pom.xml), FlatLaf, read as the program's own classes, every one of them, with two
     * allowances, each the narrowest found to hold:
     * <ul>
     * <li>java.net's URL and URI, their exceptions, and nothing else of java.net: FlatLaf reads its themes and fonts
     * from its own jar, by the URLs Class.getResource gives (UIDefaultsLoader derives a variant's from its theme's,
     * beside it); no socket, no connection, no name looked up.</li>
     * <li>Loading native code, in its two classes that do it only: FlatLaf is told to load none
     * (flatlaf.useNativeLibrary, Looks), and none is shipped (pom.xml).</li>
     * </ul>
     * Another version of it is read here before it can be built: the enforcer rule names this one.
     */
    @Test
    void theLibraryShippedReachesNoNetwork() throws Exception {
        Path jar = Path.of(FlatLaf.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> uses = new ArrayList<>();
        int read = 0;
        try (JarFile classes = new JarFile(jar.toFile())) {
            for (JarEntry entry : Collections.list(classes.entries())) {
                if (entry.getName().endsWith(".class") && !entry.getName().endsWith("module-info.class")) {
                    try (InputStream in = classes.getInputStream(entry)) {
                        uses.addAll(libraryUses(entry.getName(), ClassFileNames.read(in, entry.getName())));
                    }
                    read++;
                }
            }
        }
        assertTrue(read > 300, jar + ": " + read + " classes read: the test would pass reading nothing");
        assertEquals(List.of(), uses, "what of FlatLaf reaches the network (README, \"No network\")");
    }

    /** The library's allowances let nothing else through: a socket is still found. */
    @Test
    void theLibrarysAllowancesCoverWhatTheyNameOnly() throws IOException {
        String uses = String.join("\n", libraryUses("com/formdev/flatlaf/util/NativeLibrary.class",
                ClassFileNames.read(OFFENDER)));
        assertTrue(uses.contains("java/net/Socket"), uses);
        assertTrue(uses.contains("calls java/lang/Runtime.exec"), uses);
    }

    /** java.net's names FlatLaf may use: those of a resource's address, nothing that connects. */
    private static final Pattern LIBRARY_NET_NAMES =
            Pattern.compile("java/net/(URL|URI|URISyntaxException|MalformedURLException)(?![\\w$])");

    /** FlatLaf's classes that load native code, which it is told not to. */
    private static final List<String> LIBRARY_NATIVE_LOADERS = List.of(
            "com/formdev/flatlaf/util/NativeLibrary", "com/formdev/flatlaf/ui/FlatNativeLibrary");

    private static final List<String> NATIVE_LOADING = List.of("java/lang/System.load", "java/lang/System.loadLibrary",
            "java/lang/Runtime.load", "java/lang/Runtime.loadLibrary");

    /** As networkUses, the library's allowances made. */
    private static List<String> libraryUses(String classFile, ClassFileNames names) {
        List<String> constants = names.constants().stream()
                .map(constant -> LIBRARY_NET_NAMES.matcher(constant.replace('.', '/')).replaceAll(""))
                .toList();
        boolean loadsNatives = LIBRARY_NATIVE_LOADERS.stream().anyMatch(loader -> classFile.equals(loader + ".class")
                || classFile.startsWith(loader + "$") || classFile.endsWith("/" + loader + ".class"));
        List<String> methods = names.calledMethods().stream()
                .filter(method -> !(loadsNatives && NATIVE_LOADING.contains(method)))
                .toList();
        return networkUses(classFile, new ClassFileNames(constants, methods));
    }

    /** The check finds each kind of use: a test that cannot fail would prove nothing. */
    @Test
    void theCheckFindsEachWayToTheNetwork() throws IOException {
        String uses = String.join("\n", networkUses("Offender", ClassFileNames.read(OFFENDER)));

        assertTrue(uses.contains("java/net/Socket"), uses);
        assertTrue(uses.contains("java/awt/Desktop"), uses);
        assertTrue(uses.contains("calls java/lang/Runtime.exec"), uses);
        assertTrue(uses.contains("javax.net.ssl.SSLSocket"), uses);   // named in a string, for reflection
        assertTrue(uses.contains("javax/xml/parsers/DocumentBuilderFactory"), uses);
        assertTrue(uses.contains("calls javax/xml/stream/XMLInputFactory.newInstance"), uses);
    }

    /** An exemption covers what it names, and nothing else of the network. */
    @Test
    void anExemptionCoversWhatItNamesOnly() throws IOException {
        String asResolver = String.join("\n", networkUses(RESOLVER + ".class", ClassFileNames.read(OFFENDER)));
        assertTrue(asResolver.contains("java/net/Socket"), asResolver);

        String asXmlCursor = String.join("\n",
                networkUses("org/jtools/doccomparator/docx/XmlCursor.class", ClassFileNames.read(OFFENDER)));
        assertTrue(asXmlCursor.contains("calls javax/xml/stream/XMLInputFactory.newInstance"), asXmlCursor);
    }

    private static final Path OFFENDER =
            Path.of("target", "test-classes", "org", "jtools", "doccomparator", "NoNetworkTest$Offender.class");

    /** One line per name of the class that reaches the network, saying which; none when it does not. */
    private static List<String> networkUses(String classFile, ClassFileNames names) {
        Exemption exemption = EXEMPTIONS.stream().filter(e -> e.covers(classFile)).findFirst().orElse(NO_EXEMPTION);
        List<String> uses = new ArrayList<>();
        for (String constant : names.constants()) {
            String asPath = constant.replace('.', '/');   // a class named for reflection is written with dots
            for (String exempted : exemption.names()) {
                asPath = asPath.replace(exempted, "");
            }
            String checked = asPath;
            boolean forbidden = FORBIDDEN_PACKAGES.stream().anyMatch(checked::contains)
                    || FORBIDDEN_CLASSES.stream().anyMatch(name -> name.matcher(checked).find());
            if (forbidden) {
                uses.add(classFile + ": " + constant);
            }
        }
        for (String method : names.calledMethods()) {
            if (FORBIDDEN_METHODS.contains(method) && !exemption.methods().contains(method)) {
                uses.add(classFile + ": calls " + method);
            }
        }
        return uses;
    }

    /**
     * What a class file names, from its constant pool: every UTF-8 constant (the classes it uses, the descriptors
     * of its fields and methods, its string literals) and the methods it calls, as "owner.name".
     */
    private record ClassFileNames(List<String> constants, List<String> calledMethods) {

        static ClassFileNames read(Path classFile) throws IOException {
            try (InputStream in = Files.newInputStream(classFile)) {
                return read(in, classFile.toString());
            }
        }

        static ClassFileNames read(InputStream stream, String classFile) throws IOException {
            {
                DataInputStream in = new DataInputStream(new BufferedInputStream(stream));
                if (in.readInt() != 0xCAFEBABE) {
                    throw new IOException(classFile + ": not a class file");
                }
                in.readUnsignedShort();   // minor version
                in.readUnsignedShort();   // major version
                int count = in.readUnsignedShort();
                String[] utf8 = new String[count];
                int[] classNameIndex = new int[count];
                int[] nameAndTypeNameIndex = new int[count];
                List<int[]> methodRefs = new ArrayList<>();   // its class's index, its name-and-type's index
                for (int i = 1; i < count; i++) {
                    int tag = in.readUnsignedByte();
                    switch (tag) {
                        case 1 -> utf8[i] = in.readUTF();   // the class file's modified UTF-8 is DataInput's
                        case 7 -> classNameIndex[i] = in.readUnsignedShort();
                        case 10, 11 -> methodRefs.add(new int[] {in.readUnsignedShort(), in.readUnsignedShort()});
                        case 12 -> {
                            nameAndTypeNameIndex[i] = in.readUnsignedShort();
                            in.readUnsignedShort();
                        }
                        case 8, 16, 19, 20 -> in.readUnsignedShort();
                        case 15 -> {
                            in.readUnsignedByte();
                            in.readUnsignedShort();
                        }
                        case 3, 4, 9, 17, 18 -> in.readInt();
                        case 5, 6 -> {
                            in.readLong();
                            i++;   // a long or a double takes two entries
                        }
                        default -> throw new IOException(classFile + ": unknown constant tag " + tag);
                    }
                }
                List<String> constants = Stream.of(utf8).filter(s -> s != null).toList();
                List<String> calledMethods = methodRefs.stream()
                        .map(ref -> utf8[classNameIndex[ref[0]]] + "." + utf8[nameAndTypeNameIndex[ref[1]]])
                        .toList();
                return new ClassFileNames(constants, calledMethods);
            }
        }
    }

    /** Each thing the program may not do, for theCheckFindsEachWayToTheNetwork to find. Never run. */
    @SuppressWarnings("unused")
    private static final class Offender {

        void connect() throws IOException {
            new java.net.Socket("localhost", 1).close();
        }

        void browse() throws Exception {
            java.awt.Desktop.getDesktop().open(null);
        }

        void exec() throws IOException {
            Runtime.getRuntime().exec(new String[] {"true"});
        }

        void reflect() throws ClassNotFoundException {
            Class.forName("javax.net.ssl.SSLSocket");
        }

        void parse() throws Exception {
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.stream.XMLInputFactory.newInstance();
        }
    }
}
