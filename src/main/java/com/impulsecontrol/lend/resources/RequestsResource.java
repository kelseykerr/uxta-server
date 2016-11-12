package com.impulsecontrol.lend.resources;

import com.codahale.metrics.annotation.Timed;
import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.exception.BadRequestException;
import com.impulsecontrol.lend.exception.NotAllowedException;
import com.impulsecontrol.lend.exception.NotFoundException;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.RequestService;
import com.impulsecontrol.lend.service.ResponseService;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Created by kerrk on 7/26/16.
 */
@Path("/requests")
@Api("/requests")
public class RequestsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestsResource.class);

    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<com.impulsecontrol.lend.model.Response, String> responseCollection;
    private RequestService requestService;
    private ResponseService responseService;

    public RequestsResource(JacksonDBCollection<Request, String> requestCollection,
                            RequestService requestService,
                            JacksonDBCollection<com.impulsecontrol.lend.model.Response, String> responseCollection,
                            ResponseService responseService) {
        this.requestCollection = requestCollection;
        this.requestService = requestService;
        this.responseCollection = responseCollection;
        this.responseService = responseService;
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/notifications")
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public void getRequestNotifications(@Auth @ApiParam(hidden = true) User principal,
                                        @QueryParam("longitude") Double longitude,
                                        @QueryParam("latitude") Double latitude) {
        if (principal.getNewRequestNotificationsEnabled() == null || !principal.getNewRequestNotificationsEnabled()) {
            return;
        }
        requestService.sendRecentRequestsNotification(principal, longitude, latitude);
    }


    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    @ApiOperation(
            value = "Search for requests",
            notes = "Return requests that match query params (longitude, latitude, & radius)"
    )
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public List<RequestDto> getRequests(@Auth @ApiParam(hidden = true) User principal,
                                        @QueryParam("longitude") Double longitude,
                                        @QueryParam("latitude") Double latitude,
                                        @QueryParam("radius") Double radius,
                                        @QueryParam("expired") Boolean expired,
                                        @QueryParam("includeMine") Boolean includeMine) {
        if (longitude == null || latitude == null || radius == null) {
            String msg = "query parameters [radius], [longitude] and [latitude] are required.";
            LOGGER.error(msg);
            throw new BadRequestException(msg);
        }
        List<Request> requests = requestService.findRequests(latitude, longitude, radius, expired, includeMine, principal);
        return RequestDto.transform(requests);
    }

    public Double milesToMeters(Double radiusInMiles) {
        return radiusInMiles * 1609.344;
    }


    @POST
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public RequestDto createRequest(@Auth @ApiParam(hidden = true) User principal, @Valid RequestDto dto) {
        //TODO: take our Kei bypass when he has this set up
        if (principal.getId() != "190639591352732" && (principal.isPaymentSetup() == null || !principal.isPaymentSetup())) {
            LOGGER.error("User [" + principal.getId() + "] tried to make a request without adding a valid payment method");
            throw new NotAllowedException("Cannot create request because you have not added a valid payment method to your account");
        }
        Request request = requestService.transformRequestDto(dto, principal);
        WriteResult<Request, String> newRequest = requestCollection.insert(request);
        request = newRequest.getSavedObject();
        return new RequestDto(request);
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{requestId}")
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public RequestDto getRequestById(@Auth @ApiParam(hidden = true) User principal, @PathParam("requestId") String id) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            String msg = "Request [" + id + "] was not found.";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        return new RequestDto(request);
    }

    @PUT
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Path("/{requestId}")
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public RequestDto updateRequest(@Auth @ApiParam(hidden = true) User principal, @PathParam("requestId") String id,
                              @Valid RequestDto dto) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            String msg = "Request [" + id + "] was not found.";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        if (!request.getUser().getUserId().equals(principal.getUserId())) {
            String msg = "You do not have access to update this request.";
            LOGGER.error(msg);
            throw new UnauthorizedException(msg);
        }
        requestService.populateRequest(request, dto);
        requestCollection.save(request);
        return new RequestDto(request);
    }


    @DELETE
    @Timed
    @Path("/{requestId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public javax.ws.rs.core.Response deleteRequest(@Auth @ApiParam(hidden = true) User principal, @PathParam("requestId") String id) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            String msg = "Request [" + id + "] was not found.";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        if (!request.getUser().getUserId().equals(principal.getUserId())) {
            String msg = "You are not authorized to delete this request";
            LOGGER.error(msg);
            throw new UnauthorizedException(msg);
        }
        requestCollection.removeById(id);
        return javax.ws.rs.core.Response.noContent().build();

    }
}
