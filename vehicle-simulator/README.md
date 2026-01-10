# vehicle-simulator

Applicazione **standalone** (non microservizio) che simula una flotta di veicoli:
- ogni veicolo si muove in linea retta a velocità costante fra due stazioni
- la batteria diminuisce in funzione dei km percorsi
- pubblica su **NATS core** (senza persistenza) messaggi JSON su `telemetry.vehicle.<vehicleId>`

## Requisiti
- Java 21
- Maven 3.8+
- NATS in esecuzione (default `nats://localhost:4222`)

## Build
```bash
mvn clean package
```

## Run
```bash
java -jar target/vehicle-simulator-1.0.0-SNAPSHOT.jar
```

## Config via env var (con default)
- `NATS_URL` (default `nats://localhost:4222`)
- `SIM_TELEMETRY_SUBJECT_PREFIX` (default `telemetry.vehicle`)
- `SIM_TICK_MS` (default `1000`)
- `SIM_NUM_VEHICLES` (default `20`)
- `SIM_SPEED_MPS` (default `8.0`)  (≈ 28.8 km/h)
- `SIM_BATTERY_DRAIN_PCT_PER_KM` (default `2.0`)
- `SIM_RANDOM_SEED` (default `42`)

## Payload telemetria (JSON)
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

## Nota pratica
Il simulatore pubblica telemetria per tutti i veicoli sempre.
Se tracking/battery sono “disattivati” (stop), ignoreranno i messaggi: è voluto.
