package com.iuxta.nearby.resources;

import com.codahale.metrics.annotation.Timed;
import com.iuxta.nearby.dto.TransactionDto;
import com.iuxta.nearby.exception.IllegalArgumentException;
import com.iuxta.nearby.exception.NotFoundException;
import com.iuxta.nearby.firebase.CcsServer;
import com.iuxta.nearby.firebase.FirebaseUtils;
import com.iuxta.nearby.model.Request;
import com.iuxta.nearby.model.Response;
import com.iuxta.nearby.model.Transaction;
import com.iuxta.nearby.model.User;
import com.iuxta.nearby.service.StripeService;
import com.iuxta.nearby.service.TransactionService;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Created by kerrk on 9/22/16.
 */
@Path("/transactions/{transactionId}")
@Api("/transactions")
public class TransactionsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionsResource.class);

    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<Response, String> responseCollection;
    private JacksonDBCollection<User, String> userCollection;
    private JacksonDBCollection<Transaction, String> transactionCollection;
    private TransactionService transactionService;
    private CcsServer ccsServer;
    private StripeService stripeService;


    public TransactionsResource(JacksonDBCollection<Request, String> requestCollection,
                                JacksonDBCollection<Response, String> responseCollection,
                                JacksonDBCollection<User, String> userCollection,
                                JacksonDBCollection<Transaction, String> transactionCollection,
                                CcsServer ccsServer, StripeService stripeService) {
        this.requestCollection = requestCollection;
        this.responseCollection = responseCollection;
        this.userCollection = userCollection;
        this.transactionCollection = transactionCollection;
        this.transactionService = new TransactionService(transactionCollection, userCollection, ccsServer, requestCollection);
        this.ccsServer = ccsServer;
        this.stripeService = stripeService;
    }

    @GET
    @Timed
    @Produces(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public TransactionDto getTransaction(@Auth @ApiParam(hidden = true) User principal,
                                         @PathParam("transactionId") String transactionId) {
        Transaction transaction = getTransaction(transactionId, principal.getUserId());
        Request request = getRequest(transaction.getRequestId(), transactionId);
        Response response = getResponse(transaction.getResponseId(), transactionId);
        boolean isBuyer = request.getUser().getId().equals(principal.getId());
        boolean isSeller = response.getSellerId().equals(principal.getId());
        if (!isBuyer && !isSeller) {
            LOGGER.error("User [" + principal.getId() + "] attempted to access transaction [" + transactionId + "]");
            throw new NotAuthorizedException("You do not have access to this transaction!");
        }
        return new TransactionDto(transaction, isSeller);
    }

    @DELETE
    @Timed
    @Produces(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    public TransactionDto closeTransaction(@Auth @ApiParam(hidden = true) User principal,
                                         @PathParam("transactionId") String transactionId,
                                         @Valid TransactionDto dto) {
        Transaction transaction = getTransaction(transactionId, principal.getUserId());
        Request request = getRequest(transaction.getRequestId(), transactionId);
        Response response = getResponse(transaction.getResponseId(), transactionId);
        boolean isRequester = request.getUser().getId().equals(principal.getId());
        boolean isSeller = response.getSellerId().equals(principal.getId());
        if (!isRequester && !isSeller) {
            LOGGER.error("User [" + principal.getId() + "] tried to close transaction ["
                    + transactionId + "]");
            // return generic not found exception -- don't want to be too specific for security reasons
            throw new NotFoundException("Transaction not found");
        }
        transaction.setCanceler(principal.getId());
        transaction.setCanceledReason(dto.canceledReason);
        transaction.setCanceled(true);
        if (!isRequester) {
            // reopen the request so the requester can receive new offers
            request.setStatus(Request.Status.OPEN);
        } else {
            // if the requester cancels the transaction, close the request
            request.setStatus(Request.Status.CLOSED);
        }
        transactionCollection.save(transaction);
        response.setResponseStatus(Response.Status.CLOSED);
        response.setCanceledReason(dto.canceledReason);
        responseCollection.save(response);
        requestCollection.save(request);
        JSONObject notification = new JSONObject();
        notification.put("title", "Transaction Cancelled");
        notification.put("type", FirebaseUtils.NotificationTypes.cancelled_transaction.name());
        notification.put("reason", transaction.getCanceledReason());
        if (isRequester) {
            User seller = userCollection.findOneById(response.getSellerId());
            notification.put("message", seller.getFirstName() + " cancelled your transaction for a " + request.getItemName() + ".");
            FirebaseUtils.sendFcmMessage(seller, null, notification, ccsServer);
        } else {
            notification.put("message", request.getUser().getFirstName() + " cancelled your transaction for a " + request.getItemName() + ".");
            FirebaseUtils.sendFcmMessage(request.getUser(), null, notification, ccsServer);
        }
        return new TransactionDto(transaction, isSeller);
    }

    @GET
    @Timed
    @Path("/code")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    @ApiOperation(
            value = "Generate a code that can be exchanged or converted to a QR code to be scanned",
            notes = "The seller will call this method to generate the code on the initial exchange and " +
                    "the user will call this method to generate the code on the return. Codes expire after 3 minutes"
    )
    public String getTransactionCode(@Auth @ApiParam(hidden = true) User principal,
                                                 @PathParam("transactionId") String transactionId) {
        Transaction transaction = getTransaction(transactionId, principal.getUserId());
        Request request = getRequest(transaction.getRequestId(), transactionId);
        Response response = getResponse(transaction.getResponseId(), transactionId);
        return transactionService.generateCode(transaction, request, response, principal.getId());
    }

    @PUT
    @Timed
    @Path("/code/{code}")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    @ApiOperation(
            value = "Confirm the exchange/return has occurred by verifying the secret code either by scanning the QR " +
                    "code or manually entering the code",
            notes = "The buyer will call this method during the initial exchange by scanning or entering the exchange " +
                    "code and the seller will call this method on return."
    )
    public void enterTransactionCode(@Auth @ApiParam(hidden = true) User principal,
                                     @PathParam("transactionId") String transactionId,
                                     @PathParam("code") String code) {
        Transaction transaction = getTransaction(transactionId, principal.getUserId());
        Request request = getRequest(transaction.getRequestId(), transactionId);
        Response response = getResponse(transaction.getResponseId(), transactionId);
        boolean isBuyer = request.getUser().getId().equals(principal.getId());
        boolean isSeller = response.getSellerId().equals(principal.getId());
        if (isSeller) {
            transactionService.enterReturnCode(transaction, response, request, code);
        } else if (isBuyer) {
            transactionService.enterExchangeCode(transaction, response, request, code);
        } else {
            LOGGER.error("User [" + principal.getId() + "] tried to get code for transaction ["
                    + transactionId + "]");
            throw new NotAuthorizedException("Invalid code");
        }

    }

    @POST
    @Timed
    @Path("/exchange")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Produces(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    @ApiOperation(
            value = "If the users forgot to scan codes on the exchange or return, they can submit and override",
            notes = "If the seller forgot to do the scan on the initial exchange, they can submit an override by " +
                    "filling in the exchangeTime of the exchangeOverride object in the TransactionDto. If the buyer " +
                    "forgot to scan on the return, they can submit an override by filling in the returnTime on the " +
                    "returnOverride object in the TransactionDto. Only the price field is required in the dto."
    )
    public TransactionDto createExchangeOverride(@Auth @ApiParam(hidden = true) User principal,
                                                 @PathParam("transactionId") String transactionId,
                                                 @Valid TransactionDto dto) {
        Transaction transaction = getTransaction(transactionId, principal.getUserId());
        Request request = getRequest(transaction.getRequestId(), transactionId);
        Response response = getResponse(transaction.getResponseId(), transactionId);
        boolean isBuyer = request.getUser().getId().equals(principal.getId());
        boolean isSeller = response.getSellerId().equals(principal.getId());
        if (!isBuyer && !isSeller) {
            LOGGER.error("User [" + principal.getId() + "] attempted to create an exchange override for " +
                    " transaction [" + transactionId + "]");
            throw new NotAuthorizedException("You do not have access to this transaction!");
        }
        transactionService.createExchangeOverride(transaction, dto, isSeller, request.getRental(), request, response, principal);
        return new TransactionDto(transaction, isSeller);
    }

    @PUT
    @Timed
    @Path("/exchange")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Produces(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    @ApiOperation(
            value = "Confirm/deny the exchange/return override submitted by the other user",
            notes = "If the seller submitted an exchange override, the buyer will confirm or deny that the exchange " +
                    "happened by setting the buyerAccepted field in exchangeOverride object to true or false. " +
                    "If the buyer submitted a return override, the seller will accept or decline by setting the " +
                    "sellerStatus in the returnOverride object to true or false. "
    )
    public TransactionDto responseToExchangeOverride(@Auth @ApiParam(hidden = true) User principal,
                                                 @PathParam("transactionId") String transactionId,
                                                 @Valid TransactionDto dto) {
        Transaction transaction = getTransaction(transactionId, principal.getUserId());
        Request request = getRequest(transaction.getRequestId(), transactionId);
        Response response = getResponse(transaction.getResponseId(), transactionId);
        boolean isBuyer = request.getUser().getId().equals(principal.getId());
        boolean isSeller = response.getSellerId().equals(principal.getId());
        if (!isBuyer && !isSeller) {
            LOGGER.error("User [" + principal.getId() + "] attempted to respond to an exchange override for " +
                    " transaction [" + transactionId + "]");
            throw new NotAuthorizedException("You do not have access to this transaction!");
        }
        transactionService.respondToExchangeOverride(transaction, dto, response, isSeller, request.getRental(), principal, request);
        return new TransactionDto(transaction, isSeller);
    }

    @PUT
    @Timed
    @Path("/price")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Produces(value = MediaType.APPLICATION_JSON)
    @ApiImplicitParams({@ApiImplicitParam(name = "x-auth-token",
            value = "the authentication token received from facebook",
            dataType = "string",
            paramType = "header"),
            @ApiImplicitParam(name = "x-auth-method",
                    value = "the authentication method, either \"facebook\" (default if empty) or \"google\"",
                    dataType = "string",
                    paramType = "header")})
    @ApiOperation(
            value = "Confirm or update the final price that will be charged to the buyer",
            notes = "If the seller accepts the calculated price, simply leave the priceOverride field in the dto empty, " +
                    "otherwise the seller can DECREASE the price in the priceOverride field. The user CANNOT increase " +
                    "the price. The only field considered in the dto here is the priceOverride field, all others can be empty"
    )
    public TransactionDto verifyPrice(@Auth @ApiParam(hidden = true) User principal,
                                      @PathParam("transactionId") String transactionId,
                                      @Valid TransactionDto dto) {
        Transaction transaction = getTransaction(transactionId, principal.getUserId());
        Request request = getRequest(transaction.getRequestId(), transactionId);
        Response response = getResponse(transaction.getResponseId(), transactionId);
        if (!response.getSellerId().equals(principal.getId())) {
            LOGGER.error("User [" + principal.getId() + "] attempted to verify price for" +
                    " transaction [" + transactionId + "]");
            throw new NotAuthorizedException("You do not have access to verify the price for this transaction!");
        }
        if (transaction.getSellerAccepted() != null && transaction.getSellerAccepted()) {
            LOGGER.error("Seller [" + principal.getId() + "] attempted to verify price again for transaction ["
                    + transactionId + "]");
            throw new IllegalArgumentException("You already confirmed the price for this transaction!");
        }
        if (dto.priceOverride != null && dto.priceOverride.compareTo(transaction.getCalculatedPrice()) > 0) {
            LOGGER.error("Seller tried to increase total price for transaction [" + transactionId + "]");
            throw new IllegalArgumentException("You cannot increase the price!");

        }
        transaction.setFinalPrice(dto.priceOverride == null ? transaction.getCalculatedPrice() : dto.priceOverride);
        transaction.setSellerAccepted(true);
        transactionCollection.save(transaction);
        if (transaction.getFinalPrice() > 0) {
            request.setStatus(Request.Status.PROCESSING_PAYMENT);
        } else {
            request.setStatus(Request.Status.FULFILLED);
        }
        requestCollection.save(request);
        stripeService.doPayment(request.getUser(), principal, transaction);
        transactionCollection.save(transaction);
        request.setStatus(Request.Status.FULFILLED);
        requestCollection.save(request);
        transactionService.sendTransactionFulfilledNotification(transaction, principal, request.getUser());
        return new TransactionDto(transaction, true);
    }


    private Transaction getTransaction(String transactionId, String userId) {
        Transaction transaction = transactionCollection.findOneById(transactionId);
        if (transaction == null) {
            LOGGER.error("User [" + userId + "] tried to access non-existant transaction ["
                    + transactionId + "].");
            throw new NotFoundException("This transaction does not exist");
        }
        return transaction;
    }

    private Request getRequest(String requestId, String transactionId) {
        Request request = requestCollection.findOneById(requestId);
        if (request == null) {
            LOGGER.error("Could not find request for transaction [" + transactionId + "].");
            throw new NotFoundException("This request does not exist");
        }
        return request;
    }

    private Response getResponse(String responseId, String transactionId) {
        Response response = responseCollection.findOneById(responseId);
        if (response == null) {
            LOGGER.error("Could not find response for transaction [" + transactionId + "].");
            throw new NotFoundException("This response does not exist");
        }
        return response;
    }

}
