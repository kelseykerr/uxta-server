package com.iuxta.uxta.resources;

import com.codahale.metrics.annotation.Timed;
import com.iuxta.uxta.dto.RequestDto;
import com.iuxta.uxta.exception.*;
import com.iuxta.uxta.model.Request;
import com.iuxta.uxta.model.User;
import com.iuxta.uxta.service.RequestService;
import com.iuxta.uxta.service.ResponseService;
import com.iuxta.uxta.service.StripeService;
import com.iuxta.uxta.model.Response;
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
    private JacksonDBCollection<Response, String> responseCollection;
    private RequestService requestService;
    private ResponseService responseService;

    public RequestsResource(JacksonDBCollection<Request, String> requestCollection,
                            RequestService requestService,
                            JacksonDBCollection<Response, String> responseCollection,
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
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public void getRequestNotifications(@Auth @ApiParam(hidden = true) User principal) {
        if (principal.getNewRequestNotificationsEnabled() == null || !principal.getNewRequestNotificationsEnabled()) {
            return;
        }
        requestService.sendRecentRequestsNotification(principal);
    }


    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    @ApiOperation(
            value = "Search for requests",
            notes = "Return requests that match query params"
    )
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public List<RequestDto> getRequests(@Auth @ApiParam(hidden = true) User principal,
                                        @QueryParam("expired") Boolean expired,
                                        @QueryParam("includeMine") Boolean includeMine,
                                        @QueryParam("searchTerm") String searchTerm,
                                        @QueryParam("sort") String sort,
                                        @QueryParam("offset") Integer offset,
                                        @QueryParam("limit") Integer limit,
                                        @QueryParam("type") String type) {
        if (principal.getCommunityId() == null || principal.getCommunityId().isEmpty()) {
            String msg = "You must belong to a community to view posts from other users.";
            LOGGER.error("[" + principal.getId() + " - " + principal.getName() + "] " + msg);
            throw new NoCommunityException(msg);
        }
        List<Request> requests = requestService.findRequests(offset, limit, expired, includeMine,
                searchTerm, sort, principal, type);
        return RequestDto.transform(requests);
    }



    @POST
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public RequestDto createRequest(@Auth @ApiParam(hidden = true) User principal, @Valid RequestDto dto) {
        if (principal.getCommunityId() == null || principal.getCommunityId().isEmpty()) {
            String msg = "You must belong to a community to view posts from other users.";
            LOGGER.error("[" + principal.getId() + " - " + principal.getName() + "] " + msg);
            throw new NoCommunityException(msg);
        }
        if (!requestService.canCreateRequest(principal)) {
            throw new NotAllowedException("You have exceeded the maximum number of open requests.");
        }
        Request request = requestService.transformRequestDto(dto, principal);
        WriteResult<Request, String> newRequest = requestCollection.insert(request);
        request = newRequest.getSavedObject();
        requestService.sendAsyncPostNotifications(request);
        return new RequestDto(request);
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{requestId}")
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
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
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
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
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
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
