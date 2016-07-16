package com.impulsecontrol.lend.auth;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.Parameter;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.server.impl.inject.AbstractHttpContextInjectable;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import com.google.common.base.Optional;

/**
 * Created by kerrk on 7/8/16.
 */
public class SecurityProvider<T> implements InjectableProvider<Auth, Parameter> {

    public final static String CUSTOM_HEADER = "x-auth-token";

    private final Authenticator<Credentials, T> authenticator;

    public SecurityProvider(Authenticator<Credentials, T> authenticator) {
        this.authenticator = authenticator;
    }

    private static class SecurityInjectable<T> extends AbstractHttpContextInjectable<T> {

        private final Authenticator<Credentials, T> authenticator;
        private final boolean required;

        private SecurityInjectable(Authenticator<Credentials, T> authenticator, boolean required) {
            this.authenticator = authenticator;
            this.required = required;
        }

        @Override
        public T getValue(HttpContext c) {
            final String header = c.getRequest().getHeaderValue(CUSTOM_HEADER);
            try {
                if (header != null) {
                    final Optional<T> result = authenticator.authenticate(new Credentials(header));
                    if (result.isPresent()) {
                        return result.get();
                    }
                }
            } catch (AuthenticationException e) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }

            if (required) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }

            return null;
        }
    }

    public ComponentScope getScope() {
        return ComponentScope.PerRequest;
    }

    public Injectable getInjectable(ComponentContext ic, Auth auth, Parameter parameter) {
        return new SecurityInjectable<T>(authenticator, auth.required());
    }

}
