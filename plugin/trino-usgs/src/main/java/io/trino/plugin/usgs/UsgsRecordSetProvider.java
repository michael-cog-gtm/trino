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

import com.google.inject.Inject;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorRecordSetProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.InMemoryRecordSet;
import io.trino.spi.connector.RecordSet;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class UsgsRecordSetProvider
        implements ConnectorRecordSetProvider
{
    private final UsgsClient client;

    @Inject
    public UsgsRecordSetProvider(UsgsClient client)
    {
        this.client = requireNonNull(client, "client is null");
    }

    @Override
    public RecordSet getRecordSet(ConnectorTransactionHandle transaction, ConnectorSession session, ConnectorSplit split, ConnectorTableHandle table, List<? extends ColumnHandle> columns)
    {
        List<Integer> ordinals = columns.stream().map(UsgsColumnHandle.class::cast).map(UsgsColumnHandle::ordinal).toList();
        return new InMemoryRecordSet(
                ordinals.stream().map(ordinal -> UsgsMetadata.COLUMNS.get(ordinal).getType()).toList(),
                client.fetch(((UsgsSplit) split).period()).stream()
                        .map(row -> ordinals.stream().map(row::get).toList())
                        .toList());
    }
}
