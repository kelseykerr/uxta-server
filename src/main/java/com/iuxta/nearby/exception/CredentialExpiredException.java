package com.iuxta.nearby.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Created by kerrk on 10/1/16.
 */
public class CredentialExpiredException extends WebApplicationException {
    public CredentialExpiredException() {
        super(Response.Status.BAD_REQUEST);
    }

    public CredentialExpiredException(String message) {
        super(Response.status(Response.Status.BAD_REQUEST)
                .entity(message).type("text/plain").build());
    }
}
