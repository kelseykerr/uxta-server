package com.impulsecontrol.lend.auth;

import io.dropwizard.auth.AuthFilter;

import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import java.io.IOException;
import java.security.Principal;

/**
 * Created by kerrk on 8/17/16.
 */
@Priority(1000)
public class CredentialAuthFilter<P extends Principal> extends AuthFilter<Credentials, P> {

    public final static String CUSTOM_HEADER = "x-auth-token";

    private CredentialAuthFilter() {
    }

    public void filter(ContainerRequestContext requestContext) throws IOException {

        Credentials credentials = this.getCredentials((String)requestContext.getHeaders().getFirst(CUSTOM_HEADER));
        if(!this.authenticate(requestContext, credentials, "BASIC")) {
            throw new WebApplicationException(this.unauthorizedHandler.buildResponse(this.prefix, this.realm));
        }
    }

    @Nullable
    private Credentials getCredentials(String header) {
        if(header == null) {
            return null;
        } else {
            return new Credentials(header);
        }
    }

    public static class Builder<P extends Principal> extends AuthFilterBuilder<Credentials, P, CredentialAuthFilter<P>> {
        public Builder() {
        }

        protected CredentialAuthFilter<P> newInstance() {
            return new CredentialAuthFilter();
        }
    }
}

