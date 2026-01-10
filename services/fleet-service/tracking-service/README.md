# tracking-service (Fleet Management)

Microservizio Helidon MP che:
- riceve telemetria (pub/sub) su NATS
- gestisce comandi start/stop (NATS request-reply)
- mantiene stato di tracking in memoria
- pubblica snapshot periodici (push) per aggiornare la cache del fleet-gateway

## Requisiti
- Java 21
- Maven 3.8+
- un broker NATS (core) in esecuzione (default: `nats://localhost:4222`)

## Build
```bash
mvn clean package
```

## Run
Da root progetto:
```bash
java -jar target/tracking-service-1.0.0-SNAPSHOT.jar
```

### Config (env var o microprofile-config.properties)
- `NATS_URL` (default `nats://localhost:4222`)
- `TRACKING_TELEMETRY_SUBJECT` (default `telemetry.vehicle.*`)
- `TRACKING_CMD_START_SUBJECT` (default `cmd.tracking.start`)
- `TRACKING_CMD_STOP_SUBJECT` (default `cmd.tracking.stop`)
- `TRACKING_SNAPSHOT_SUBJECT_PREFIX` (default `event.tracking.snapshot`)
- `TRACKING_SNAPSHOT_INTERVAL_MS` (default `1000`)
- `TRACKING_SNAPSHOT_PUBLISH_DISTANCE_THRESHOLD_M` (default `10`)
- `TRACKING_SNAPSHOT_PUBLISH_STOPPED` (default `true`)

## Message schema (JSON)
### Telemetry (pub)
Subject: `telemetry.vehicle.<vehicleId>`
```json
{
  "vehicleId": "V001",
  "ts": 1736520000000,
  "lat": 44.4949,
  "lon": 11.3426,
  "batteryPct": 82
}
```

### Start (req/reply)
Subject: `cmd.tracking.start`
```json
{ "vehicleId": "V001", "ts": 1736520000000, "stationId": "S01" }
```

### Stop (req/reply)
Subject: `cmd.tracking.stop`
```json
{ "vehicleId": "V001", "ts": 1736520100000, "stationId": "S05" }
```

### Snapshot (pub)
Subject: `event.tracking.snapshot.<vehicleId>`
```json
{
  "vehicleId": "V001",
  "ts": 1736520001500,
  "active": true,
  "lat": 44.4950,
  "lon": 11.3430,
  "distanceMeters": 120.4,
  "startedAt": 1736520000000,
  "lastUpdateTs": 1736520001400,
  "stale": false
}
```
