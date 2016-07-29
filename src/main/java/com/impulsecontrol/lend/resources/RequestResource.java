package com.impulsecontrol.lend.resources;

import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.RequestService;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.sun.jersey.api.NotFoundException;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.metrics.annotation.Timed;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;

import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

/**
 * Created by kerrk on 7/26/16.
 */
@Path("/request")
public class RequestResource {

    private JacksonDBCollection<Request, String> requestCollection;
    private RequestService service;

    public RequestResource(JacksonDBCollection<Request, String> requestCollection, RequestService requestService) {
        this.requestCollection = requestCollection;
        this.service = requestService;
    }

    @POST
    @Timed
    public Response createRequest(@Auth User principal, @Valid RequestDto dto) {
        Request request = service.transformRequestDto(dto, principal);
        WriteResult<Request, String> newRequest = requestCollection.insert(request);
        URI uriOfCreatedResource = URI.create("/request");
        return Response.created(uriOfCreatedResource).build();
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    public List<Request> getAllUserRequests(@Auth User principal) {
        DBObject searchByUser = new BasicDBObject("user", principal);
        DBCursor userRequests = requestCollection.find(searchByUser).sort(new BasicDBObject("postDate", -1));
        return userRequests.toArray();
    }

    @DELETE
    @Timed
    @Path("/{id}")
    public Response deleteRequest(@Auth User principal, @PathParam("id") String id) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            throw new NotFoundException("unable to find request [" + id + "]");
        }
        if (request.getUser().getUserId() != principal.getUserId()) {
            throw new UnauthorizedException("You are not authorized to delete this request");
        }
        BasicDBObject document = new BasicDBObject();
        document.put("id", id);
        requestCollection.remove(document);
        return Response.noContent().build();

    }



}
