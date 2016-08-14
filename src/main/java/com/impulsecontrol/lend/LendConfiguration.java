package com.impulsecontrol.lend;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;
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

}
