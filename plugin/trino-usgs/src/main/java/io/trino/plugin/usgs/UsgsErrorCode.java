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

import io.trino.spi.ErrorCode;
import io.trino.spi.ErrorCodeSupplier;

import static io.trino.spi.ErrorType.EXTERNAL;

public enum UsgsErrorCode
        implements ErrorCodeSupplier
{
    USGS_HTTP_ERROR(0),
    USGS_INVALID_RESPONSE(1);

    private final ErrorCode errorCode;

    UsgsErrorCode(int code)
    {
        errorCode = new ErrorCode(0x0536_0000 + code, name(), EXTERNAL);
    }

    @Override
    public ErrorCode toErrorCode()
    {
        return errorCode;
    }
}
