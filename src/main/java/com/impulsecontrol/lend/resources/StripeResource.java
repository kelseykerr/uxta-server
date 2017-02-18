package com.impulsecontrol.lend.resources;

import com.codahale.metrics.annotation.Timed;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.StripeService;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Created by kerrk on 10/16/16.
 */
@Path("/stripe")
@Api("/stripe")
public class StripeResource {

    private StripeService stripeService;

    public StripeResource(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @POST
    @Path("/webhooks")
    @Consumes(value = MediaType.APPLICATION_FORM_URLENCODED)
    @Timed
    public void submerchantStatus(@FormParam("bt_signature") String signature,
                                 @FormParam("bt_payload") String payload) {
        stripeService.handleWebhookResponse(signature, payload);
    }

    @POST
    @Path("/bank")
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
    public UserDto addBankAccount(@Auth @ApiParam(hidden=true) User principal,  @Valid UserDto userDto) {
        stripeService.saveBankAccount(principal, userDto);
        userDto = new UserDto(principal);
        userDto.canRespond = stripeService.canAcceptTransfers(principal);
        userDto.canRequest = stripeService.hasCustomerAccount(principal);
        return userDto;
    }


    @POST
    @Path("/creditcard")
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
    public UserDto addCreditCard(@Auth @ApiParam(hidden=true) User principal, @Valid UserDto userDto) {
        System.out.println("***successfully hit endpoint");
        stripeService.saveCreditCard(principal, userDto);
        userDto = new UserDto(principal);
        userDto.canRespond = stripeService.canAcceptTransfers(principal);
        userDto.canRequest = stripeService.hasCustomerAccount(principal);
        return userDto;
    }

}
