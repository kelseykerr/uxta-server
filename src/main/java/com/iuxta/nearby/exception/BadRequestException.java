package com.iuxta.nearby.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kerrk on 8/6/16.
 */
public class BadRequestException extends WebApplicationException {
    public BadRequestException() {
        super(Response.Status.BAD_REQUEST);
    }

    public BadRequestException(String message) {
        super(Response.status(Response.Status.BAD_REQUEST).
                entity(message).type("text/plain").build());
    }
}
