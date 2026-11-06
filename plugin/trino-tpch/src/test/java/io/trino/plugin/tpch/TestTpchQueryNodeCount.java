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

import io.trino.spi.HostAddress;
import io.trino.spi.Node;
import io.trino.spi.NodeManager;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.Constraint;
import io.trino.spi.security.ConnectorIdentity;
import io.trino.spi.type.TimeZoneKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTpchQueryNodeCount
{
    @Test
    void testTopologyChanges()
            throws Exception
    {
        for (boolean mappingFirst : List.of(false, true)) {
            for (int initialCount : List.of(2, 3)) {
                TpchQueryNodeCount counts = new TpchQueryNodeCount();
                TestingNodes nodes = new TestingNodes();
                nodes.count = initialCount;
                TpchSplitManager splits = new TpchSplitManager(nodes, 4, counts);
                TpchNodePartitioningProvider partitions = new TpchNodePartitioningProvider(nodes, 4, counts);
                ConnectorSession session = new TestingSession("query");
                if (mappingFirst) {
                    assertThat(partitions.getBucketNodeMapping(null, session, null).orElseThrow().getBucketCount()).isEqualTo(initialCount * 4);
                }
                else {
                    assertThat(splits.getSplits(null, session, null, Set.of(), Constraint.alwaysTrue()).getNextBatch(100, null).get()).hasSize(initialCount * 4);
                }
                nodes.count = 5 - initialCount;
                List<Node> mapping = partitions.getBucketNodeMapping(null, session, null).orElseThrow().getFixedMapping();
                assertThat(mapping).hasSize(initialCount * 4);
                List<TpchSplit> batch = splits.getSplits(null, session, null, Set.of(), Constraint.alwaysTrue())
                        .getNextBatch(100, null).get().stream().map(TpchSplit.class::cast).toList();
                assertThat(batch).hasSize(initialCount * 4);
                for (int i = 0; i < batch.size(); i++) {
                    assertThat(batch.get(i).getPartNumber()).isEqualTo(i);
                    assertThat(batch.get(i).getTotalParts()).isEqualTo(initialCount * 4);
                    assertThat(batch.get(i).getAddresses()).containsExactly(mapping.get(i).getHostAndPort());
                    assertThat(mapping.get(i).getHostAndPort()).isEqualTo(HostAddress.fromParts("localhost", 1000 + (i / 4) % nodes.count));
                }
                assertThat(partitions.getBucketNodeMapping(null, new TestingSession("next"), null).orElseThrow().getBucketCount()).isEqualTo(nodes.count * 4);
            }
        }
    }

    @Test
    void testNoWorkers()
    {
        TestingNodes nodes = new TestingNodes();
        ConnectorSession session = new TestingSession("query");
        TpchQueryNodeCount counts = new TpchQueryNodeCount();
        assertThatThrownBy(() -> new TpchSplitManager(nodes, 4, counts).getSplits(null, session, null, Set.of(), Constraint.alwaysTrue()))
                .hasMessage("No nodes available to run query");
        assertThatThrownBy(() -> new TpchNodePartitioningProvider(nodes, 4, counts).getBucketNodeMapping(null, session, null))
                .hasMessage("No nodes available to run query");
        nodes.count = 2;
        assertThat(counts.nodeCount(session, nodes.count)).isEqualTo(2);
    }

    private static class TestingNodes
            implements NodeManager
    {
        private int count;

        @Override
        public Set<Node> getAllNodes()
        {
            return getWorkerNodes();
        }

        @Override
        public Set<Node> getWorkerNodes()
        {
            return java.util.stream.IntStream.range(0, count)
                    .mapToObj(i -> (Node) new TestingNode(1000 + i))
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        @SuppressWarnings("deprecation")
        public Node getCurrentNode()
        {
            throw new UnsupportedOperationException();
        }
    }

    private record TestingNode(int port)
            implements Node
    {
        @Override
        public String getHost()
        {
            return "localhost";
        }

        @Override
        public HostAddress getHostAndPort()
        {
            return HostAddress.fromParts(getHost(), port);
        }

        @Override
        public String getNodeIdentifier()
        {
            return Integer.toString(port);
        }

        @Override
        public String getVersion()
        {
            return "test";
        }

        @Override
        public boolean isCoordinator()
        {
            return false;
        }
    }

    private record TestingSession(String queryId)
            implements ConnectorSession
    {
        @Override
        public String getQueryId()
        {
            return queryId;
        }

        @Override
        public Optional<String> getSource()
        {
            return Optional.empty();
        }

        @Override
        public ConnectorIdentity getIdentity()
        {
            return ConnectorIdentity.ofUser("test");
        }

        @Override
        public TimeZoneKey getTimeZoneKey()
        {
            return TimeZoneKey.UTC_KEY;
        }

        @Override
        public Locale getLocale()
        {
            return Locale.ENGLISH;
        }

        @Override
        public Optional<String> getTraceToken()
        {
            return Optional.empty();
        }

        @Override
        public Instant getStart()
        {
            return Instant.EPOCH;
        }

        @Override
        public <T> T getProperty(String name, Class<T> type)
        {
            throw new UnsupportedOperationException();
        }
    }
}
