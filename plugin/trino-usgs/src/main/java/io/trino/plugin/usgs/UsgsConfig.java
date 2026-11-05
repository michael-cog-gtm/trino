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

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

public class UsgsConfig
{
    private URI feedUri = URI.create("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/");

    @NotNull
    public URI getFeedUri()
    {
        return feedUri;
    }

    @Config("usgs.feed-uri")
    @ConfigDescription("Base URI of the USGS GeoJSON summary feeds")
    public UsgsConfig setFeedUri(URI feedUri)
    {
        this.feedUri = feedUri;
        return this;
    }

    @AssertTrue(message = "usgs.feed-uri must be an absolute HTTP or HTTPS URI ending in / without user info, query, or fragment")
    public boolean isFeedUriValid()
    {
        return feedUri != null &&
                ("http".equals(feedUri.getScheme()) || "https".equals(feedUri.getScheme())) &&
                feedUri.getHost() != null &&
                feedUri.getUserInfo() == null &&
                feedUri.getQuery() == null &&
                feedUri.getFragment() == null &&
                feedUri.getPath().endsWith("/");
    }
}
