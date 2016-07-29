package com.impulsecontrol.lend.resources;

import com.impulsecontrol.lend.model.CustomParams;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.SearchParams;
import com.impulsecontrol.lend.model.User;
import com.mongodb.BasicDBObject;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.metrics.annotation.Timed;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Created by kerrk on 7/27/16.
 */
@Path("/requests")
public class RequestsResource {

    private JacksonDBCollection<Request, String> requestCollection;

    public RequestsResource(JacksonDBCollection<Request, String> requestCollection) {
        this.requestCollection = requestCollection;
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    public List<Request> getRequests(@Auth User principal, @CustomParams SearchParams searchParams) {
        System.out.println("lat: " + searchParams.latitude);
        System.out.println("long: " + searchParams.longitude);
        BasicDBObject geoNearParams = new BasicDBObject();
        double[] near = {searchParams.getLongitude(), searchParams.getLatitude()};
        geoNearParams.append("near", near);
        geoNearParams.append("spherical", "true");
        geoNearParams.append("maxDistance", Math.toRadians(metersToDegrees(searchParams.getRadius())));
        geoNearParams.append("distanceField", "dist");

        BasicDBObject distanceQuery =  new BasicDBObject("$geoNear", geoNearParams);


        DBCursor userRequests = requestCollection.find(distanceQuery).sort(new BasicDBObject("postDate", -1));
        return userRequests.toArray();
    }

    public Float metersToDegrees(Double radiusInMeters) {
        return radiusInMeters.floatValue() / 111119.99965975954f;
    }
}
