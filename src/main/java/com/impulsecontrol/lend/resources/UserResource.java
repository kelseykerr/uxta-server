package com.impulsecontrol.lend.resources;

import com.impulsecontrol.lend.model.User;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.metrics.annotation.Timed;
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


@Path("/user")
public class UserResource {

    private JacksonDBCollection<User, String> userCollection;

    public UserResource(JacksonDBCollection<User, String> userCollection) {
        this.userCollection = userCollection;
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
        return user;
    }

    @POST
    @Timed
    public Response createUser(@Valid User user) {
        //TODO: check result for error
        WriteResult<User, String> result = userCollection.insert(user);
        URI uriOfCreatedResource = URI.create("/user");
        return Response.created(uriOfCreatedResource).build();
    }

    /*@DELETE
    @Path("/{id}")
    @Timed
    public Response deleteUser(@PathParam("id") String id) {

    }*/
}
