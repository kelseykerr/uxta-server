package com.iuxta.nearby.auth;

/**
 * Created by kerrk on 7/8/16.
 */
public class Credentials {
    private final String token;

    private final String method;

    private final String ip;

    public Credentials(String token, String method, String ip) {
        this.token = token;
        this.method = method;
        this.ip = ip;
    }

    public String getToken() {
        return token;
    }

    public String getMethod() {
        return method;
    }

    public String getIp() {
        return ip;
    }
}
