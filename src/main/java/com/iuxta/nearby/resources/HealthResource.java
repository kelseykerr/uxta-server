package com.iuxta.nearby.resources;

import com.codahale.metrics.annotation.Timed;
import com.iuxta.nearby.dto.UserDto;
import io.swagger.annotations.Api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/health")
@Api("/health")
public class HealthResource {

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    public UserDto doHealthCheck() {
        return new UserDto();
    }
}
