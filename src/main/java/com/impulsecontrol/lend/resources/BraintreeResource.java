package com.impulsecontrol.lend.resources;

import com.codahale.metrics.annotation.Timed;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.BraintreeService;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * Created by kerrk on 10/16/16.
 */
@Path("/braintree")
@Api("/braintree")
public class BraintreeResource {

    private BraintreeService braintreeService;

    public BraintreeResource(BraintreeService braintreeService) {
        this.braintreeService = braintreeService;
    }

    @GET
    @Path("/token")
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    @ApiImplicitParams({ @ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header") })
    public String getClientToken(@Auth @ApiParam(hidden=true) User principal) {
        return braintreeService.getBraintreeClientToken();
    }

    @POST
    @Path("/webhooks")
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    public void submerchantStatus(@QueryParam("bt_signature") String signature,
                                 @QueryParam("bt_payload") String payload) {
        braintreeService.handleWebhookResponse(signature, payload);
    }
}