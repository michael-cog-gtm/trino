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
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.json.JsonCodec;
import io.trino.plugin.usgs.UsgsModule.ForUsgs;
import io.trino.spi.TrinoException;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.trino.plugin.usgs.UsgsErrorCode.USGS_HTTP_ERROR;
import static io.trino.plugin.usgs.UsgsErrorCode.USGS_INVALID_RESPONSE;
import static io.trino.spi.type.DateTimeEncoding.packDateTimeWithZone;
import static io.trino.spi.type.TimeZoneKey.UTC_KEY;
import static java.util.Objects.requireNonNull;

public class UsgsClient
{
    private final HttpClient httpClient;
    private final JsonCodec<JsonNode> codec;
    private final URI feedUri;

    @Inject
    public UsgsClient(@ForUsgs HttpClient httpClient, JsonCodec<JsonNode> codec, UsgsConfig config)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.codec = requireNonNull(codec, "codec is null");
        this.feedUri = config.getFeedUri();
    }

    public List<List<Object>> fetch(String period)
    {
        checkArgument(UsgsMetadata.PERIODS.contains(period), "Unknown USGS feed period: %s", period);
        URI uri = feedUri.resolve("all_" + period + ".geojson");
        StringResponse response;
        try {
            response = httpClient.execute(
                    prepareGet().setUri(uri).setHeader(ACCEPT, "application/geo+json, application/json").build(),
                    createStringResponseHandler());
        }
        catch (RuntimeException e) {
            throw new TrinoException(USGS_HTTP_ERROR, "Cannot read USGS feed: " + uri, e);
        }
        if (response.getStatusCode() != 200) {
            throw new TrinoException(USGS_HTTP_ERROR, "USGS feed returned HTTP " + response.getStatusCode() + ": " + uri);
        }
        try {
            return parse(codec.fromJson(response.getBody()));
        }
        catch (RuntimeException e) {
            throw new TrinoException(USGS_INVALID_RESPONSE, "Invalid USGS GeoJSON feed: " + uri, e);
        }
    }

    static List<List<Object>> parse(JsonNode root)
    {
        checkArgument(root != null && root.isObject() && root.path("type").asText().equals("FeatureCollection"), "Expected a GeoJSON FeatureCollection");
        JsonNode features = root.path("features");
        checkArgument(features.isArray(), "Expected a features array");
        ImmutableList.Builder<List<Object>> rows = ImmutableList.builder();
        for (JsonNode feature : features) {
            checkArgument(feature.path("type").asText().equals("Feature"), "Expected a GeoJSON Feature");
            String id = text(feature.path("id"));
            checkArgument(id != null && !id.isEmpty(), "Missing event id");
            JsonNode properties = feature.path("properties");
            checkArgument(properties.isObject(), "Expected event properties");
            JsonNode geometry = feature.path("geometry");
            JsonNode coordinates = geometry.path("coordinates");
            if (!geometry.isMissingNode() && !geometry.isNull()) {
                checkArgument(geometry.path("type").asText().equals("Point") && coordinates.isArray() && coordinates.size() == 3, "Expected Point coordinates [longitude, latitude, depth]");
            }
            Long tsunami = integer(properties.path("tsunami"));
            checkArgument(tsunami == null || tsunami == 0 || tsunami == 1, "Invalid tsunami flag");
            rows.add(Arrays.asList(
                    id,
                    number(properties.path("mag")),
                    text(properties.path("place")),
                    timestamp(properties.path("time")),
                    timestamp(properties.path("updated")),
                    number(coordinates.path(0)),
                    number(coordinates.path(1)),
                    number(coordinates.path(2)),
                    text(properties.path("magType")),
                    text(properties.path("type")),
                    text(properties.path("status")),
                    tsunami == null ? null : tsunami == 1,
                    text(properties.path("url")),
                    integer(properties.path("sig")),
                    integer(properties.path("felt"))));
        }
        return rows.build();
    }

    private static String text(JsonNode value)
    {
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        checkArgument(value.isTextual(), "Expected a string: %s", value);
        return value.textValue();
    }

    private static Double number(JsonNode value)
    {
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        checkArgument(value.isNumber() && Double.isFinite(value.doubleValue()), "Expected a finite number: %s", value);
        return value.doubleValue();
    }

    private static Long integer(JsonNode value)
    {
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        checkArgument(value.isIntegralNumber() && value.canConvertToLong(), "Expected an integer: %s", value);
        return value.longValue();
    }

    private static Long timestamp(JsonNode value)
    {
        Long millis = integer(value);
        if (millis == null) {
            return null;
        }
        return packDateTimeWithZone(millis, UTC_KEY);
    }
}
