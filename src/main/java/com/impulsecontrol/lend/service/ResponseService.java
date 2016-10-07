package com.impulsecontrol.lend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulsecontrol.lend.dto.HistoryDto;
import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.dto.ResponseDto;
import com.impulsecontrol.lend.dto.TransactionDto;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.exception.BadRequestException;
import com.impulsecontrol.lend.exception.InternalServerException;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.firebase.CcsServer;
import com.impulsecontrol.lend.model.HistoryComparator;
import com.impulsecontrol.lend.model.Message;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.Response;
import com.impulsecontrol.lend.model.Transaction;
import com.impulsecontrol.lend.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Transient;
import java.lang.IllegalArgumentException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by kerrk on 9/3/16.
 */
public class ResponseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseService.class);

    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<Response, String> responseCollection;
    private JacksonDBCollection<User, String> userCollection;
    private JacksonDBCollection<Transaction, String> transactionCollection;
    private CcsServer ccsServer;

    public ResponseService() {

    }

    public ResponseService(JacksonDBCollection<Request, String> requestCollection,
                           JacksonDBCollection<Response, String> responseCollection,
                           JacksonDBCollection<User, String> userCollection,
                           JacksonDBCollection<Transaction, String> transactionCollection,
                                   CcsServer ccsServer) {
        this.requestCollection = requestCollection;
        this.responseCollection = responseCollection;
        this.userCollection = userCollection;
        this.transactionCollection = transactionCollection;
        this.ccsServer = ccsServer;
    }

    public Response transformResponseDto(ResponseDto dto, Request request, User seller) {
        if (request.getStatus() != Request.Status.OPEN) {
            String msg = "Cannot create this offer because the request was recently fulfilled or closed.";
            LOGGER.info(msg);
            throw new BadRequestException(msg);
        }
        if (request.getExpireDate() != null && request.getExpireDate().before(new Date())) {
            request.setStatus(Request.Status.CLOSED);
            requestCollection.save(request);
            String msg = "Cannot create this offer because the request was recently closed.";
            LOGGER.info(msg);
            throw new BadRequestException(msg);
        }
        Response response = new Response();
        response.setResponseTime(new Date());
        response.setSellerStatus(Response.SellerStatus.OFFERED);
        response.setResponseStatus(Response.Status.PENDING);
        response.setRequestId(request.getId());
        response.setSellerId(seller.getId());
        response.setBuyerStatus(Response.BuyerStatus.OPEN);
        populateResponse(response, dto);
        if (hasMessage(dto)) {
            Message message = new Message();
            message.setTimeSent(new Date());
            message.setSenderId(seller.getId());
            message.setContent(dto.messages.get(0).getContent());
            response.addMessage(message);
        }
        responseCollection.insert(response);
        String title = "New Offer";
        String body = seller.getFirstName() + " offered their " + request.getItemName() + " for $" + dto.offerPrice;
        if (dto.priceType.toLowerCase() != Response.PriceType.FLAT.toString().toLowerCase()) {
            body += (dto.priceType.toLowerCase().equals(Response.PriceType.PER_DAY.toString().toLowerCase())) ?
                    " per day" : " per hour";
        }
        JSONObject notification = new JSONObject();
        notification.put("title", title);
        notification.put("body", body);
        User recipient = userCollection.findOneById(request.getUser().getId());
        sendFcmMessage(recipient, dto, notification);
        return response;
    }

    private boolean hasMessage(ResponseDto dto) {
        return dto != null && dto.messages != null && dto.messages.size() > 0 && dto.messages.get(0).getContent() != null;
    }

    private void sendFcmMessage(User recipient, ResponseDto dto, JSONObject notification) {
        if (recipient.getFcmRegistrationId() == null) {
            String msg = "could not send notification/message to [" + recipient.getFirstName() + "] " +
                    "because they have not allowed messages.";
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
        String messageId = CcsServer.nextMessageId();
        JSONObject payload = new JSONObject();

        /*//TODO: rethink this, should we send messages separately?
        if (hasMessage(dto)) {
            payload.put("message", dto.messages.get(0).getContent());
        }*/
        // we will currently send everything as a message and the client can construct the notification when needed

        LOGGER.info("attempting to send message/notification to user [" + recipient.getName() + "] with fcm token [" +
                recipient.getFcmRegistrationId() + "].");
        String jsonMessage = CcsServer.createJsonMessage(recipient.getFcmRegistrationId(), messageId, notification,
                payload, null, null, null);
        try {
            Boolean sent = ccsServer.sendDownstreamMessage(jsonMessage);
            if (sent) {
                LOGGER.info("Successfully sent message!");
            } else {
                LOGGER.error("could not sent message :(");
            }
        } catch (Exception e) {
            String msg = "could not send message, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public boolean populateResponse(Response response, ResponseDto dto) {
        boolean changed = getResponseUpdated(response, dto);
        LOGGER.info("[" + response.getId() + "] has been updated: " + changed);
        response.setOfferPrice(dto.offerPrice);
        response.setExchangeLocation(dto.exchangeLocation);
        response.setExchangeTime(dto.exchangeTime);
        response.setReturnLocation(dto.returnLocation);
        response.setReturnTime(dto.returnTime);
        try {
            Response.PriceType priceType = Response.PriceType.valueOf(dto.priceType.toUpperCase());
            response.setPriceType(priceType);
        } catch (IllegalArgumentException e) {
            String msg = "Unable to set price type to [" + dto.priceType + "]. Options are FLAT, PER_HOUR, PER_DAY";
            LOGGER.error(msg);
            throw new com.impulsecontrol.lend.exception.IllegalArgumentException(msg);
        }
        return changed;
    }

    private boolean getResponseUpdated(Response response, ResponseDto dto) {
        boolean exchangeLocation = response.getExchangeLocation() != null ?
                !response.getExchangeLocation().equals(dto.exchangeLocation) : dto.exchangeLocation != null;
        boolean exchangeTime = response.getExchangeTime() != null ?
                response.getExchangeTime().compareTo(dto.exchangeTime) != 0 : dto.exchangeTime != null;
        boolean returnLocation = response.getReturnLocation() != null ?
                !response.getReturnLocation().equals(dto.returnLocation) : dto.returnLocation != null;
        boolean returnTime = response.getReturnTime() != null ?
                response.getReturnTime().compareTo(dto.returnTime) != 0 : dto.returnTime != null;
        boolean priceType = response.getPriceType() != null ?
                !response.getPriceType().toString().toLowerCase().equals(dto.priceType.toLowerCase()) : dto.priceType != null;
        boolean offerPrice = response.getOfferPrice() != null ?
                response.getOfferPrice().compareTo(dto.offerPrice) != 0 : dto.priceType != null;

        return exchangeLocation || exchangeTime || returnLocation || returnTime || priceType || offerPrice;
    }

    public Response updateResponse(ResponseDto dto, Response response, Request request, String userId) {
        if (!(request.getStatus() == Request.Status.OPEN) &&
                !(request.getStatus() == Request.Status.FULFILLED && request.getFulfilledByUserId().equals(userId))) {
            String msg = "unable to update this response because the request is not longer open";
            LOGGER.error(msg);
            throw new UnauthorizedException(msg);
        }
        if (request.getExpireDate() != null && request.getExpireDate().before(new Date())) {
            request.setStatus(Request.Status.CLOSED);
            requestCollection.save(request);
            response.setBuyerStatus(Response.BuyerStatus.CLOSED);
            response.setResponseStatus(Response.Status.CLOSED);
            responseCollection.save(response);
            String msg = "Cannot update this offer because the request was recently closed.";
            LOGGER.info(msg);
            throw new BadRequestException(msg);
        }
        if (response.getBuyerStatus() == null) {
            response.setBuyerStatus(Response.BuyerStatus.OPEN);
        }
        boolean updated = populateResponse(response, dto);
        if (request.getUser().getId().equals(userId)) {
            if (updated) {
                response.setSellerStatus(Response.SellerStatus.OFFERED);
            }
            LOGGER.info("updating buyer status");
            updateBuyerStatus(response, dto, request, updated);
        } else {
            if (updated && response.getBuyerStatus().equals(Response.BuyerStatus.ACCEPTED)) {
                response.setBuyerStatus(Response.BuyerStatus.OPEN);
            }
            updateSellerStatus(response, dto, request);
        }
        responseCollection.save(response);
        return response;
    }

    private void updateBuyerStatus(Response response, ResponseDto dto, Request request, Boolean updated) {
        boolean sentUpdate = false;
        if (!response.getBuyerStatus().toString().toLowerCase().equals(dto.buyerStatus.toLowerCase())) {
            String buyerStatus = dto.buyerStatus.toLowerCase();
            if (buyerStatus.equals(Response.BuyerStatus.ACCEPTED.toString().toLowerCase())) {
                response.setBuyerStatus(Response.BuyerStatus.ACCEPTED);
                //if both users have accepted, send notifications and close other responses
                if (response.getSellerStatus().equals(Response.SellerStatus.ACCEPTED)) {
                    acceptResponse(response, request);
                    sentUpdate = true;
                }
            } else if (buyerStatus.equals(Response.BuyerStatus.DECLINED.toString().toLowerCase())) {
                response.setBuyerStatus(Response.BuyerStatus.DECLINED);
                response.setResponseStatus(Response.Status.CLOSED);
                //TODO: should we send a notification here??
                sentUpdate = true;
            } else {
                //THIS SHOULD NOT HAPPEN
            }
        }
        if (!sentUpdate && updated) {
            sendUpdateToSeller(request, response);
        }
    }

    private void updateSellerStatus(Response response, ResponseDto dto, Request request) {
        if (!response.getSellerStatus().toString().toLowerCase().equals(dto.sellerStatus.toLowerCase())) {
            String sellerStatus = dto.sellerStatus.toLowerCase();
            if (sellerStatus.equals(Response.SellerStatus.ACCEPTED.toString().toLowerCase())) {
                response.setSellerStatus(Response.SellerStatus.ACCEPTED);
                if (response.getBuyerStatus().equals(Response.BuyerStatus.ACCEPTED)) {
                    acceptResponse(response, request);
                } else {
                    // send notification to buyer that the offer has been updated
                    sendUpdateToBuyer(request, response);
                }
            } else if (sellerStatus.equals(Response.SellerStatus.OFFERED.toString().toLowerCase())) {
                //Not sure what scenario this would be
                response.setSellerStatus(Response.SellerStatus.OFFERED);
                // send notification to buyer that the offer has been updated
                sendUpdateToBuyer(request, response);
            } else if (sellerStatus.equals(Response.SellerStatus.WITHDRAWN.toString().toLowerCase())) {
                response.setSellerStatus(Response.SellerStatus.WITHDRAWN);
                response.setResponseStatus(Response.Status.CLOSED);
                //TODO: should we send a notification here?
            } else {
                String msg = "Unable to update status to [" + sellerStatus + "]. Options are WITHDRAWN, OFFERED, and ACCEPTED";
                LOGGER.error(msg);
                throw new com.impulsecontrol.lend.exception.IllegalArgumentException(msg);
            }
        }

    }

    public void sendUpdateToBuyer(Request request, Response response) {
        try {
            JSONObject notification = new JSONObject();
            User seller = userCollection.findOneById(response.getSellerId());
            notification.put("title", seller.getFirstName() + " updated their offer");
            notification.put("message", seller.getFirstName() + " updated their offer for a " + request.getItemName());
            notification.put("type", "response_update");
            ObjectMapper mapper = new ObjectMapper();
            String responseJson = mapper.writeValueAsString(new ResponseDto(response));
            notification.put("response", responseJson);
            String requestJson = mapper.writeValueAsString(new RequestDto(request));
            notification.put("request", requestJson);
            User recipient = userCollection.findOneById(request.getUser().getId());
            sendFcmMessage(recipient, null, notification);
        } catch (JsonProcessingException e) {
            String msg = "Could not convert object to json string, got error: " + e.getMessage();
            LOGGER.error(msg);
        }

    }

    public void sendUpdateToSeller(Request request, Response response) {
        JSONObject notification = new JSONObject();
        notification.put("title", request.getUser().getFirstName() + " made updates to the offer");
        notification.put("body", request.getUser().getFirstName() + " edited your offer for a " + request.getItemName());
        User recipient = userCollection.findOneById(response.getSellerId());
        sendFcmMessage(recipient, null, notification);
    }

    private void acceptResponse(Response response, Request request) {
        response.setResponseStatus(Response.Status.ACCEPTED);
        request.setStatus(Request.Status.TRANSACTION_PENDING);
        openTransaction(request.getId(), response.getId());
        BasicDBObject query = new BasicDBObject();
        query.append("requestId", request.getId());
        DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
        List<Response> responses = requestResponses.toArray();
        requestResponses.close();
        String title = "Offer Closed";
        String body = "Your offer to " + request.getUser().getFirstName() + " for a " + request.getItemName() +
                " has been closed because the user accepted another offer or closed the request. Thanks for your offer!";
        //TODO: think about doing this asynchronously
        responses.forEach(r -> {
            if (r.getId() != response.getId()) {
                r.setBuyerStatus(Response.BuyerStatus.CLOSED);
                r.setResponseStatus(Response.Status.CLOSED);
                responseCollection.save(r);
                JSONObject notification = new JSONObject();
                notification.put("title", title);
                notification.put("body", body);
                // send message
                User recipient = userCollection.findOneById(r.getSellerId());
                sendFcmMessage(recipient, null, notification);
            }
        });
        //let seller know the response has been accepted
        JSONObject notification = new JSONObject();
        notification.put("title", request.getUser().getFirstName() + " accepted your offer!");
        String priceType = response.getPriceType().equals(Response.PriceType.FLAT) ? "" :
                response.getPriceType().equals(Response.PriceType.PER_DAY) ? " per day " : " per hour ";
        notification.put("body", "Your offer for a " + request.getItemName() + " for $" + response.getOfferPrice() +
                priceType + " was accepted!");
        User recipient = userCollection.findOneById(response.getSellerId());
        sendFcmMessage(recipient, null, notification);

        //let buyer know they accepted the offer and other responses have been closed
        notification = new JSONObject();
        notification.put("title", "You accepted " + recipient.getFirstName() + "'s offer!");
        notification.put("body", "Your accepted " + recipient.getFirstName() + "'s offer for $" + response.getOfferPrice() +
                priceType + ". If you received any other offers for this item, they have now been closed.");
        recipient = userCollection.findOneById(request.getUser().getId());
        requestCollection.save(request);
        sendFcmMessage(recipient, null, notification);
    }

    private void openTransaction(String requestId, String responseId) {
        Transaction transaction = new Transaction();
        transaction.setRequestId(requestId);
        transaction.setResponseId(responseId);
        transactionCollection.insert(transaction);
    }

    public List<HistoryDto> getHistory(User user) {
        DBObject searchByUser = new BasicDBObject("user._id", new ObjectId(user.getId()));
        DBCursor userRequests = requestCollection.find(searchByUser).sort(new BasicDBObject("postDate", -1));
        List<Request> requests = userRequests.toArray();
        userRequests.close();
        List<HistoryDto> historyDtos = new ArrayList<>();
        requests.forEach(r -> {
            BasicDBObject query = new BasicDBObject("requestId", r.getId());
            DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
            List<Response> responses = requestResponses.toArray();
            requestResponses.close();
            HistoryDto dto = new HistoryDto();
            dto.request = new RequestDto(r);
            List<ResponseDto> dtos = ResponseDto.transform(responses);
            dtos.forEach(d -> {
                User seller = userCollection.findOneById(d.sellerId);
                UserDto userDto = new UserDto();
                userDto.userId = seller.getId();
                userDto.lastName = seller.getLastName();
                userDto.firstName = seller.getFirstName();
                userDto.fullName = seller.getName();
                d.seller = userDto;
            });
            Transaction transaction = transactionCollection.findOne(query);
            if (transaction != null) {
                dto.transaction = new TransactionDto(transaction, false);
            }
            dto.responses = dtos;
            historyDtos.add(dto);
        });
        List<HistoryDto> userOffers = getMyOfferHistory(user.getId());
        historyDtos.addAll(userOffers);
        Collections.sort(historyDtos, new HistoryComparator(user.getId()));
        return historyDtos;
    }

    public List<HistoryDto> getMyOfferHistory(String userId) {
        BasicDBObject query = new BasicDBObject("sellerId", userId);
        DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
        List<Response> responses = requestResponses.toArray();
        requestResponses.close();
        List<HistoryDto> historyDtos = new ArrayList<>();
        // for each offer, get the corresponding request
        responses.forEach(r -> {
            Request request = requestCollection.findOneById(r.getRequestId());
            if (request == null) {
                //TODO: log an error here, but probably don't need to throw an exception...this really shouldn't happen
            } else {
                HistoryDto dto = new HistoryDto();
                BasicDBObject qry = new BasicDBObject("responseId", r.getId());
                Transaction transaction = transactionCollection.findOne(qry);
                if (transaction != null) {
                    dto.transaction = new TransactionDto(transaction, true);
                }
                dto.request = new RequestDto(request);
                dto.responses = Collections.singletonList(new ResponseDto(r));
                historyDtos.add(dto);
            }
        });
        return historyDtos;
    }
}
