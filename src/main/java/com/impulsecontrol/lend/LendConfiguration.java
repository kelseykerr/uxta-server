package com.impulsecontrol.lend;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

public class LendConfiguration extends Configuration {

    @JsonProperty
    @NotEmpty
    public String mongohost;

    @JsonProperty
    @Min(1)
    @Max(65535)
    public int mongoport;

    @JsonProperty
    @NotEmpty
    public String mongodb;

    @JsonProperty
    @NotEmpty
    public String fbAccessToken;

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
    public String btMerchantId;

    @JsonProperty
    @NotEmpty
    public String btPublicKey;

    @JsonProperty
    @NotEmpty
    public String btPrivateKey;
}
