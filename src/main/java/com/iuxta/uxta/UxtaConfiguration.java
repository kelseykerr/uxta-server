package com.iuxta.uxta;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

public class UxtaConfiguration extends Configuration {

    @JsonProperty
    @NotEmpty
    public String mongoUri;

    @JsonProperty
    @NotEmpty
    public String mongodb;

    @JsonProperty
    @NotEmpty
    public String fbAccessToken;

    @JsonProperty
    @NotEmpty
    public List<String> googleClientIds;

    @JsonProperty("swagger")
    public SwaggerBundleConfiguration swaggerBundleConfiguration;

    @JsonProperty
    @NotEmpty
    public String fcmServer;

    @JsonProperty
    public String fcmPort;

    @JsonProperty
    @NotEmpty
    public String fcmApiKey;

    @JsonProperty
    @NotEmpty
    public String fcmSenderId;
}
