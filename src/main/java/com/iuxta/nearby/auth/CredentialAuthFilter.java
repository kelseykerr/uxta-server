package com.iuxta.nearby.auth;

import com.iuxta.nearby.NearbyUtils;
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

    public static final String METHOD_HEADER = "x-auth-method";

    private CredentialAuthFilter() {
    }

    public void filter(ContainerRequestContext requestContext) throws IOException {

        Credentials credentials = this.getCredentials((String)requestContext.getHeaders().getFirst(CUSTOM_HEADER),
                (String)requestContext.getHeaders().getFirst(METHOD_HEADER));
        if(!this.authenticate(requestContext, credentials, "BASIC")) {
            throw new WebApplicationException(this.unauthorizedHandler.buildResponse(this.prefix, this.realm));
        }
    }

    @Nullable
    private Credentials getCredentials(String header, String method) {
        if(header == null) {
            return null;
        } else {
            return new Credentials(header,method == null ? NearbyUtils.FB_AUTH_METHOD : method);
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

