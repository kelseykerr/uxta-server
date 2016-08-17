package com.impulsecontrol.lend.resources;

import com.codahale.metrics.annotation.Timed;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;


@Path("/user")
@Api("/user")
public class UserResource {

    private JacksonDBCollection<User, String> userCollection;
    private JacksonDBCollection<Request, String> requestCollection;


    public UserResource(JacksonDBCollection<User, String> userCollection,
                        JacksonDBCollection<Request, String> requestCollection) {
        this.userCollection = userCollection;
        this.requestCollection = requestCollection;
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    @ApiImplicitParams({ @ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header") })
    public User getCurrentUser(@Auth @ApiParam(hidden=true) User principal) {
        return principal;
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @Timed
    @ApiImplicitParams({
            @ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "id",
                    value = "the id of the user you wish to fetch info about",
                    dataType = "string",
                    paramType = "path",
                    required = true)
    })
    public UserDto getUser(@Auth @ApiParam(hidden=true) User principal, @PathParam("id") String id) {
        User user = userCollection.findOneById(id);
        //don't return user id
        user.setUserId(null);
        return new UserDto(user);
    }

    /*@POST
    @Timed
    public Response createUser(@Auth @ApiParam(hidden=true) User principal, @Valid User user) {
        WriteResult<User, String> result = userCollection.insert(user);
        URI uriOfCreatedResource = URI.create("/user");
        return Response.created(uriOfCreatedResource).build();
    }*/

    /*@DELETE
    @Path("/{id}")
    @Timed
    public Response deleteUser(@PathParam("id") String id) {

    }*/

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/requests")
    @Timed
    @ApiOperation(
            value = "get my requests",
            notes = "get all requests created by this authenticated user"
    )
    @ApiImplicitParams({ @ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header") })
    public List<Request> getAllUserRequests(@Auth @ApiParam(hidden=true) User principal) {
        DBObject searchByUser = new BasicDBObject("user", principal);
        DBCursor userRequests = requestCollection.find(searchByUser).sort(new BasicDBObject("postDate", -1));
        List<Request> requests = userRequests.toArray();
        userRequests.close();
        return requests;
    }
}
