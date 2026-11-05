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

import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.QueryRunner;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

final class TestUsgsQueries
        extends AbstractTestQueryFramework
{
    private final ConcurrentLinkedQueue<String> paths = new ConcurrentLinkedQueue<>();

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        String fixture = TestUsgsClient.fixture();
        MockWebServer server = closeAfterClass(new MockWebServer());
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                paths.add(request.getUrl().encodedPath());
                return new MockResponse.Builder().code(200).body(fixture).build();
            }
        });
        server.start();
        return UsgsQueryRunner.create(Map.of("usgs.feed-uri", server.url("/summary/").toString()));
    }

    @Test
    void testDiscovery()
    {
        assertQuery("SHOW TABLES", "VALUES 'earthquakes_hour', 'earthquakes_day', 'earthquakes_week', 'earthquakes_month'");
        assertQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'default' AND table_name = 'earthquakes_week'", "VALUES 15");
        assertQueryFails("SELECT * FROM usgs.default.unknown", ".*Table 'usgs.default.unknown' does not exist");
        assertQueryFails("SELECT * FROM usgs.unknown.earthquakes_week", ".*does not exist");
    }

    @Test
    void testAllPeriods()
    {
        for (String period : UsgsMetadata.PERIODS) {
            assertQuery("SELECT count(*) FROM earthquakes_" + period, "VALUES 3");
            assertThat(paths).contains("/summary/all_" + period + ".geojson");
        }
    }

    @Test
    void testTopEarthquakes()
    {
        assertQueryOrdered(
                """
                SELECT id, magnitude, place FROM earthquakes_week
                WHERE event_type = 'earthquake' AND magnitude IS NOT NULL
                ORDER BY magnitude DESC LIMIT 10
                """,
                "VALUES ('strong', 6.2, 'Near Hawaiʻi'), ('small', -0.4, 'Alaska')");
        assertQuery("SELECT sum(magnitude), count(magnitude) FROM earthquakes_day", "VALUES (5.8, 2)");
        assertQuery("SELECT id FROM earthquakes_day WHERE magnitude IS NULL AND event_time IS NULL AND longitude IS NULL", "VALUES 'unknown'");
        assertQuery(
                "SELECT latitude, longitude, depth_km, tsunami, significance, felt FROM earthquakes_hour WHERE id = 'strong'",
                "VALUES (19.25, -155.5, 10.75, true, CAST(600 AS bigint), CAST(12 AS bigint))");
        assertQuery(
                "SELECT to_unixtime(event_time), to_unixtime(updated) FROM earthquakes_week WHERE id = 'strong'",
                "VALUES (1700000000.123, 1700000001.456)");
        assertQuery("SELECT place, id, place FROM earthquakes_month WHERE id = 'small'", "VALUES ('Alaska', 'small', 'Alaska')");
        assertQuery("SELECT count(*) FROM earthquakes_day WHERE false", "VALUES 0");
    }

    @Test
    void testReadOnly()
    {
        assertQueryFails("CREATE TABLE usgs.default.new_table (id varchar)", ".*does not support creating tables");
        assertQueryFails("INSERT INTO earthquakes_day (id) VALUES 'new'", ".*does not support inserts");
        assertQueryFails("DROP TABLE earthquakes_week", ".*does not support dropping tables");
        assertQueryFails("DELETE FROM earthquakes_day", ".*does not support modifying table rows");
        assertQueryFails("UPDATE earthquakes_day SET magnitude = 1", ".*does not support modifying table rows");
    }
}
