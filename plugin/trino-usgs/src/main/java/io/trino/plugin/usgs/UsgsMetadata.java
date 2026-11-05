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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.ConnectorTableVersion;
import io.trino.spi.connector.RelationColumnsMetadata;
import io.trino.spi.connector.SchemaTableName;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.TimestampWithTimeZoneType.TIMESTAMP_TZ_MILLIS;
import static io.trino.spi.type.VarcharType.VARCHAR;

public class UsgsMetadata
        implements ConnectorMetadata
{
    static final List<String> PERIODS = ImmutableList.of("hour", "day", "week", "month");
    static final List<ColumnMetadata> COLUMNS = ImmutableList.of(
            new ColumnMetadata("id", VARCHAR),
            new ColumnMetadata("magnitude", DOUBLE),
            new ColumnMetadata("place", VARCHAR),
            new ColumnMetadata("event_time", TIMESTAMP_TZ_MILLIS),
            new ColumnMetadata("updated", TIMESTAMP_TZ_MILLIS),
            new ColumnMetadata("longitude", DOUBLE),
            new ColumnMetadata("latitude", DOUBLE),
            new ColumnMetadata("depth_km", DOUBLE),
            new ColumnMetadata("magnitude_type", VARCHAR),
            new ColumnMetadata("event_type", VARCHAR),
            new ColumnMetadata("status", VARCHAR),
            new ColumnMetadata("tsunami", BOOLEAN),
            new ColumnMetadata("url", VARCHAR),
            new ColumnMetadata("significance", BIGINT),
            new ColumnMetadata("felt", BIGINT));

    @Override
    public List<String> listSchemaNames(ConnectorSession session)
    {
        return ImmutableList.of("default");
    }

    @Override
    public UsgsTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName, Optional<ConnectorTableVersion> startVersion, Optional<ConnectorTableVersion> endVersion)
    {
        if (startVersion.isPresent() || endVersion.isPresent()) {
            throw new TrinoException(NOT_SUPPORTED, "USGS does not support versioned tables");
        }
        return PERIODS.stream()
                .map(UsgsTableHandle::new)
                .filter(table -> table.schemaTableName().equals(tableName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> schemaName)
    {
        if (schemaName.isPresent() && !schemaName.orElseThrow().equals("default")) {
            return ImmutableList.of();
        }
        return PERIODS.stream().map(UsgsTableHandle::new).map(UsgsTableHandle::schemaTableName).toList();
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle table)
    {
        return new ConnectorTableMetadata(((UsgsTableHandle) table).schemaTableName(), COLUMNS);
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle table)
    {
        ImmutableMap.Builder<String, ColumnHandle> columns = ImmutableMap.builder();
        for (int ordinal = 0; ordinal < COLUMNS.size(); ordinal++) {
            columns.put(COLUMNS.get(ordinal).getName(), new UsgsColumnHandle(ordinal));
        }
        return columns.buildOrThrow();
    }

    @Override
    public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle table, ColumnHandle column)
    {
        return COLUMNS.get(((UsgsColumnHandle) column).ordinal());
    }

    @Override
    public Iterator<RelationColumnsMetadata> streamRelationColumns(ConnectorSession session, Optional<String> schemaName, UnaryOperator<Set<SchemaTableName>> relationFilter)
    {
        Set<SchemaTableName> tables = listTables(session, schemaName).stream().collect(toImmutableSet());
        return relationFilter.apply(tables).stream()
                .map(table -> RelationColumnsMetadata.forTable(table, COLUMNS))
                .iterator();
    }
}
