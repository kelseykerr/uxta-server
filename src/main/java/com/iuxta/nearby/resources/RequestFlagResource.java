package com.iuxta.nearby.resources;

import com.codahale.metrics.annotation.Timed;
import com.iuxta.nearby.dto.RequestDto;
import com.iuxta.nearby.dto.RequestFlagDto;
import com.iuxta.nearby.model.Request;
import com.iuxta.nearby.model.RequestFlag;
import com.iuxta.nearby.model.User;
import com.iuxta.nearby.service.RequestFlagService;
import com.iuxta.nearby.service.RequestService;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * Created by kelseykerr on 5/15/17.
 */
@Path("/requests/{requestId}/flags")
@Api("/requests/{requestId}/flags")
public class RequestFlagResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestFlagResource.class);
    private RequestFlagService requestFlagService;

    public RequestFlagResource(RequestFlagService requestFlagService) {
        this.requestFlagService = requestFlagService;
    }

    @POST
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public RequestFlagDto getRequestFlags(@Auth @ApiParam(hidden = true) User principal,
                                                @PathParam("requestId") String requestId, @Valid RequestFlagDto flagDto) {
        RequestFlag flag = requestFlagService.createRequestFlag(principal, flagDto, requestId);
        return new RequestFlagDto(flag);
    }

}
