package com.rscb.agent.api;

/**
 * Token device tidak berlaku (401). Terjadi setelah token dirotasi di
 * server — agent merespons dengan register ulang sekali.
 */
public class AuthTokenInvalidException extends ApiException {

    public AuthTokenInvalidException(String message) {
        super(401, message);
    }
}
