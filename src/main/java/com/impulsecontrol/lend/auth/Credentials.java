package com.impulsecontrol.lend.auth;

/**
 * Created by kerrk on 7/8/16.
 */
public class Credentials {
    private final String token;

    public Credentials(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
