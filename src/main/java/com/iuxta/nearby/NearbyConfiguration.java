package com.iuxta.nearby;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

public class NearbyConfiguration extends Configuration {

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
    @Min(5235)
    @Max(5236)
    public int fcmPort;

    @JsonProperty
    @NotEmpty
    public String fcmApiKey;

    @JsonProperty
    @NotEmpty
    public String fcmSenderId;

    @JsonProperty
    @NotEmpty
    public String stripeSecretKey;

    @JsonProperty
    @NotEmpty
    public String stripePublishableKey;
}
