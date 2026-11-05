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
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.testing.TestingHttpClient;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.RecordCursor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.trino.plugin.usgs.UsgsErrorCode.USGS_HTTP_ERROR;
import static io.trino.plugin.usgs.UsgsErrorCode.USGS_INVALID_RESPONSE;
import static io.trino.spi.type.DateTimeEncoding.unpackMillisUtc;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestUsgsClient
{
    static String fixture()
            throws IOException
    {
        try (var stream = requireNonNull(TestUsgsClient.class.getResourceAsStream("/earthquakes.geojson"))) {
            return new String(stream.readAllBytes(), UTF_8);
        }
    }

    @Test
    void testRowsAndProjection()
            throws Exception
    {
        String body = fixture();
        try (var httpClient = new TestingHttpClient(request -> {
            assertThat(request.getUri()).isEqualTo(URI.create("https://example.com/feeds/all_week.geojson"));
            return mockResponse(HttpStatus.OK, JSON_UTF_8, body);
        })) {
            UsgsClient client = client(httpClient);
            List<List<Object>> rows = client.fetch("week");
            assertThat(rows).hasSize(3);
            assertThat(rows.getFirst().get(0)).isEqualTo("strong");
            assertThat(rows.getFirst().get(5)).isEqualTo(-155.5);
            assertThat(rows.getFirst().get(6)).isEqualTo(19.25);
            assertThat(rows.getFirst().get(7)).isEqualTo(10.75);
            assertThat(unpackMillisUtc((Long) rows.getFirst().get(3))).isEqualTo(1700000000123L);
            assertThat(rows.getFirst().get(11)).isEqualTo(true);
            assertThat(rows.get(1).get(11)).isEqualTo(false);
            assertThat(rows.get(2).subList(1, 15)).containsOnlyNulls();

            var provider = new UsgsRecordSetProvider(client);
            try (RecordCursor cursor = provider.getRecordSet(
                    null,
                    null,
                    new UsgsSplit("week"),
                    new UsgsTableHandle("week"),
                    List.of(new UsgsColumnHandle(2), new UsgsColumnHandle(1), new UsgsColumnHandle(2))).cursor()) {
                assertThat(cursor.advanceNextPosition()).isTrue();
                assertThat(cursor.getSlice(0).toStringUtf8()).isEqualTo("Near Hawaiʻi");
                assertThat(cursor.getDouble(1)).isEqualTo(6.2);
                assertThat(cursor.getSlice(2)).isEqualTo(cursor.getSlice(0));
            }
            try (RecordCursor cursor = provider.getRecordSet(null, null, new UsgsSplit("week"), new UsgsTableHandle("week"), List.of()).cursor()) {
                int count = 0;
                while (cursor.advanceNextPosition()) {
                    count++;
                }
                assertThat(count).isEqualTo(3);
            }
        }
    }

    @Test
    void testEmptyFeed()
    {
        try (var httpClient = new TestingHttpClient(_ -> mockResponse(HttpStatus.OK, JSON_UTF_8,
                """
                {"type":"FeatureCollection","features":[]}
                """))) {
            assertThat(client(httpClient).fetch("day")).isEmpty();
        }
    }

    @Test
    void testHttpFailure()
    {
        for (HttpStatus status : List.of(HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE)) {
            try (var httpClient = new TestingHttpClient(_ -> mockResponse(status, JSON_UTF_8, "upstream failed"))) {
                assertThatThrownBy(() -> client(httpClient).fetch("day"))
                        .isInstanceOfSatisfying(TrinoException.class, e -> assertThat(e.getErrorCode()).isEqualTo(USGS_HTTP_ERROR.toErrorCode()))
                        .hasMessageContaining("HTTP " + status.code());
            }
        }
        try (var httpClient = new TestingHttpClient(_ -> { throw new RuntimeException("connection failed"); })) {
            assertThatThrownBy(() -> client(httpClient).fetch("hour"))
                    .isInstanceOfSatisfying(TrinoException.class, e -> assertThat(e.getErrorCode()).isEqualTo(USGS_HTTP_ERROR.toErrorCode()));
        }
    }

    @Test
    void testMalformedFeed()
            throws Exception
    {
        for (String body : List.of(
                "not json",
                "{}",
                "{\"type\":\"FeatureCollection\"}",
                "{\"type\":\"FeatureCollection\",\"features\":[{}]}",
                fixture().replace("\"mag\":6.2", "\"mag\":\"bad\""),
                fixture().replace("-155.5,19.25,10.75", "-155.5,19.25"),
                fixture().replace("\"tsunami\":1", "\"tsunami\":2"),
                fixture().replace("1700000000123", "9223372036854775807"))) {
            try (var httpClient = new TestingHttpClient(_ -> mockResponse(HttpStatus.OK, JSON_UTF_8, body))) {
                assertThatThrownBy(() -> client(httpClient).fetch("month"))
                        .as(body)
                        .isInstanceOfSatisfying(TrinoException.class, e -> assertThat(e.getErrorCode()).isEqualTo(USGS_INVALID_RESPONSE.toErrorCode()));
            }
        }
    }

    private static UsgsClient client(TestingHttpClient httpClient)
    {
        return new UsgsClient(httpClient, jsonCodec(JsonNode.class), new UsgsConfig().setFeedUri(URI.create("https://example.com/feeds/")));
    }
}
