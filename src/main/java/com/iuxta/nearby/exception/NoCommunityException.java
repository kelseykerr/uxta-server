package com.iuxta.nearby.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kelseykerr on 5/6/17.
 */
public class NoCommunityException extends WebApplicationException {
    public NoCommunityException() {
        super(Response.Status.FORBIDDEN);
    }

    public NoCommunityException(String message) {
        super(Response.status(Response.Status.FORBIDDEN)
                .entity(message).type("text/plain").build());
    }
}
