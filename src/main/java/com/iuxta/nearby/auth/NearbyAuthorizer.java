package com.iuxta.nearby.auth;

import com.iuxta.nearby.model.User;
import io.dropwizard.auth.Authorizer;

/**
 * Created by kerrk on 8/17/16.
 */
public class NearbyAuthorizer implements Authorizer<User> {

    public boolean authorize(User user, String role) {
        return user != null;
    }
}
