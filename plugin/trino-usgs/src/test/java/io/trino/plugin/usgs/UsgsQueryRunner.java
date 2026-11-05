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

import io.trino.testing.DistributedQueryRunner;

import java.time.Instant;
import java.util.Map;

import static io.airlift.testing.Closeables.closeAllSuppress;
import static io.trino.testing.TestingSession.testSessionBuilder;

public final class UsgsQueryRunner
{
    private UsgsQueryRunner() {}

    public static DistributedQueryRunner create(Map<String, String> properties)
            throws Exception
    {
        DistributedQueryRunner runner = DistributedQueryRunner.builder(testSessionBuilder()
                        .setCatalog("usgs")
                        .setSchema("default")
                        .build())
                .setWorkerCount(1)
                .build();
        try {
            runner.installPlugin(new UsgsPlugin());
            runner.createCatalog("usgs", "usgs", properties);
            return runner;
        }
        catch (Exception e) {
            closeAllSuppress(e, runner);
            throw e;
        }
    }

    public static void main(String[] args)
            throws Exception
    {
        String sql =
                """
                SELECT id, magnitude, place, event_time
                FROM usgs.default.earthquakes_week
                WHERE event_type = 'earthquake' AND magnitude IS NOT NULL
                ORDER BY magnitude DESC, event_time DESC, id
                LIMIT 10
                """;
        if (args.length > 0) {
            sql = args[0];
        }
        try (DistributedQueryRunner runner = create(Map.of())) {
            System.out.println("Live USGS query at " + Instant.now());
            System.out.println(sql);
            runner.execute(sql).getMaterializedRows().forEach(System.out::println);
        }
    }
}
