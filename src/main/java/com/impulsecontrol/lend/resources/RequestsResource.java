package com.impulsecontrol.lend.resources;

import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.exception.BadRequestException;
import com.impulsecontrol.lend.exception.NotFoundException;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.service.RequestService;
import com.mongodb.BasicDBObject;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.metrics.annotation.Timed;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;

import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

/**
 * Created by kerrk on 7/26/16.
 */
@Path("/requests")
public class RequestsResource {

    private JacksonDBCollection<Request, String> requestCollection;
    private RequestService service;

    public RequestsResource(JacksonDBCollection<Request, String> requestCollection, RequestService requestService) {
        this.requestCollection = requestCollection;
        this.service = requestService;
    }


    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    public List<Request> getRequests(@Auth User principal,
                                     @QueryParam("longitude") Double longitude,
                                     @QueryParam("latitude") Double latitude,
                                     @QueryParam("radius") Double radius) {
        if (longitude == null || latitude == null || radius == null) {
            throw new BadRequestException("query parameters [radius], [longitude] and [latitude] are required.");
        }
        BasicDBObject geometry = new BasicDBObject();
        geometry.append("type", "Point");
        double[] coords = {longitude, latitude};
        geometry.append("coordinates", coords);

        BasicDBObject near = new BasicDBObject();
        near.append("$geometry", geometry);
        near.append("$maxDistance",  Math.toRadians(milesToDegrees(radius)));

        BasicDBObject location = new BasicDBObject();
        location.append("$near", near);

        BasicDBObject locationQuery = new BasicDBObject();
        locationQuery.append("location", location);

        DBCursor userRequests = requestCollection.find(locationQuery).sort(new BasicDBObject("postDate", -1));
        List<Request> requests =  userRequests.toArray();
        userRequests.close();
        return requests;
    }

    public Double milesToDegrees(Double radiusInMiles) {
        Double km = radiusInMiles * 0.621371;
        return (1 / 110.54) * km;
    }


    @POST
    @Timed
    public Response createRequest(@Auth User principal, @Valid RequestDto dto) {
        Request request = service.transformRequestDto(dto, principal);
        WriteResult<Request, String> newRequest = requestCollection.insert(request);
        URI uriOfCreatedResource = URI.create("/requests");
        return Response.created(uriOfCreatedResource).build();
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{requestId}")
    @Timed
    public RequestDto getRequestById(@Auth User principal, @PathParam("requestId") String id) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            throw new NotFoundException("Request [" + id + "] was not found.");
        }
        return new RequestDto(request);
    }

    @PUT
    @Path("/{requestId}")
    @Timed
    public void updateRequest(@Auth User principal, @PathParam("requestId") String id, @Valid RequestDto dto) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            throw new NotFoundException("Could not find request [" + id + "]");
        }
        if (!request.getUser().getUserId().equals(principal.getUserId())) {
            throw new UnauthorizedException("You do not have access to update this request.");
        }
        service.populateRequest(request, dto);
        requestCollection.save(request);
    }


    @DELETE
    @Timed
    @Path("/{id}")
    public Response deleteRequest(@Auth User principal, @PathParam("id") String id) {
        Request request = requestCollection.findOneById(id);
        if (request == null) {
            throw new NotFoundException("unable to find request [" + id + "]");
        }
        if (!request.getUser().getUserId().equals(principal.getUserId())) {
            throw new UnauthorizedException("You are not authorized to delete this request");
        }
        BasicDBObject document = new BasicDBObject();
        document.put("id", id);
        requestCollection.remove(document);
        return Response.noContent().build();

    }



}
