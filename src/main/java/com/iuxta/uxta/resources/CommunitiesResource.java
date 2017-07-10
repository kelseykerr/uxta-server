package com.iuxta.uxta.resources;

import com.codahale.metrics.annotation.Timed;
import com.iuxta.uxta.dto.CommunityDto;
import com.iuxta.uxta.dto.RequestDto;
import com.iuxta.uxta.dto.UserDto;
import com.iuxta.uxta.exception.*;
import com.iuxta.uxta.model.Community;
import com.iuxta.uxta.model.Request;
import com.iuxta.uxta.model.User;
import com.iuxta.uxta.service.CommunityService;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Created by kelseykerr on 7/9/17.
 */
@Path("/communities")
@Api("/communities")
public class CommunitiesResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunitiesResource.class);

    private CommunityService communityService;

    public CommunitiesResource(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public List<CommunityDto> getCommunities(@Auth @ApiParam(hidden = true) User principal, @QueryParam("searchTerm") String searchTerm) {
        List<Community> communities = communityService.getCommunities(searchTerm);
        return CommunityDto.transform(communities);
    }

    @GET
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{communityId}")
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public CommunityDto getCommunityById(@Auth @ApiParam(hidden = true) User principal, @PathParam("communityId") String id) {
        Community community = communityService.getCommunityById(id);
        return new CommunityDto(community);
    }

    @POST
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{communityId}/users")
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public UserDto requestCommunityAccess(@Auth @ApiParam(hidden = true) User principal, @PathParam("communityId") String id) {
        User u = communityService.requestAccess(id, principal);
        return new UserDto(u);
    }

    @DELETE
    @Produces(value = MediaType.APPLICATION_JSON)
    @Path("/{communityId}/users")
    @Timed
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public UserDto removeCommunityAccess(@Auth @ApiParam(hidden = true) User principal, @PathParam("communityId") String id) {
        User u = communityService.removeAccess(id, principal);
        return new UserDto(u);
    }

}
