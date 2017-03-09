package com.impulsecontrol.lend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulsecontrol.lend.NearbyUtils;
import com.impulsecontrol.lend.dto.HistoryDto;
import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.dto.ResponseDto;
import com.impulsecontrol.lend.dto.TransactionDto;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.exception.*;
import com.impulsecontrol.lend.exception.IllegalArgumentException;
import com.impulsecontrol.lend.firebase.CcsServer;
import com.impulsecontrol.lend.firebase.FirebaseUtils;
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
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
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
    private static final Currency USD = Currency.getInstance("USD");
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;

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
        ensureValidOffferPrice(dto.offerPrice);
        Response response = new Response();
        response.setResponseTime(new Date());
        response.setSellerStatus(Response.SellerStatus.ACCEPTED);
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
        WriteResult result = responseCollection.insert(response);
        response = (Response) result.getSavedObject();
        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            //do nothing
        }
        String title = "New Offer";
        String body = seller.getFirstName() + " offered their " + request.getItemName() + " for $" + dto.offerPrice;
        if (!dto.priceType.toLowerCase().equals(Response.PriceType.FLAT.toString().toLowerCase())) {
            body += (dto.priceType.toLowerCase().equals(Response.PriceType.PER_DAY.toString().toLowerCase())) ?
                    " per day" : " per hour";
        }
        JSONObject notification = new JSONObject();
        notification.put("title", title);
        notification.put("message", body);
        notification.put("type", "response_update");
        try {
            ObjectMapper mapper = new ObjectMapper();
            String responseJson = mapper.writeValueAsString(new ResponseDto(response));
            notification.put("response", responseJson);
            String requestJson = mapper.writeValueAsString(new RequestDto(request));
            notification.put("request", requestJson);
        } catch (JsonProcessingException e) {
            String msg = "Could not convert object to json string, got error: " + e.getMessage();
            LOGGER.error(msg);
        }
        User recipient = userCollection.findOneById(request.getUser().getId());
        FirebaseUtils.sendFcmMessage(recipient, dto, notification, ccsServer);
        return response;
    }

    private boolean hasMessage(ResponseDto dto) {
        return dto != null && dto.messages != null && dto.messages.size() > 0 && dto.messages.get(0).getContent() != null;
    }

    public void ensureValidOffferPrice(Double offerPrice) {
        if (offerPrice.equals(0)) {
            return;
        } else if (offerPrice < NearbyUtils.MINIMUM_OFFER_PRICE) {
            String msg = "Cannot create offer because offer price must be greater than $0.50 or $0.00";
            LOGGER.error(msg);
            throw new com.impulsecontrol.lend.exception.IllegalArgumentException(msg);
        }
    }

    public boolean populateResponse(Response response, ResponseDto dto) {
        ensureValidOffferPrice(dto.offerPrice);
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
            String msg = "unable to update this response because the request is no longer open";
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
        } else {
            sendUpdateToBuyer(request, response);
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
            FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
        } catch (JsonProcessingException e) {
            String msg = "Could not convert object to json string, got error: " + e.getMessage();
            LOGGER.error(msg);
        }

    }

    public void sendUpdateToSeller(Request request, Response response) {
        try {
            JSONObject notification = new JSONObject();
            notification.put("title", request.getUser().getFirstName() + " made updates to the offer");
            notification.put("message", request.getUser().getFirstName() + " edited your offer for a " + request.getItemName());
            notification.put("type", "response_update");
            ObjectMapper mapper = new ObjectMapper();
            String responseJson = mapper.writeValueAsString(new ResponseDto(response));
            notification.put("response", responseJson);
            String requestJson = mapper.writeValueAsString(new RequestDto(request));
            notification.put("request", requestJson);
            User recipient = userCollection.findOneById(response.getSellerId());
            FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
        } catch (JsonProcessingException e) {
            String msg = "Could not convert object to json string, got error: " + e.getMessage();
            LOGGER.error(msg);
        }

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
            if (!r.getId().equals(response.getId())) {
                try {
                    r.setBuyerStatus(Response.BuyerStatus.CLOSED);
                    r.setResponseStatus(Response.Status.CLOSED);
                    responseCollection.save(r);
                    JSONObject notification = new JSONObject();
                    notification.put("title", title);
                    notification.put("message", body);
                    notification.put("type", "offer_closed");
                    ObjectMapper mapper = new ObjectMapper();
                    String responseJson = mapper.writeValueAsString(new ResponseDto(response));
                    notification.put("response", responseJson);
                    String requestJson = mapper.writeValueAsString(new RequestDto(request));
                    notification.put("request", requestJson);
                    User recipient = userCollection.findOneById(response.getSellerId());
                    FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
                } catch (JsonProcessingException e) {
                    String msg = "Could not convert object to json string, got error: " + e.getMessage();
                    LOGGER.error(msg);
                }
            }
        });
        //let seller know the response has been accepted
        JSONObject notification = new JSONObject();

        User recipient = userCollection.findOneById(response.getSellerId());
        String priceType = response.getPriceType().equals(Response.PriceType.FLAT) ? "" :
                response.getPriceType().equals(Response.PriceType.PER_DAY) ? " per day " : " per hour ";

        try {
            BigDecimal price = BigDecimal.valueOf(response.getOfferPrice());
            price = price.setScale(USD.getDefaultFractionDigits(), DEFAULT_ROUNDING);
            notification.put("title", request.getUser().getFirstName() + " accepted your offer!");
            notification.put("message", "Your offer for a " + request.getItemName() + " for $" + price +
                    priceType + " was accepted!");
            notification.put("type", "offer_accepted");
            ObjectMapper mapper = new ObjectMapper();
            String responseJson = mapper.writeValueAsString(new ResponseDto(response));
            notification.put("response", responseJson);
            String requestJson = mapper.writeValueAsString(new RequestDto(request));
            notification.put("request", requestJson);
            FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);

            //let buyer know they accepted the offer and other responses have been closed
            notification = new JSONObject();
            notification.put("title", "You accepted " + recipient.getFirstName() + "'s offer!");
            notification.put("message", "You accepted " + recipient.getFirstName() + "'s offer for $" + price +
                    priceType + ". Any other offers have been closed.");
            recipient = userCollection.findOneById(request.getUser().getId());
            requestCollection.save(request);
            FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
        } catch (JsonProcessingException e) {
            String msg = "Could not convert object to json string, got error: " + e.getMessage();
            LOGGER.error(msg);
        }

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
                if (seller == null) {
                    for (Response response : responses) {
                        if (response.getSellerId().equals(d.sellerId)) {
                            response.setResponseStatus(Response.Status.CLOSED);
                            responseCollection.save(response);
                            d.sellerStatus = r.getStatus().toString();
                        }
                    }
                    return;
                }
                userDto = new UserDto(seller);
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
