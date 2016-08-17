package com.impulsecontrol.lend;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
}
