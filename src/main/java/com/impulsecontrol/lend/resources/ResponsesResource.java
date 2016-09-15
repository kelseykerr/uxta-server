package com.impulsecontrol.lend.resources;

import com.codahale.metrics.annotation.Timed;
import com.impulsecontrol.lend.dto.ResponseDto;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.exception.NotFoundException;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.Response;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.ResponseService;
import com.mongodb.BasicDBObject;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
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
    private JacksonDBCollection<com.impulsecontrol.lend.model.Response, String> responseCollection;
    private JacksonDBCollection<User, String> userCollection;
    private ResponseService responseService;

    public ResponsesResource(JacksonDBCollection<Request, String> requestCollection,
                            JacksonDBCollection<com.impulsecontrol.lend.model.Response, String> responseCollection,
                            ResponseService responseService, JacksonDBCollection<User, String> userCollection) {
        this.requestCollection = requestCollection;
        this.responseCollection = responseCollection;
        this.responseService = responseService;
        this.userCollection = userCollection;
    }

    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
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
            User u = userCollection.findOneById(r.sellerId);
            UserDto userDto = new UserDto();
            userDto.userId = u.getId();
            userDto.lastName = u.getLastName();
            userDto.firstName = u.getFirstName();
            userDto.fullName = u.getName();
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
            paramType = "header")})
    public ResponseDto addResponse(@Auth @ApiParam(hidden = true) User principal,
                                                 @PathParam("requestId") String id,
                                                 @Valid ResponseDto dto) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            String msg = "unable to return responses for request [" + id + "], " +
                    "because the request was not found";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
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
        ResponseDto responseDto = new ResponseDto(response);
        User seller = userCollection.findOneById(response.getSellerId());
        UserDto userDto = new UserDto();
        userDto.userId = seller.getId();
        userDto.lastName = seller.getLastName();
        userDto.firstName = seller.getFirstName();
        userDto.fullName = seller.getName();
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
            paramType = "header")})
    @ApiOperation(
            value = "Update a response - in progress!!",
            notes = "this method has not been fully implemented!!"
    )
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
        //TODO: finish this!
        return null;
    }
}
