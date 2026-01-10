package it.unibo.acme.fleet.simulator;

public class SimulatorConfig {
    public final String natsUrl;
    public final String telemetrySubjectPrefix;
    public final long tickMs;
    public final int numVehicles;
    public final double speedMps;
    public final double batteryDrainPctPerKm;
    public final long randomSeed;

    private SimulatorConfig(String natsUrl,
                            String telemetrySubjectPrefix,
                            long tickMs,
                            int numVehicles,
                            double speedMps,
                            double batteryDrainPctPerKm,
                            long randomSeed) {
        this.natsUrl = natsUrl;
        this.telemetrySubjectPrefix = telemetrySubjectPrefix;
        this.tickMs = tickMs;
        this.numVehicles = numVehicles;
        this.speedMps = speedMps;
        this.batteryDrainPctPerKm = batteryDrainPctPerKm;
        this.randomSeed = randomSeed;
    }

    public static SimulatorConfig fromEnv() {
        String natsUrl = getenv("NATS_URL", "nats://localhost:4222");
        String subjectPrefix = getenv("SIM_TELEMETRY_SUBJECT_PREFIX", "telemetry.vehicle");
        long tickMs = parseLong(getenv("SIM_TICK_MS", "1000"), 1000);
        int numVehicles = (int) parseLong(getenv("SIM_NUM_VEHICLES", "20"), 20);
        double speedMps = parseDouble(getenv("SIM_SPEED_MPS", "8.0"), 8.0);
        double drain = parseDouble(getenv("SIM_BATTERY_DRAIN_PCT_PER_KM", "2.0"), 2.0);
        long seed = parseLong(getenv("SIM_RANDOM_SEED", "42"), 42);

        if (tickMs < 100) tickMs = 100;
        if (numVehicles < 1) numVehicles = 1;
        if (speedMps <= 0) speedMps = 1.0;
        if (drain < 0) drain = 0.0;

        return new SimulatorConfig(natsUrl, subjectPrefix, tickMs, numVehicles, speedMps, drain, seed);
    }

    private static String getenv(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static long parseLong(String v, long def) {
        try { return Long.parseLong(v.trim()); } catch (Exception e) { return def; }
    }

    private static double parseDouble(String v, double def) {
        try { return Double.parseDouble(v.trim()); } catch (Exception e) { return def; }
    }

    @Override
    public String toString() {
        return "SimulatorConfig{" +
                "natsUrl='" + natsUrl + '\'' +
                ", telemetrySubjectPrefix='" + telemetrySubjectPrefix + '\'' +
                ", tickMs=" + tickMs +
                ", numVehicles=" + numVehicles +
                ", speedMps=" + speedMps +
                ", batteryDrainPctPerKm=" + batteryDrainPctPerKm +
                ", randomSeed=" + randomSeed +
                '}';
    }
}
