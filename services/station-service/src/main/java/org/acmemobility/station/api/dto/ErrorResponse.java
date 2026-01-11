package org.acmemobility.station.api.dto;

public class ErrorResponse {
    public String error;

    public ErrorResponse() {
    }

    public ErrorResponse(String error) {
        this.error = error;
    }
}
