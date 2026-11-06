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
package io.trino.plugin.tpch;

import com.google.common.cache.Cache;
import io.trino.cache.EvictableCacheBuilder;
import io.trino.spi.connector.ConnectorSession;

import static com.google.common.base.Preconditions.checkArgument;
import static io.trino.cache.CacheUtils.uncheckedCacheGet;
import static java.time.Duration.ofDays;

public class TpchQueryNodeCount
{
    private final Cache<String, Integer> nodeCounts = EvictableCacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(ofDays(1))
            .build();

    public int nodeCount(ConnectorSession session, int currentNodeCount)
    {
        checkArgument(currentNodeCount > 0, "No TPCH nodes available");
        return uncheckedCacheGet(nodeCounts, session.getQueryId(), () -> currentNodeCount);
    }
}
