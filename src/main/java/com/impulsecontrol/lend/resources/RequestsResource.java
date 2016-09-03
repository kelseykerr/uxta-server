package com.impulsecontrol.lend.resources;

import com.codahale.metrics.annotation.Timed;
import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.dto.ResponseDto;
import com.impulsecontrol.lend.exception.BadRequestException;
import com.impulsecontrol.lend.exception.NotFoundException;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.Response;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.RequestService;
import com.impulsecontrol.lend.service.ResponseService;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.rmi.runtime.Log;

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
import java.net.URI;
import java.util.Date;
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
        BasicDBObject geometry = new BasicDBObject();
        geometry.append("type", "Point");
        double[] coords = {longitude, latitude};
        geometry.append("coordinates", coords);

        BasicDBObject near = new BasicDBObject();
        near.append("$geometry", geometry);
        near.append("$maxDistance", milesToMeters(radius));

        BasicDBObject location = new BasicDBObject();
        location.append("$near", near);

        BasicDBObject query = new BasicDBObject();
        query.append("location", location);

        if (expired != null && expired) {
            BasicDBObject expiredQuery = new BasicDBObject();
            expiredQuery.append("$lte", new Date());
            query.put("expireDate", expiredQuery);
        } else if (expired != null && !expired) {
            // expire date is after current date
            BasicDBObject notExpiredQuery = new BasicDBObject();
            notExpiredQuery.append("$gt", new Date());
            BasicDBObject query1 = new BasicDBObject();
            query1.append("expireDate", notExpiredQuery);

            // expire date is not set
            BasicDBObject notSetQuery = new BasicDBObject();
            notSetQuery.append("$exists", false);
            BasicDBObject query2 = new BasicDBObject();
            query2.append("expireDate", notSetQuery);

            BasicDBList or = new BasicDBList();
            or.add(query1);
            or.add(query2);
            query.put("$or", or);
        }

        if (includeMine != null && !includeMine) {
            BasicDBObject notMineQuery = new BasicDBObject();
            notMineQuery.append("$ne", principal.getUserId());
            query.put("user.userId", notMineQuery);
        }

        DBCursor userRequests = requestCollection.find(query).sort(new BasicDBObject("postDate", -1));
        List<Request> requests = userRequests.toArray();
        userRequests.close();
        return RequestDto.transform(requests);
    }

    public Double milesToMeters(Double radiusInMiles) {
        return radiusInMiles * 1609.344;
    }


    @POST
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public javax.ws.rs.core.Response createRequest(@Auth @ApiParam(hidden = true) User principal, @Valid RequestDto dto) {
        Request request = requestService.transformRequestDto(dto, principal);
        WriteResult<Request, String> newRequest = requestCollection.insert(request);
        URI uriOfCreatedResource = URI.create("/requests");
        return javax.ws.rs.core.Response.created(uriOfCreatedResource).build();
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
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public void updateRequest(@Auth @ApiParam(hidden = true) User principal, @PathParam("requestId") String id,
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
    }


    @DELETE
    @Timed
    @Path("/{requestId}")
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

    @GET
    @Timed
    @Path("/{requestId}/responses")
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public List<ResponseDto> getRequestResponses(@Auth @ApiParam(hidden = true) User principal,
                                                 @PathParam("requestId") String id,
                                                 @QueryParam("seller")
                                                 @ApiParam(value = "gets responses from a certain user, set to \"me\" to view your responses to a request")
                                                 String seller) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            String msg = "unable to return responses for request [" + id + "], " +
                    "because the request was not found";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        boolean isSellerMe = seller != null && (seller.equals("me") || seller.equals(principal.getId()));
        if (!request.getUser().getId().equals(principal.getId()) && !isSellerMe) {
            String msg = "You are not authorized to get the responses for request [" + id + "]. " +
                    "You can only get all responses for your request, or you can get your responses to a request" +
                    " by specifying the query parameter \"seller=me\"";
            LOGGER.error(msg);
            throw new UnauthorizedException(msg);
        }
        seller = seller.equals("me") ? principal.getId() : seller;
        BasicDBObject query = new BasicDBObject();
        query.append("requestId", id);
        if (seller != null) {
            query.append("sellerId", seller);
        }

        DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
        List<Response> responses = requestResponses.toArray();
        requestResponses.close();
        return ResponseDto.transform(responses);
    }

    @POST
    @Timed
    @Path("/{requestId}/responses")
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public javax.ws.rs.core.Response addResponse(@Auth @ApiParam(hidden = true) User principal,
                                                 @PathParam("requestId") String id,
                                                 @Valid ResponseDto dto) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            String msg = "unable to return responses for request [" + id + "], " +
                    "because the request was not found";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        responseService.transformResponseDto(dto, request, principal.getId());
        URI uriOfCreatedResource = URI.create("/requests/" + id + "/responses");
        return javax.ws.rs.core.Response.created(uriOfCreatedResource).build();
    }

    @GET
    @Timed
    @Path("/{requestId}/responses/{responseId}")
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public ResponseDto getResponse(@Auth @ApiParam(hidden = true) User principal,
                                   @PathParam("requestId") String requestId,
                                   @PathParam("responseId") String responseId) {
        Response response = responseCollection.findOneById(responseId);
        if (response == null) {
            String msg = "response [" + responseId + "] was not found";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        if (response.getSellerId().equals(principal.getId())) {
            Request request = requestCollection.findOneById(requestId);
            if (request == null) {
                String msg = "unable to return response for request [" + requestId + "], " +
                        "because the request was not found";
                LOGGER.error(msg);
                throw new NotFoundException(msg);
            }
            if (!request.getUser().getId().equals(principal.getId())) {
                LOGGER.error("user [" + principal.getId() + "] attempted to get access to response [" +
                        response.getId() + "].");
                throw new UnauthorizedException("you do not have access to this response");
            }
        }
        return new ResponseDto(response);
    }

    @PUT
    @Timed
    @Path("/{requestId}/responses/{responseId}")
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header")})
    public void updateResponse(@Auth @ApiParam(hidden = true) User principal,
                              @PathParam("requestId") String requestId,
                              @PathParam("responseId") String responseId,
                              @Valid ResponseDto dto) {
        Response response = responseCollection.findOneById(responseId);
        if (response == null) {
            String msg = "unable to update response [" + responseId + "] because the response was not found";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        Request request = requestCollection.findOneById(requestId);
        if (request == null) {
            String msg = "could not find request [" + requestId + "]";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        if (!response.getSellerId().equals(principal.getId()) && !request.getUser().getId().equals(principal.getId())) {
            LOGGER.error("user [" + principal.getId() + "] attempted to update response [" +
                    response.getId() + "].");
            throw new UnauthorizedException("you do not have access to this response");
        }
    }



}
