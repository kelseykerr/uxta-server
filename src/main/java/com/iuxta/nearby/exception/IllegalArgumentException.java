package com.iuxta.nearby.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kerrk on 9/3/16.
 */
public class IllegalArgumentException extends WebApplicationException {
    public IllegalArgumentException() {
        super(Response.Status.EXPECTATION_FAILED);
    }

    public IllegalArgumentException(String message) {
        super(Response.status(Response.Status.EXPECTATION_FAILED)
                .entity(message).type("text/plain").build());
    }
}
