# USGS connector

The USGS connector reads the public [US Geological Survey earthquake summary
feeds](https://earthquake.usgs.gov/earthquakes/feed/v1.0/geojson.php).
No account or API key is required.

## Configuration

Create `etc/catalog/usgs.properties` on the coordinator and workers:

```properties
connector.name=usgs
```

Restart Trino after adding the catalog. The connector is included in the server
distribution. For a custom installation, build `plugin/trino-usgs` and copy the
directory extracted from its plugin ZIP into `plugin/` on every node, using the
same Trino version.
Workers need HTTPS access to `earthquake.usgs.gov`.

## Tables

The `default` schema contains four tables:

| Table | Rolling coverage |
|---|---|
| `earthquakes_hour` | Past hour |
| `earthquakes_day` | Past 24 hours |
| `earthquakes_week` | Past 7 days |
| `earthquakes_month` | Past 30 days |

Each table reads the corresponding `all_<period>.geojson` summary feed. These are
rolling windows, not calendar periods. USGS refreshes the feeds every minute;
events and their preliminary measurements can change between queries. The feeds
may also include non-earthquake seismic events, so filter `event_type` when needed.

## Columns

| Column | Trino type | Meaning |
|---|---|---|
| `id` | `varchar` | USGS event identifier |
| `magnitude` | `double` | Reported magnitude; may be null or negative |
| `place` | `varchar` | Human-readable location |
| `event_time` | `timestamp(3) with time zone` | Origin time, converted from epoch milliseconds to UTC |
| `updated` | `timestamp(3) with time zone` | Last update time in UTC |
| `longitude` | `double` | Longitude in decimal degrees |
| `latitude` | `double` | Latitude in decimal degrees |
| `depth_km` | `double` | Depth in kilometers; may be negative |
| `magnitude_type` | `varchar` | Magnitude scale, such as `ml` or `mww` |
| `event_type` | `varchar` | Event category, such as `earthquake` |
| `status` | `varchar` | Review status |
| `tsunami` | `boolean` | USGS tsunami flag; not a tsunami warning |
| `url` | `varchar` | USGS event page |
| `significance` | `bigint` | USGS significance score |
| `felt` | `bigint` | Number of felt reports |

Missing and JSON null measurements become SQL `NULL`, not zero. GeoJSON Point
coordinates are mapped in longitude, latitude, depth order. Unknown JSON fields
are ignored; malformed feeds fail the query instead of silently losing events.

## Example queries

The ten strongest earthquakes in the past seven days:

```sql
SELECT id, magnitude, place, event_time
FROM usgs.default.earthquakes_week
WHERE event_type = 'earthquake' AND magnitude IS NOT NULL
ORDER BY magnitude DESC, event_time DESC, id
LIMIT 10;
```

For the current calendar week in UTC, query the longer window and filter:

```sql
SELECT id, magnitude, place, event_time
FROM usgs.default.earthquakes_month
WHERE event_type = 'earthquake'
  AND event_time >= date_trunc('week', current_timestamp AT TIME ZONE 'UTC')
ORDER BY magnitude DESC NULLS LAST, event_time DESC, id
LIMIT 10;
```

Count today's stronger earthquakes:

```sql
SELECT count(*) AS earthquakes, max(magnitude) AS strongest
FROM usgs.default.earthquakes_day
WHERE event_type = 'earthquake'
  AND magnitude >= 4.5
  AND event_time >= date_trunc('day', current_timestamp AT TIME ZONE 'UTC');
```

## Operational limits

This connector is read-only: it does not support DDL, inserts, updates, or deletes.
It is intended for small public summary feeds, not historical catalog searches.
Each table scan uses one split and one HTTP GET, then buffers the response and
rows in worker memory. Filters, aggregation, sorting, and limits run in Trino;
there is no predicate or limit pushdown, pagination, or connector cache. A join or
repeated scan can fetch a feed again. There is no cross-scan snapshot isolation.

The HTTP client defaults to a 10-second connect timeout, a 30-second request
timeout, and a 32 MB maximum response. Standard Airlift HTTP client settings use
the `usgs.http-client.` prefix, for example:

```properties
usgs.http-client.request-timeout=45s
usgs.http-client.max-response-content-length=32MB
```

HTTP errors (including rate limits), connection failures, and timeouts fail with
`USGS_HTTP_ERROR`. Invalid JSON or unexpected event structure fails with
`USGS_INVALID_RESPONSE`. The connector does not automatically retry. Reduce
query frequency on rate limits; USGS data availability is not guaranteed.

For an administrator-managed mirror or local testing, set `usgs.feed-uri` to an
absolute HTTP(S) base URI ending in `/`, such as `http://localhost:8081/summary/`.
It must serve the four standard summary filenames. Do not include credentials,
a query string, or a fragment in this URI. SQL cannot specify arbitrary URLs.

## Development verification

From the repository root, with the required JDK:

```text
./mvnw install -pl plugin/trino-usgs -am -DskipTests -Dair.check.skip-all=true
./mvnw test -pl plugin/trino-usgs
```

The tests use checked-in GeoJSON and local HTTP responses, never the live USGS
service. `UsgsQueryRunner` is a separate opt-in live check:

```bash
./mvnw -pl plugin/trino-usgs test-compile exec:exec \
  -Dexec.classpathScope=test -Dexec.executable=java \
  '-Dexec.args=--add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Xmx1G -cp %classpath io.trino.plugin.usgs.UsgsQueryRunner'
```

It starts a coordinator and worker, installs the connector, executes the first
example query against USGS, prints the rows, and shuts down. Live data is not a
test fixture.
