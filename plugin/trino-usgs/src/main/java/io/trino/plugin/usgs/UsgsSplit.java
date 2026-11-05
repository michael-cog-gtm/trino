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

import com.fasterxml.jackson.annotation.JsonCreator;
import io.trino.spi.connector.ConnectorSplit;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.slice.SizeOf.estimatedSizeOf;
import static io.airlift.slice.SizeOf.instanceSize;

public record UsgsSplit(String period)
        implements ConnectorSplit
{
    @JsonCreator
    public UsgsSplit
    {
        checkArgument(UsgsMetadata.PERIODS.contains(period), "Unknown USGS feed period: %s", period);
    }

    @Override
    public long getRetainedSizeInBytes()
    {
        return instanceSize(UsgsSplit.class) + estimatedSizeOf(period);
    }
}
