package org.acmemobility.station.domain.model;

/**
 * Stati del veicolo.
 *
 * DOCKED_AVAILABLE: in stazione e libero (puoi riservare o fare unlock immediate rent)
 * DOCKED_RESERVED : in stazione ma riservato (unlock solo con reservation valida)
 * IN_USE          : fuori dalla stazione, in noleggio (lock per rientro)
 */
public enum VehicleState {
    DOCKED_AVAILABLE,
    DOCKED_RESERVED,
    IN_USE
}
