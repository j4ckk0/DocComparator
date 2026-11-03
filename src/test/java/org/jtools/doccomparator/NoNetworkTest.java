package org.jtools.doccomparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DocComparator never reaches the network (README, "No network"): no class of the program may name what the JDK
 * reaches it with. Read from the compiled classes rather than the sources, so that nothing escapes through an
 * import, a fully qualified name or a method reference. The enforcer rule of pom.xml keeps out the libraries,
 * whose classes this test does not read.
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

    /** Classes elsewhere that reach the network, or hand over to a program that may. */
    private static final List<Pattern> FORBIDDEN_CLASSES = Stream.of(
                    "java/awt/Desktop",           // browse() and mail() start the browser and the mail client
                    "java/lang/ProcessBuilder")   // another program would do what this one may not
            .map(name -> Pattern.compile(Pattern.quote(name) + "(?!\\w)"))
            .toList();

    /** Methods of classes that are otherwise allowed. */
    private static final List<String> FORBIDDEN_METHODS = List.of(
            "java/lang/Runtime.exec");            // as ProcessBuilder

    /** The one exception: NoNetworkResolver, which refuses every lookup, names the types it implements. */
    private static final String RESOLVER = "org/jtools/doccomparator/NoNetworkResolver";
    private static final List<String> RESOLVER_TYPES = List.of(   // the longest first: each is removed from the names
            "java/net/spi/InetAddressResolverProvider$Configuration",
            "java/net/spi/InetAddressResolverProvider",
            "java/net/spi/InetAddressResolver$LookupPolicy",
            "java/net/spi/InetAddressResolver",
            "java/net/UnknownHostException",
            "java/net/InetAddress");

    @Test
    void noClassOfTheProgramReachesTheNetwork() throws IOException {
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(CLASSES)) {
            classFiles = files.filter(file -> file.toString().endsWith(".class")).sorted().toList();
        }
        assertFalse(classFiles.isEmpty(), CLASSES + " holds no class: the test would pass reading nothing");

        List<String> uses = new ArrayList<>();
        for (Path classFile : classFiles) {
            uses.addAll(networkUses(CLASSES.relativize(classFile).toString(), ClassFileNames.read(classFile)));
        }
        assertEquals(List.of(), uses, "what reaches the network (README, \"No network\")");
    }

    /** The check finds each kind of use: a test that cannot fail would prove nothing. */
    @Test
    void theCheckFindsEachWayToTheNetwork() throws IOException {
        Path offender = Path.of("target", "test-classes", "org", "jtools", "doccomparator", "NoNetworkTest$Offender.class");
        String uses = String.join("\n", networkUses("Offender", ClassFileNames.read(offender)));

        assertTrue(uses.contains("java/net/Socket"), uses);
        assertTrue(uses.contains("java/awt/Desktop"), uses);
        assertTrue(uses.contains("calls java/lang/Runtime.exec"), uses);
        assertTrue(uses.contains("javax.net.ssl.SSLSocket"), uses);   // named in a string, for reflection
    }

    /** The resolver may name the types it implements, and nothing else of the network. */
    @Test
    void theResolversExceptionCoversItsOwnTypesOnly() throws IOException {
        Path offender = Path.of("target", "test-classes", "org", "jtools", "doccomparator", "NoNetworkTest$Offender.class");
        String uses = String.join("\n", networkUses(RESOLVER + ".class", ClassFileNames.read(offender)));

        assertTrue(uses.contains("java/net/Socket"), uses);
    }

    /** One line per name of the class that reaches the network, saying which; none when it does not. */
    private static List<String> networkUses(String className, ClassFileNames names) {
        boolean resolver = className.equals(RESOLVER + ".class") || className.startsWith(RESOLVER + "$");
        List<String> uses = new ArrayList<>();
        for (String constant : names.constants()) {
            String asPath = constant.replace('.', '/');   // a class named for reflection is written with dots
            if (resolver) {
                for (String type : RESOLVER_TYPES) {
                    asPath = asPath.replace(type, "");
                }
            }
            String checked = asPath;
            boolean forbidden = FORBIDDEN_PACKAGES.stream().anyMatch(checked::contains)
                    || FORBIDDEN_CLASSES.stream().anyMatch(name -> name.matcher(checked).find());
            if (forbidden) {
                uses.add(className + ": " + constant);
            }
        }
        for (String method : names.calledMethods()) {
            if (FORBIDDEN_METHODS.contains(method)) {
                uses.add(className + ": calls " + method);
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
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(classFile)))) {
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
    }
}
