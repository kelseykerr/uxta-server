package com.iuxta.nearby.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kelseykerr on 5/6/17.
 */
public class LocationNotAvailableException extends WebApplicationException {
    public LocationNotAvailableException() {
        super(Response.Status.FORBIDDEN);
    }

    public LocationNotAvailableException(String message) {
        super(Response.status(Response.Status.FORBIDDEN)
                .entity(message).type("text/plain").build());
    }
}
