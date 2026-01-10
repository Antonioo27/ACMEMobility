# battery-service (Fleet Management)

Microservizio Helidon MP che:
- riceve telemetria (pub/sub) su NATS (senza persistenza)
- **si attiva/disattiva per veicolo** tramite comandi start/stop (NATS request-reply) inviati dal fleet-gateway
- mantiene lo stato batteria in memoria per veicolo *solo quando attivo*
- pubblica snapshot (push) su NATS per aggiornare la cache del fleet-gateway

## Requisiti
- Java 21
- Maven 3.8+
- NATS in esecuzione (default: `nats://localhost:4222`)

## Build
```bash
mvn clean package
```

## Run
```bash
java -jar target/battery-service-1.0.0-SNAPSHOT.jar
```

## Config
- `NATS_URL` (default `nats://localhost:4222`)
- `BATTERY_TELEMETRY_SUBJECT` (default `telemetry.vehicle.*`)
- `BATTERY_CMD_START_SUBJECT` (default `cmd.battery.start`)
- `BATTERY_CMD_STOP_SUBJECT` (default `cmd.battery.stop`)
- `BATTERY_SNAPSHOT_SUBJECT_PREFIX` (default `event.battery.snapshot`)
- `BATTERY_SNAPSHOT_INTERVAL_MS` (default `1000`)
- `BATTERY_SNAPSHOT_PUBLISH_DELTA_PCT` (default `1`)  (pubblica anche se cambia di almeno 1%)
- `BATTERY_LOW_THRESHOLD_PCT` (default `15`)
- `BATTERY_SNAPSHOT_PUBLISH_STOPPED` (default `true`) (pubblica uno snapshot finale quando riceve stop)

## Subjects

### Telemetry (pub dal simulatore)
`telemetry.vehicle.<vehicleId>`

### Commands (req/reply dal gateway)
- `cmd.battery.start`
- `cmd.battery.stop`

### Snapshot (pub dal battery-service)
`event.battery.snapshot.<vehicleId>`
