package com.impulsecontrol.lend.resources;

import com.codahale.metrics.annotation.Timed;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.BraintreeService;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header") })
    public String getClientToken(@Auth @ApiParam(hidden=true) User principal) {
        return braintreeService.getBraintreeClientToken();
    }

    @POST
    @Path("/webhooks")
    @Consumes(value = MediaType.APPLICATION_FORM_URLENCODED)
    @Timed
    public void submerchantStatus(@FormParam("bt_signature") String signature,
                                 @FormParam("bt_payload") String payload) {
        braintreeService.handleWebhookResponse(signature, payload);
    }

    @POST
    @Path("/merchant")
    @Produces(value = MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header") })
    public UserDto createMerchantDestination(@Auth @ApiParam(hidden=true) User principal,  @Valid UserDto userDto) {
        User user = braintreeService.saveOrUpdateMerchantAccount(principal, userDto);
        return new UserDto(user);
    }

    @DELETE
    @Path("/merchant")
    @Produces(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header") })
    public UserDto deleteMerchantDestination(@Auth @ApiParam(hidden=true) User principal) {
        User user = braintreeService.removeMerchantDestination(principal);
        return new UserDto(user);
    }

    @POST
    @Path("/customer")
    @Produces(value = MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header") })
    public UserDto createOrUpdateCustomerAccount(@Auth @ApiParam(hidden=true) User principal, @Valid UserDto userDto) {
        User user = braintreeService.saveOrUpdateCustomerAccount(principal, userDto);
        return new UserDto(user);
    }

    @DELETE
    @Path("/customer")
    @Produces(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header") })
    public UserDto deleteCustomerPayment(@Auth @ApiParam(hidden=true) User principal) {
        User user = braintreeService.removeCustomerPayment(principal);
        return new UserDto(user);
    }

}
