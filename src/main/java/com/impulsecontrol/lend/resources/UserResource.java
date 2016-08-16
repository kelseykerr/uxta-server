package com.impulsecontrol.lend.resources;

import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.metrics.annotation.Timed;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;

import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;


@Path("/user")
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
    public User getCurrentUser(@Auth User principal) {
        return principal;
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @Timed
    public User getUser(@PathParam("id") String id) {
        User user = userCollection.findOneById(id);
        //don't return user id
        user.setUserId(null);
        return user;
    }

    @POST
    @Timed
    public Response createUser(@Valid User user) {
        WriteResult<User, String> result = userCollection.insert(user);
        URI uriOfCreatedResource = URI.create("/user");
        return Response.created(uriOfCreatedResource).build();
    }

    /*@DELETE
    @Path("/{id}")
    @Timed
    public Response deleteUser(@PathParam("id") String id) {

    }*/

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/requests")
    @Timed
    public List<Request> getAllUserRequests(@Auth User principal) {
        DBObject searchByUser = new BasicDBObject("user", principal);
        DBCursor userRequests = requestCollection.find(searchByUser).sort(new BasicDBObject("postDate", -1));
        List<Request> requests = userRequests.toArray();
        userRequests.close();
        return requests;
    }
}
