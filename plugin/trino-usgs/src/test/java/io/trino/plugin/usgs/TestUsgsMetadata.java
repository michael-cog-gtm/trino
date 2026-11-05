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

import io.trino.spi.connector.RelationColumnsMetadata;
import io.trino.spi.connector.SchemaTableName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestUsgsMetadata
{
    private final UsgsMetadata metadata = new UsgsMetadata();

    @Test
    void testDiscovery()
    {
        assertThat(metadata.listSchemaNames(null)).containsExactly("default");
        assertThat(metadata.listTables(null, Optional.empty())).hasSize(4);
        assertThat(metadata.listTables(null, Optional.of("missing"))).isEmpty();
        assertThat(metadata.getTableHandle(null, new SchemaTableName("default", "earthquakes_week"), Optional.empty(), Optional.empty()))
                .isEqualTo(new UsgsTableHandle("week"));
        assertThat(metadata.getTableHandle(null, new SchemaTableName("missing", "earthquakes_week"), Optional.empty(), Optional.empty())).isNull();
        assertThat(metadata.getTableHandle(null, new SchemaTableName("default", "missing"), Optional.empty(), Optional.empty())).isNull();
        assertThat(metadata.streamRelationColumns(null, Optional.of("default"), tables -> tables.stream()
                .filter(table -> table.getTableName().equals("earthquakes_week"))
                .collect(toImmutableSet())))
                .toIterable()
                .containsExactly(RelationColumnsMetadata.forTable(new SchemaTableName("default", "earthquakes_week"), UsgsMetadata.COLUMNS));
        assertThat(metadata.streamRelationColumns(null, Optional.of("missing"), identity())).isExhausted();
    }

    @Test
    void testColumns()
    {
        UsgsTableHandle table = new UsgsTableHandle("week");
        assertThat(metadata.getTableMetadata(null, table).getColumns()).hasSize(15);
        assertThat(metadata.getColumnHandles(null, table)).hasSize(15).containsEntry("magnitude", new UsgsColumnHandle(1));
        assertThat(metadata.getColumnMetadata(null, table, new UsgsColumnHandle(3)).getType().getDisplayName()).isEqualTo("timestamp(3) with time zone");
    }

    @Test
    void testHandleSerialization()
    {
        var tableCodec = jsonCodec(UsgsTableHandle.class);
        assertThat(tableCodec.fromJson(tableCodec.toJson(new UsgsTableHandle("week")))).isEqualTo(new UsgsTableHandle("week"));
        var splitCodec = jsonCodec(UsgsSplit.class);
        assertThat(splitCodec.fromJson(splitCodec.toJson(new UsgsSplit("day")))).isEqualTo(new UsgsSplit("day"));
        var columnCodec = jsonCodec(UsgsColumnHandle.class);
        assertThat(columnCodec.fromJson(columnCodec.toJson(new UsgsColumnHandle(2)))).isEqualTo(new UsgsColumnHandle(2));
        assertThatThrownBy(() -> new UsgsTableHandle("../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsgsSplit("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UsgsColumnHandle(15)).isInstanceOf(IndexOutOfBoundsException.class);
    }
}
