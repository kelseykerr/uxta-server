package com.iuxta.nearby.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kerrk on 7/26/16.
 */
public class UnauthorizedException extends WebApplicationException {
    public UnauthorizedException() {
        super(Response.Status.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(Response.status(Response.Status.UNAUTHORIZED).
                entity(message).type("text/plain").build());
    }
}
