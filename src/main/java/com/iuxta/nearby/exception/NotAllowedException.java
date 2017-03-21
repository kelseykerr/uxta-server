package com.iuxta.nearby.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kerrk on 9/18/16.
 */
public class NotAllowedException extends WebApplicationException {

    public NotAllowedException() {
        super(Response.Status.NOT_ACCEPTABLE);
    }

    public NotAllowedException(String message) {
        super(Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity(message).type("text/plain").build());
    }
}
