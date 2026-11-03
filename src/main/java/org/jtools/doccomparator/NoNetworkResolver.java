package org.jtools.doccomparator;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.stream.Stream;

/**
 * The program's name service, which answers nothing: a name lookup reaches the network, even one the JDK makes on its
 * own — on Linux, Swing's fonts look up the machine's name to name their cache file (README, "No network"). Java
 * takes it in place of the system's through META-INF/services/java.net.spi.InetAddressResolverProvider. Every lookup
 * fails as an unknown host, which the JDK's callers expect: the fonts' cache is then named after "localhost".
 */
public final class NoNetworkResolver extends InetAddressResolverProvider {

    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new Refusal();
    }

    @Override
    public String name() {
        return "DocComparator: no network";
    }

    /** The answer to every question: unknown. */
    static final class Refusal implements InetAddressResolver {

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
            throw new UnknownHostException(host + ": DocComparator looks up no name");
        }

        @Override
        public String lookupByAddress(byte[] address) throws UnknownHostException {
            throw new UnknownHostException("DocComparator looks up no address");
        }
    }
}
