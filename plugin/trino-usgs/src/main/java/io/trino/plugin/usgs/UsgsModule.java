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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.concurrent.TimeUnit.SECONDS;

public class UsgsModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(UsgsConnector.class).in(Scopes.SINGLETON);
        binder.bind(UsgsClient.class).in(Scopes.SINGLETON);
        binder.bind(UsgsRecordSetProvider.class).in(Scopes.SINGLETON);
        configBinder(binder).bindConfig(UsgsConfig.class);
        jsonCodecBinder(binder).bindJsonCodec(JsonNode.class);
        httpClientBinder(binder).bindHttpClient("usgs", ForUsgs.class)
                .withConfigDefaults(config -> {
                    config.setConnectTimeout(new Duration(10, SECONDS));
                    config.setRequestTimeout(new Duration(30, SECONDS));
                    config.setMaxResponseContentLength(DataSize.of(32, MEGABYTE));
                });
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    public @interface ForUsgs {}
}
