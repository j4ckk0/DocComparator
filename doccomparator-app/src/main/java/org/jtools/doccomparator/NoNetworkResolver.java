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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.stream.Stream;

/**
 * The program's name service, which answers nothing: a name lookup reaches the network, even one the JDK makes on its
 * own — on Linux, Swing's fonts look up the machine's name to name their cache file (README, "No network"). Java
 * takes it in place of the system's, as a service: META-INF/services on the class path, module-info.java in the
 * runtime image. Every lookup fails as an unknown host, which the JDK's callers expect: the fonts' cache is then
 * named after "localhost".
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
