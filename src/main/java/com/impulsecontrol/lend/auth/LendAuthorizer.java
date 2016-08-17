package com.impulsecontrol.lend.auth;

import com.impulsecontrol.lend.model.User;
import io.dropwizard.auth.Authorizer;

/**
 * Created by kerrk on 8/17/16.
 */
public class LendAuthorizer implements Authorizer<User> {

    public boolean authorize(User user, String role) {
        return user != null;
    }
}
