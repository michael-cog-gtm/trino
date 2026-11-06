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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.trino.spi.Node;
import io.trino.spi.NodeManager;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.FixedSplitSource;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

public class TpchSplitManager
        implements ConnectorSplitManager
{
    private final NodeManager nodeManager;
    private final int splitsPerNode;
    private final TpchQueryNodeCount queryNodeCount;

    @Inject
    public TpchSplitManager(NodeManager nodeManager, TpchConfig config, TpchQueryNodeCount queryNodeCount)
    {
        this(nodeManager, requireNonNull(config, "config is null").getSplitsPerNode(), queryNodeCount);
    }

    public TpchSplitManager(NodeManager nodeManager, TpchConfig config)
    {
        this(nodeManager, config.getSplitsPerNode());
    }

    public TpchSplitManager(NodeManager nodeManager, int splitsPerNode)
    {
        this(nodeManager, splitsPerNode, new TpchQueryNodeCount());
    }

    public TpchSplitManager(NodeManager nodeManager, int splitsPerNode, TpchQueryNodeCount queryNodeCount)
    {
        this.nodeManager = requireNonNull(nodeManager, "nodeManager is null");
        this.queryNodeCount = requireNonNull(queryNodeCount, "queryNodeCount is null");
        checkArgument(splitsPerNode > 0, "splitsPerNode must be at least 1");
        this.splitsPerNode = splitsPerNode;
    }

    @Override
    public ConnectorSplitSource getSplits(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorTableHandle table,
            Set<ColumnHandle> dynamicFilterColumns,
            Constraint constraint)
    {
        Set<Node> nodes = nodeManager.getRequiredWorkerNodes();

        int totalParts = queryNodeCount.nodeCount(session, nodes.size()) * splitsPerNode;

        // sort to ensure the assignment is consistent with TpchNodePartitioningProvider
        List<Node> sortedNodes = nodes.stream()
                .sorted(comparing(node -> node.getHostAndPort().toString()))
                .collect(toImmutableList());

        // Split the data using split and skew by the number of nodes available.
        ImmutableList.Builder<ConnectorSplit> splits = ImmutableList.builder();
        for (int partNumber = 0; partNumber < totalParts; partNumber++) {
            Node node = sortedNodes.get((partNumber / splitsPerNode) % sortedNodes.size());
            splits.add(new TpchSplit(partNumber, totalParts, ImmutableList.of(node.getHostAndPort())));
        }
        return new FixedSplitSource(splits.build());
    }
}
