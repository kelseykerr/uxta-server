package com.iuxta.nearby.resources;

import com.codahale.metrics.annotation.Timed;
import com.iuxta.nearby.dto.ResponseDto;
import com.iuxta.nearby.dto.ResponseFlagDto;
import com.iuxta.nearby.dto.UserDto;
import com.iuxta.nearby.exception.NotAllowedException;
import com.iuxta.nearby.exception.NotFoundException;
import com.iuxta.nearby.exception.UnauthorizedException;
import com.iuxta.nearby.model.Request;
import com.iuxta.nearby.model.Response;
import com.iuxta.nearby.model.ResponseFlag;
import com.iuxta.nearby.model.User;
import com.iuxta.nearby.service.StripeService;
import com.iuxta.nearby.service.ResponseService;
import com.mongodb.BasicDBObject;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
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
 * Created by kerrk on 9/5/16.
 */
@Path("/requests/{requestId}/responses")
@Api("/responses")
public class ResponsesResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestsResource.class);

    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<Response, String> responseCollection;
    private JacksonDBCollection<User, String> userCollection;
    private ResponseService responseService;
    private StripeService stripeService;

    public ResponsesResource(JacksonDBCollection<Request, String> requestCollection,
                             JacksonDBCollection<Response, String> responseCollection,
                             ResponseService responseService, JacksonDBCollection<User, String> userCollection,
                             StripeService stripeService) {
        this.requestCollection = requestCollection;
        this.responseCollection = responseCollection;
        this.responseService = responseService;
        this.userCollection = userCollection;
        this.stripeService = stripeService;
    }

    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public List<ResponseDto> getRequestResponses(@Auth @ApiParam(hidden = true) User principal,
                                                 @PathParam("requestId") String id,
                                                 @QueryParam("responder")
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
                    " by specifying the query parameter \"responder=me\"";
            LOGGER.error(msg);
            throw new UnauthorizedException(msg);
        }
        seller = seller != null && seller.equals("me") ? principal.getId() : seller;
        BasicDBObject query = new BasicDBObject();
        query.append("requestId", id);
        if (seller != null) {
            query.append("sellerId", seller);
        }

        DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
        List<Response> responses = requestResponses.toArray();
        requestResponses.close();
        List<ResponseDto> responsesDto = ResponseDto.transform(responses);
        responsesDto.forEach(r -> {
            User u = userCollection.findOneById(r.responderId);
            UserDto userDto = new UserDto();
            userDto.userId = u.getId();
            userDto.lastName = u.getLastName();
            userDto.firstName = u.getFirstName();
            userDto.fullName = u.getName();
            r.responder = userDto;
            r.seller = userDto;
        });
        return responsesDto;
    }

    @POST
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public ResponseDto addResponse(@Auth @ApiParam(hidden = true) User principal,
                                   @PathParam("requestId") String id,
                                   @Valid ResponseDto dto) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            String msg = "unable to create response for request [" + id + "], " +
                    "because the request was not found";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        if (principal.getId().equals(request.getUser().getId())) {
            String msg = "You cannot create an offer for your own request";
            LOGGER.error(msg);
            throw new NotAllowedException(msg);
        }
        if (!request.isInventoryListing() && (principal.getStripeManagedAccountId() == null || !stripeService.canAcceptTransfers(principal))) {
            LOGGER.error("User [" + principal.getId() + "] tried to create an offer without a valid bank account");
            throw new NotAllowedException("Cannot create offer because you do not have a valid bank account setup");
        } else if (request.isInventoryListing() && !stripeService.hasCustomerAccount(principal)) {
            LOGGER.error("User [" + principal.getId() + "] tried to create an inventory response without a valid payment info");
            throw new NotAllowedException("Cannot create offer because you do not have not added valid payment info");
        }
        BasicDBObject query = new BasicDBObject();
        query.append("requestId", id);
        query.append("sellerId", principal.getId());
        DBCursor myResponses = responseCollection.find(query);
        List<Response> responses = myResponses.toArray();
        myResponses.close();

        // users can only make 1 offer/request
        if (responses != null && responses.size() > 0) {
            String msg = "You already made an offer for this request.";
            LOGGER.error(msg);
            throw new NotAllowedException(msg);
        }
        if (!responseService.canCreateResponse(principal)) {
            throw new NotAllowedException("You have exceeded the maximum number of open/pending offers");
        }
        Response response = responseService.transformResponseDto(dto, request, principal);
        return new ResponseDto(response);
    }

    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{responseId}")
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
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
        if (response.getResponderId().equals(principal.getId())) {
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
        ResponseDto responseDto = new ResponseDto(response);
        User seller = userCollection.findOneById(response.getResponderId());
        UserDto userDto = new UserDto();
        userDto.userId = seller.getId();
        userDto.lastName = seller.getLastName();
        userDto.firstName = seller.getFirstName();
        userDto.fullName = seller.getName();
        responseDto.responder = userDto;
        responseDto.seller = userDto;
        return responseDto;
    }

    @PUT
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Path("/{responseId}")
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public ResponseDto updateResponse(@Auth @ApiParam(hidden = true) User principal,
                                      @PathParam("requestId") String requestId,
                                      @PathParam("responseId") String responseId,
                                      @Valid ResponseDto dto) {
        Response response = responseCollection.findOneById(responseId);
        if (response == null) {
            String msg = "unable to update response [" + responseId + "] because the response was not found";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        if (response.getInappropriate()) {
         String msg = "Unable to update this response. It has been marked as inappropriate and has been hidden.";
            LOGGER.error(msg);
            throw new NotAllowedException(msg);
        }
        Request request = requestCollection.findOneById(requestId);
        if (request == null) {
            String msg = "could not find request [" + requestId + "]";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        if (!response.getResponderId().equals(principal.getId()) && !request.getUser().getId().equals(principal.getId())) {
            LOGGER.error("user [" + principal.getId() + "] attempted to update response [" +
                    response.getId() + "].");
            throw new UnauthorizedException("you do not have access to this response");
        }
        responseService.updateResponse(dto, response, request, principal.getId());
        return new ResponseDto(response);
    }

    @POST
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Path("/{responseId}/flags")
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public ResponseFlagDto flagResponse(@Auth @ApiParam(hidden = true) User principal,
                                        @PathParam("requestId") String requestId,
                                        @PathParam("responseId") String responseId,
                                        @Valid ResponseFlagDto dto) {
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
        if (!response.getResponderId().equals(principal.getId()) && !request.getUser().getId().equals(principal.getId())) {
            LOGGER.error("user [" + principal.getId() + "] attempted to flag response [" +
                    response.getId() + "].");
            throw new UnauthorizedException("you do not have access to this response");
        }
        ResponseFlag flag = responseService.flagResponse(principal, dto, response);
        return new ResponseFlagDto(flag);
    }
}
