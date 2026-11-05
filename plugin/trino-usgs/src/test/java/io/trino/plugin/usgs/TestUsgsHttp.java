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

import com.fasterxml.jackson.databind.JsonNode;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import io.trino.spi.TrinoException;
import io.trino.testing.TestingConnectorContext;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.trino.plugin.usgs.UsgsErrorCode.USGS_HTTP_ERROR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestUsgsHttp
{
    @Test
    void testResponseSizeLimit()
            throws Exception
    {
        try (MockWebServer server = new MockWebServer();
                JettyHttpClient http = new JettyHttpClient(new HttpClientConfig().setMaxResponseContentLength(DataSize.ofBytes(100)))) {
            server.enqueue(new MockResponse.Builder().body(TestUsgsClient.fixture()).build());
            server.start();
            UsgsClient client = new UsgsClient(
                    http,
                    jsonCodec(JsonNode.class),
                    new UsgsConfig().setFeedUri(URI.create(server.url("/").toString())));
            assertThatThrownBy(() -> client.fetch("day"))
                    .isInstanceOfSatisfying(TrinoException.class, e -> assertThat(e.getErrorCode()).isEqualTo(USGS_HTTP_ERROR.toErrorCode()));
        }
    }

    @Test
    void testTimeout()
            throws Exception
    {
        try (MockWebServer server = new MockWebServer();
                JettyHttpClient http = new JettyHttpClient(new HttpClientConfig().setRequestTimeout(new Duration(100, MILLISECONDS)))) {
            server.enqueue(new MockResponse.Builder().body("{}").headersDelay(1, SECONDS).build());
            server.start();
            UsgsClient client = new UsgsClient(
                    http,
                    jsonCodec(JsonNode.class),
                    new UsgsConfig().setFeedUri(URI.create(server.url("/").toString())));
            assertThatThrownBy(() -> client.fetch("day"))
                    .isInstanceOfSatisfying(TrinoException.class, e -> assertThat(e.getErrorCode()).isEqualTo(USGS_HTTP_ERROR.toErrorCode()));
        }
    }

    @Test
    void testFactoryConfiguration()
    {
        var factory = new UsgsConnectorFactory();
        var context = new TestingConnectorContext();
        factory.create("usgs", Map.of(), context).shutdown();
        assertThatThrownBy(() -> factory.create("usgs", Map.of("usgs.feed-uri", "file:///tmp/"), context))
                .hasMessageContaining("usgs.feed-uri must be");
        assertThatThrownBy(() -> factory.create("usgs", Map.of("usgs.unknown", "value"), context))
                .hasMessageContaining("usgs.unknown");
    }
}
