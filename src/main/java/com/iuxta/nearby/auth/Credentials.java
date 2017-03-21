package com.iuxta.nearby.auth;

/**
 * Created by kerrk on 7/8/16.
 */
public class Credentials {
    private final String token;

    private final String method;

    public Credentials(String token, String method) {
        this.token = token;
        this.method = method;
    }

    public String getToken() {
        return token;
    }

    public String getMethod() {
        return method;
    }
}
