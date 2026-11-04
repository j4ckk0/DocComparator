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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.net.spi.InetAddressResolverProvider;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/** No lookup is made here: a broken resolver would reach the network the test is meant to keep out. */
class NoNetworkResolverTest {

    private final InetAddressResolver resolver = new NoNetworkResolver().get(null);

    @Test
    void everyNameIsUnknown() {
        LookupPolicy any = LookupPolicy.of(LookupPolicy.IPV4 | LookupPolicy.IPV6);
        assertThrows(UnknownHostException.class, () -> resolver.lookupByName("example.org", any));
        assertThrows(UnknownHostException.class, () -> resolver.lookupByName("localhost", any));
    }

    @Test
    void everyAddressIsUnknown() {
        assertThrows(UnknownHostException.class, () -> resolver.lookupByAddress(new byte[] {127, 0, 0, 1}));
    }

    /** Java finds it where it looks for a resolver: the program's classpath, as the jar's META-INF/services. */
    @Test
    void javaFindsIt() {
        boolean found = ServiceLoader.load(InetAddressResolverProvider.class).stream()
                .anyMatch(provider -> provider.type() == NoNetworkResolver.class);
        assertTrue(found, "META-INF/services/java.net.spi.InetAddressResolverProvider should name NoNetworkResolver");
    }
}
