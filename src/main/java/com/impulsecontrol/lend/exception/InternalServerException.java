package com.impulsecontrol.lend.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kerrk on 9/5/16.
 */
public class InternalServerException extends WebApplicationException {
    public InternalServerException() {
        super(Response.Status.INTERNAL_SERVER_ERROR);
    }

    public InternalServerException(String message) {
        super(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(message).type("text/plain").build());
    }
}
