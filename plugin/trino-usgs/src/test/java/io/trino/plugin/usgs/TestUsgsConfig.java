/*
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
package io.trino.plugin.usgs;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static org.assertj.core.api.Assertions.assertThat;

final class TestUsgsConfig
{
    @Test
    void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(UsgsConfig.class)
                .setFeedUri(URI.create("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/")));
    }

    @Test
    void testExplicitPropertyMappings()
    {
        assertFullMapping(
                Map.of("usgs.feed-uri", "http://localhost:1234/feeds/"),
                new UsgsConfig().setFeedUri(URI.create("http://localhost:1234/feeds/")));
    }

    @Test
    void testInvalidUri()
    {
        for (String uri : new String[] {"file:///tmp/", "/relative/", "https://example.com/feeds", "https://user@example.com/", "https://example.com/?query", "https://example.com/#fragment"}) {
            assertThat(new UsgsConfig().setFeedUri(URI.create(uri)).isFeedUriValid()).as(uri).isFalse();
        }
        assertThat(new UsgsConfig().setFeedUri(null).isFeedUriValid()).isFalse();
    }
}
