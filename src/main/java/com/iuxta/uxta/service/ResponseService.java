package com.iuxta.uxta.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iuxta.uxta.UxtaUtils;
import com.iuxta.uxta.dto.*;
import com.iuxta.uxta.exception.*;
import com.iuxta.uxta.exception.IllegalArgumentException;
import com.iuxta.uxta.firebase.CcsServer;
import com.iuxta.uxta.firebase.FirebaseUtils;
import com.iuxta.uxta.model.*;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by kerrk on 9/3/16.
 */
public class ResponseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseService.class);

    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<Response, String> responseCollection;
    private JacksonDBCollection<User, String> userCollection;
    private JacksonDBCollection<Transaction, String> transactionCollection;
    private JacksonDBCollection<ResponseFlag, String> responseFlagCollection;
    private CcsServer ccsServer;

    public ResponseService() {

    }

    public ResponseService(JacksonDBCollection<Request, String> requestCollection,
                           JacksonDBCollection<Response, String> responseCollection,
                           JacksonDBCollection<User, String> userCollection,
                           JacksonDBCollection<Transaction, String> transactionCollection,
                           JacksonDBCollection<ResponseFlag, String> responseFlagCollection,
                           CcsServer ccsServer) {
        this.requestCollection = requestCollection;
        this.responseCollection = responseCollection;
        this.userCollection = userCollection;
        this.transactionCollection = transactionCollection;
        this.responseFlagCollection = responseFlagCollection;
        this.ccsServer = ccsServer;
    }

    public Response transformResponseDto(ResponseDto dto, Request request, User responder) {
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
        ensureValidOffferPrice(dto.offerPrice);
        if (request.getType() != null && request.getType().equals(Request.Type.loaning)) {
            Request requestDuplicate = SerializationUtils.clone(request);
            requestDuplicate.setDuplicate(true);
            requestDuplicate.setId(null);
            WriteResult result = requestCollection.save(requestDuplicate);;
            requestDuplicate = (Request) result.getSavedObject();
            response.setIsOfferToBuyOrRent(true);
            response.setRequestId(requestDuplicate.getId());
        } else if (request.getType() != null && request.getType().equals(Request.Type.selling)) {
            response.setIsOfferToBuyOrRent(true);
            response.setRequestId(request.getId());
        } else {
            response.setRequestId(request.getId());
            response.setIsOfferToBuyOrRent(false);
        }
        response.setResponseTime(new Date());
        if (request.isInventoryListing()) {
            response.setBuyerStatus(Response.BuyerStatus.ACCEPTED);
            response.setSellerStatus(Response.SellerStatus.OFFERED);
        } else {
            response.setSellerStatus(Response.SellerStatus.ACCEPTED);
            response.setBuyerStatus(Response.BuyerStatus.OPEN);
        }
        response.setResponseStatus(Response.Status.PENDING);
        response.setResponderId(responder.getId());
        response.setInappropriate(false);
        populateResponse(response, dto);
        if (hasMessage(dto)) {
            Message message = new Message();
            message.setTimeSent(new Date());
            message.setSenderId(responder.getId());
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
        BigDecimal price = BigDecimal.valueOf(dto.offerPrice);
        price = price.setScale(UxtaUtils.USD.getDefaultFractionDigits(), UxtaUtils.DEFAULT_ROUNDING);
        String body = "";
        if (!request.isInventoryListing()) {
            body = responder.getFirstName() + " offered their " + request.getItemName() + " for $" + price;
        } else {
            body = responder.getFirstName() + " requested to " +  (request.getType().equals(Request.Type.loaning) ? "borrow " : "buy ") + "your " + request.getItemName() + " for $" + price;
        }
        if (!dto.priceType.toLowerCase().equals(Response.PriceType.FLAT.toString().toLowerCase())) {
            body += (dto.priceType.toLowerCase().equals(Response.PriceType.PER_DAY.toString().toLowerCase())) ?
                    " per day" : " per hour";
        }
        JSONObject notification = new JSONObject();
        notification.put("title", title);
        notification.put("message", body);
        notification.put("type", FirebaseUtils.NotificationTypes.response_update.name());
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
        if (offerPrice.equals(0) || offerPrice.equals(0.0)) {
            return;
        } else if (offerPrice < UxtaUtils.MINIMUM_OFFER_PRICE) {
            String msg = "Cannot create offer because offer price must be greater than $0.50 or $0.00";
            LOGGER.error(msg);
            throw new IllegalArgumentException(msg);
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
        response.setDescription(dto.description);
        response.setMessagesEnabled(dto.messagesEnabled);
        response.setPhotos(dto.photos);
        try {
            Response.PriceType priceType = Response.PriceType.valueOf(dto.priceType.toUpperCase());
            response.setPriceType(priceType);
        } catch (IllegalArgumentException e) {
            String msg = "Unable to set price type to [" + dto.priceType + "]. Options are FLAT, PER_HOUR, PER_DAY";
            LOGGER.error(msg);
            throw new IllegalArgumentException(msg);
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
        boolean description = response.getDescription() != null ?
                !response.getDescription().equals(dto.description) : dto.description != null;

        return exchangeLocation || exchangeTime || returnLocation || returnTime || priceType || offerPrice || description;
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
            String msg = "";
            if (request.getType().equals(Request.Type.loaning) || request.getType().equals(Request.Type.selling)) {
                msg = "Cannot update this response because the offer was recently closed.";
            } else {
                msg = "Cannot update this offer because the request was recently closed.";
            }
            LOGGER.info(msg);
            throw new BadRequestException(msg);
        }
        if (response.getBuyerStatus() == null) {
            response.setBuyerStatus(Response.BuyerStatus.OPEN);
        }
        boolean updated = populateResponse(response, dto);
        if (request.getUser().getId().equals(userId)) {
            if (updated && request.isInventoryListing()) {
                response.setBuyerStatus(Response.BuyerStatus.OPEN);
            } else if (updated) {
                response.setSellerStatus(Response.SellerStatus.OFFERED);
            }
            if (request.isInventoryListing()) {
                LOGGER.info("updating responder status");
                if (!dto.sellerStatus.equalsIgnoreCase(Response.SellerStatus.WITHDRAWN.toString())) {
                    dto.sellerStatus = "ACCEPTED";
                }
                updateSellerStatus(response, dto, request);
            } else {
                LOGGER.info("updating buyer status");
                if (!dto.buyerStatus.equalsIgnoreCase(Response.BuyerStatus.DECLINED.toString())) {
                    dto.buyerStatus = "ACCEPTED";
                }
                updateBuyerStatus(response, dto, request, updated);
            }
        } else {
            if (updated && response.getBuyerStatus().equals(Response.BuyerStatus.ACCEPTED) && !request.isInventoryListing()) {
                response.setBuyerStatus(Response.BuyerStatus.OPEN);
            } else if (updated && response.getSellerStatus().equals(Response.SellerStatus.ACCEPTED) && request.isInventoryListing()) {
                response.setSellerStatus(Response.SellerStatus.OFFERED);
            }
            if (request.isInventoryListing()) {
                if (!dto.buyerStatus.equalsIgnoreCase(Response.BuyerStatus.DECLINED.toString())) {
                    dto.buyerStatus = "ACCEPTED";
                }                updateBuyerStatus(response, dto, request, updated);
            } else {
                if (!dto.sellerStatus.equalsIgnoreCase(Response.SellerStatus.WITHDRAWN.toString())) {
                    dto.sellerStatus = "ACCEPTED";
                }
                updateSellerStatus(response, dto, request);
            }
        }
        responseCollection.save(response);
        return response;
    }

    private void updateBuyerStatus(Response response, ResponseDto dto, Request request, Boolean updated) {
        boolean sentUpdate = false;
        String notification = null;
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
                notification = request.getUser().getFirstName() + " declined your offer for a " + request.getItemName();
                updated = true;
            } else {
                //THIS SHOULD NOT HAPPEN
            }
        }
        if (!sentUpdate && updated) {
            if (request.isInventoryListing()) {
                sendUpdateToRequester(request, response, notification);
            } else {
                sendUpdateToResponder(request, response, notification);
            }
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
                    if (request.isInventoryListing()) {
                        sendUpdateToResponder(request, response, null);
                    } else {
                        sendUpdateToRequester(request, response, null);
                    }
                }
            } else if (sellerStatus.equals(Response.SellerStatus.OFFERED.toString().toLowerCase())) {
                //Not sure what scenario this would be
                response.setSellerStatus(Response.SellerStatus.OFFERED);
                // send notification to buyer that the offer has been updated
                if (request.isInventoryListing()) {
                    sendUpdateToResponder(request, response, null);
                } else {
                    sendUpdateToRequester(request, response, null);
                }
            } else if (sellerStatus.equals(Response.SellerStatus.WITHDRAWN.toString().toLowerCase())) {
                response.setSellerStatus(Response.SellerStatus.WITHDRAWN);
                response.setResponseStatus(Response.Status.CLOSED);
                if (request.isInventoryListing()) {
                    String msg = request.getUser().getFirstName() + " declined your offer for a " + request.getItemName();
                    sendUpdateToResponder(request, response, msg);
                } else {
                    String msg = request.getUser().getFirstName() + " withdrew their offer for a " + request.getItemName();
                    sendUpdateToRequester(request, response, msg);
                }
            } else {
                String msg = "Unable to update status to [" + sellerStatus + "]. Options are WITHDRAWN, OFFERED, and ACCEPTED";
                LOGGER.error(msg);
                throw new IllegalArgumentException(msg);
            }
        } else {
            if (request.isInventoryListing()) {
                sendUpdateToResponder(request, response, null);
            } else {
                sendUpdateToRequester(request, response, null);
            }
        }

    }

    public void sendUpdateToRequester(Request request, Response response, String msg) {
        try {
            JSONObject notification = new JSONObject();
            User seller = userCollection.findOneById(response.getResponderId());
            notification.put("title", msg != null ? msg : seller.getFirstName() + " updated their offer");
            notification.put("message", msg != null ? msg : seller.getFirstName() + " updated their offer for a " + request.getItemName());
            notification.put("type", FirebaseUtils.NotificationTypes.response_update.name());
            ObjectMapper mapper = new ObjectMapper();
            String responseJson = mapper.writeValueAsString(new ResponseDto(response));
            notification.put("response", responseJson);
            String requestJson = mapper.writeValueAsString(new RequestDto(request));
            notification.put("request", requestJson);
            User recipient = userCollection.findOneById(request.getUser().getId());
            FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
        } catch (JsonProcessingException e) {
            String err = "Could not send update to responder for response [" + response.getId() + "], " +
                    "got error converting object to json string: " + e.getMessage();
            LOGGER.error(err);
        }

    }

    public void sendUpdateToResponder(Request request, Response response, String msg) {
        try {
            JSONObject notification = new JSONObject();
            notification.put("title", msg != null ? msg : request.getUser().getFirstName() + " made updates to the offer");
            notification.put("message", msg != null ? msg : request.getUser().getFirstName() + " edited your offer for a " + request.getItemName());
            notification.put("type", FirebaseUtils.NotificationTypes.response_update.name());
            ObjectMapper mapper = new ObjectMapper();
            String responseJson = mapper.writeValueAsString(new ResponseDto(response));
            notification.put("response", responseJson);
            String requestJson = mapper.writeValueAsString(new RequestDto(request));
            notification.put("request", requestJson);
            User recipient = userCollection.findOneById(response.getResponderId());
            FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
        } catch (JsonProcessingException e) {
            String err = "Could not send update to responder for response [" + response.getId() + "], " +
                    "got error converting object to json string: " + e.getMessage();
            LOGGER.error(err);
        }

    }

    private void acceptResponse(Response response, Request request) {
        openTransaction(request, response);
        response.setResponseStatus(Response.Status.ACCEPTED);
        request.setStatus(Request.Status.TRANSACTION_PENDING);
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
            LOGGER.info("closing other responses, comparing [" +r.getId() + "] to accepted response id [" + response.getId()+ "]");
            if (!r.getId().equals(response.getId())) {
                try {
                    r.setBuyerStatus(Response.BuyerStatus.CLOSED);
                    r.setResponseStatus(Response.Status.CLOSED);
                    responseCollection.save(r);
                    JSONObject notification = new JSONObject();
                    notification.put("title", title);
                    notification.put("message", body);
                    notification.put("type", FirebaseUtils.NotificationTypes.offer_closed.name());
                    ObjectMapper mapper = new ObjectMapper();
                    String responseJson = mapper.writeValueAsString(new ResponseDto(r));
                    notification.put("response", responseJson);
                    String requestJson = mapper.writeValueAsString(new RequestDto(request));
                    notification.put("request", requestJson);
                    User recipient = userCollection.findOneById(r.getResponderId());
                    FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
                } catch (JsonProcessingException e) {
                    String msg = "Could not convert object to json string, got error: " + e.getMessage();
                    LOGGER.error(msg);
                }
            }
        });
        //let responder know the response has been accepted
        JSONObject notification = new JSONObject();
        User recipient = userCollection.findOneById(response.getResponderId());
        String priceType = response.getPriceType().equals(Response.PriceType.FLAT) ? "" :
                response.getPriceType().equals(Response.PriceType.PER_DAY) ? " per day " : " per hour ";

        try {
            BigDecimal price = BigDecimal.valueOf(response.getOfferPrice());
            price = price.setScale(UxtaUtils.USD.getDefaultFractionDigits(), UxtaUtils.DEFAULT_ROUNDING);
            notification.put("title", request.getUser().getFirstName() + " accepted your offer!");
            notification.put("message", "Your offer for a " + request.getItemName() + " for $" + price +
                    priceType + " was accepted!");
            notification.put("type", FirebaseUtils.NotificationTypes.offer_accepted.name());
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

    private void openTransaction(Request request, Response response) {
        String requestId = request.getId();
        BasicDBObject qry = new BasicDBObject("requestId", requestId);
        String responseId = response.getId();
        String sellerId = "";
        String buyerId = "";
        if (request.getType().equals(Request.Type.renting) || request.getType().equals(Request.Type.buying)) {
            sellerId = response.getResponderId();
            buyerId = request.getUser().getId();
        } else {
            sellerId = request.getUser().getId();
            buyerId = response.getResponderId();
        }
        qry.put("canceled", false);
        Transaction t = transactionCollection.findOne(qry);
        if (t != null) {
            LOGGER.error("Tried to open another transaction for request [" + requestId + "] with response [" +
                    responseId + "] but was unable to do so because open transaction [" + t.getId() + "] already exists");
            throw new InternalServerException("An offer has already been accepted for this transaction");
        }
        Transaction transaction = new Transaction();
        transaction.setRequestId(requestId);
        transaction.setResponseId(responseId);
        transaction.setSellerId(sellerId);
        transaction.setBuyerId(buyerId);
        transactionCollection.insert(transaction);
    }

    public List<HistoryDto> getHistory(User user, List<String> types, List<String> status) {
        DBObject searchByUser = new BasicDBObject("user._id", new ObjectId(user.getId()));
        boolean getRequests = false;
        boolean getOffers = false;
        boolean getTransactions = false;
        boolean getOpen = false;
        boolean getClosed = false;
        if (types == null || types.size() == 0) {
            getRequests = true;
            getOffers = true;
            getTransactions = true;
        } else {
            for (String type:types) {
                if (type.toLowerCase().equals("requests")) {
                    getRequests = true;
                } else if (type.toLowerCase().equals("offers")) {
                    getOffers = true;
                } else if (type.toLowerCase().equals("transactions")) {
                    getTransactions = true;
                }
            }
        }
        if (status == null || status.size() == 0) {
            getOpen = true;
            getClosed = true;
        } else {
            for (String s:status) {
                if (s.toLowerCase().equals("open")) {
                    getOpen = true;
                } else if (s.toLowerCase().equals("closed")) {
                    getClosed = true;
                }
            }
        }
        final boolean addRequests = getRequests;
        final boolean addOffers = getOffers;
        final boolean addTransactions = getTransactions;
        final boolean addOpen = getOpen;
        final boolean addClosed = getClosed;
        List<HistoryDto> historyDtos = new ArrayList<>();
        if (getRequests || getTransactions) {
            //requests includes responses to sale items
            List<HistoryDto> responseDtos = getResponsesToListings(user, getRequests, getTransactions, addOpen, addClosed);
            historyDtos.addAll(responseDtos);
            BasicDBObject query = new BasicDBObject();
            query.put("user._id", new ObjectId(user.getId()));
            BasicDBObject inQuery = new BasicDBObject();
            List<String> requestTypes = Stream.of("buying", "renting").collect(Collectors.toList());
            inQuery.put("$in", requestTypes);
            query.put("type", inQuery);
            DBCursor userRequests = requestCollection.find(query).sort(new BasicDBObject("postDate", -1));
            List<Request> requests = userRequests.toArray();
            userRequests.close();
            historyDtos.addAll(getRequestOffers(user, getRequests, getRequests, addOpen, addClosed, requests));
        }

        if (getOffers || getTransactions) {
            List<HistoryDto> userOffers = getMyOfferHistory(user, getOffers, getTransactions, getOpen, getClosed);
            historyDtos.addAll(userOffers);
        }
        Collections.sort(historyDtos, new HistoryComparator(user.getId()));
        if (historyDtos.size() > UxtaUtils.DEFAULT_LIMIT) {
            return historyDtos.subList(0, UxtaUtils.DEFAULT_LIMIT);
        } else {
            return historyDtos;
        }
    }

    public List<HistoryDto> getResponsesToListings(User user, final boolean getRequests, final boolean getTransactions,
                                                   final boolean getOpen, final boolean getClosed) {
        BasicDBObject query = new BasicDBObject();
        query.put("responderId", user.getId());
        query.put("isOfferToBuyOrRent", true);
        DBCursor responses  = responseCollection.find(query);
        List<Response> responseRequests = responses.toArray();
        responses.close();
        List<HistoryDto> historyDtos = new ArrayList<>();
        responseRequests.forEach(r -> {
            HistoryDto historyDto = getResponseRequest(r, user, getRequests, getTransactions, getOpen, getClosed);
            if (historyDto != null) {
                historyDtos.add(historyDto);
            }
        });
        return historyDtos;

    }

    public HistoryDto getResponseRequest(Response r, User user, final boolean getObject, final boolean getTransactions, final boolean getOpen, final boolean getClosed) {
        Request request = requestCollection.findOneById(r.getRequestId());
        if (request == null) {
            return null;
            //TODO: log an error here, but probably don't need to throw an exception...this really shouldn't happen
        } else {
            if (user.getBlockedUsers() != null && user.getBlockedUsers().size() > 0) {
                for (String blocked : user.getBlockedUsers()) {
                    if (blocked.equals(request.getUser().getId())) {
                        return null; //don't show this offer because they have blocked this user
                    }
                }
            }

            if (!getClosed && r.getResponseStatus().equals(Response.Status.CLOSED)) {
                return null;
            } else if (!getOpen && r.getResponseStatus().equals(Response.Status.PENDING)) {
                return null;
            }
            HistoryDto dto = new HistoryDto();
            dto.request = new RequestDto(request);
            dto.responses = Collections.singletonList(new ResponseDto(r));
            BasicDBObject qry = new BasicDBObject("responseId", r.getId());
            Transaction transaction = null;
            //only look for a transaction if the response is accepted, otherwise the transaction may not belong to the response
            if (r.getResponseStatus().equals(Response.Status.ACCEPTED)) {
                transaction = transactionCollection.findOne(qry);
            }
            if (transaction != null) {
                dto.transaction = new TransactionDto(transaction, true);
                if (getTransactions) {
                    if (!getOpen && (request.getStatus().equals(Request.Status.PROCESSING_PAYMENT) ||
                            request.getStatus().equals(Request.Status.TRANSACTION_PENDING))) {
                        return null;
                    } else if (!getClosed && (request.getStatus().equals(Request.Status.CLOSED) ||
                            request.getStatus().equals(Request.Status.FULFILLED))) {
                        return null;
                    }
                    return dto;
                }
            } else if (getObject) {
                return dto;
            }
            return null;
        }
    }

    public List<HistoryDto> getMyOfferHistory(User user, final boolean getOffers, final boolean getTransactions,
                                              final boolean getOpen, final boolean getClosed) {
        List<HistoryDto> historyDtos = new ArrayList<>();

        //includes requests that are of type "selling" or "loaning"
        BasicDBObject requestQuery = new BasicDBObject();
        requestQuery.put("user._id", new ObjectId(user.getId()));
        BasicDBObject inQuery = new BasicDBObject();
        List<String> types = Stream.of("selling", "loaning").collect(Collectors.toList());
        inQuery.put("$in", types);
        requestQuery.put("type", inQuery);
        DBCursor requests  = requestCollection.find(requestQuery);
        List<Request> offerRequests = requests.toArray();
        requests.close();
        historyDtos.addAll(getRequestOffers(user, getOffers, getTransactions, getOpen, getClosed, offerRequests));

        BasicDBObject query = new BasicDBObject("responderId", user.getId());
        //is the response really a request to rent or buy something? if so, don't include it in "offers"
        query.put("isOfferToBuyOrRent", false);

        DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
        List<Response> responses = requestResponses.toArray();
        requestResponses.close();
        // for each offer, get the corresponding request
        responses.forEach(r -> {
            HistoryDto historyDto = getResponseRequest(r, user, getOffers, getTransactions, getOpen, getClosed);
            if (historyDto != null) {
                historyDtos.add(historyDto);
            }
            /*Request request = requestCollection.findOneById(r.getRequestId());
            if (request == null) {
                //TODO: log an error here, but probably don't need to throw an exception...this really shouldn't happen
            } else {
                if (user.getBlockedUsers() != null && user.getBlockedUsers().size() > 0) {
                    for (String blocked:user.getBlockedUsers()) {
                        if (blocked.equals(request.getUser().getId())) {
                            return; //don't show this offer because they have blocked this user
                        }
                    }
                }

                if (!getClosed && r.getResponseStatus().equals(Response.Status.CLOSED)) {
                    return;
                } else if (!getOpen && r.getResponseStatus().equals(Response.Status.PENDING)) {
                    return;
                }
                HistoryDto dto = new HistoryDto();
                dto.request = new RequestDto(request);
                dto.responses = Collections.singletonList(new ResponseDto(r));
                BasicDBObject qry = new BasicDBObject("responseId", r.getId());
                Transaction transaction = null;
                //only look for a transaction if the response is accepted, otherwise the transaction may not belong to the response
                if (r.getResponseStatus().equals(Response.Status.ACCEPTED)) {
                    transaction = transactionCollection.findOne(qry);
                }
                if (transaction != null) {
                    dto.transaction = new TransactionDto(transaction, true);
                    if (getTransactions) {
                        if (!getOpen && (request.getStatus().equals(Request.Status.PROCESSING_PAYMENT) ||
                                request.getStatus().equals(Request.Status.TRANSACTION_PENDING))) {
                            return;
                        } else if (!getClosed && (request.getStatus().equals(Request.Status.CLOSED) ||
                                request.getStatus().equals(Request.Status.FULFILLED))) {
                            return;
                        }
                        historyDtos.add(dto);
                    }
                } else if (getOffers) {
                    historyDtos.add(dto);
                }

            }*/
        });
        return historyDtos;
    }

    public List<HistoryDto> getRequestOffers(User user, final boolean getObjects, final boolean getTransactions,
                                             final boolean getOpen, final boolean getClosed, List<Request> requests) {
        List<HistoryDto> historyDtos = new ArrayList<>();
        requests.forEach(r -> {
            BasicDBObject query = new BasicDBObject("requestId", r.getId());
            //don't return the inappropriate offers
            BasicDBObject notTrueQuery = new BasicDBObject();
            notTrueQuery.append("$ne", true);
            query.put("inappropriate", notTrueQuery);
            if (user.getBlockedUsers() != null && user.getBlockedUsers().size() > 0) {
                BasicDBObject blockedUserIdsQuery = new BasicDBObject();
                blockedUserIdsQuery.put("$nin", user.getBlockedUsers());
                query.put("responderId", blockedUserIdsQuery);
            }
            DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
            List<Response> responses = requestResponses.toArray();
            requestResponses.close();
            HistoryDto dto = new HistoryDto();
            dto.request = new RequestDto(r);
            if (!getOpen && (r.getStatus().equals(Request.Status.OPEN) ||
                    r.getStatus().equals(Request.Status.PROCESSING_PAYMENT) ||
                    r.getStatus().equals(Request.Status.TRANSACTION_PENDING))) {
                return;
            } else if (!getClosed && (r.getStatus().equals(Request.Status.CLOSED) ||
                    r.getStatus().equals(Request.Status.FULFILLED))) {
                return;
            }
            List<ResponseDto> dtos = ResponseDto.transform(responses);
            dtos.forEach(d -> {
                User seller = userCollection.findOneById(d.responderId);
                UserDto userDto = new UserDto();
                if (seller == null) {
                    for (Response response : responses) {
                        if (response.getResponderId().equals(d.responderId)) {
                            response.setResponseStatus(Response.Status.CLOSED);
                            responseCollection.save(response);
                            d.sellerStatus = r.getStatus().toString();
                        }
                    }
                    return;
                }
                userDto = new UserDto(seller);
                if (d.messagesEnabled != null && d.messagesEnabled) {
                    userDto.phone = seller.getPhone();
                }
                d.responder = userDto;
                d.seller = userDto;
            });
            query.put("canceled", false);
            Transaction transaction = transactionCollection.findOne(query);
            dto.responses = dtos;
            if (transaction != null) {
                dto.transaction = new TransactionDto(transaction, false);
                if (getTransactions) {
                    historyDtos.add(dto);
                }
            } else {
                if (getObjects) {
                    historyDtos.add(dto);
                }
            }
        });
        return historyDtos;
    }

    /**
     * Returns true if the user can make a new response/offer. User CANNOT make a new response/offer if they have 5 or more
     * open/pending offers
     * @param user
     * @return
     */
    public boolean canCreateResponse(User user) {
        BasicDBObject query = new BasicDBObject();
        query.put("responderId", user.getUserId());
        query.put("responseStatus", Response.Status.PENDING);
        DBCursor userResponses  = responseCollection.find(query);
        List<Response> userRs = userResponses.toArray();
        int openTransactions = getOpenTransactions(user);
        String msg = "User [" + user.getFirstName() + ":" + user.getId() + "] has [" +
                userResponses.size() + "] pending offers and [" + openTransactions + "] open transactions";
        int totalResponseAndTransactions = userRs.size() + openTransactions;
        if (userRs.size() < UxtaUtils.MAX_OPEN_RESPONSES && (totalResponseAndTransactions < 20)) {
            LOGGER.info(msg);
            return true;
        } else {
            LOGGER.info("Cannot make offer: " + msg);
            return false;
        }
    }

    public int getOpenTransactions(User user) {
        BasicDBObject query = new BasicDBObject();
        BasicDBList or = new BasicDBList();
        BasicDBObject buyerQuery = new BasicDBObject("buyerId", user.getId());
        or.add(buyerQuery);
        BasicDBObject sellerQuery = new BasicDBObject("responderId", user.getId());
        or.add(sellerQuery);
        query.put("$or", or);
        BasicDBObject notSetQuery = new BasicDBObject("$exists", false);
        query.put("finalPrice", notSetQuery);
        query.put("canceled", false);
        DBCursor transactions  = transactionCollection.find(query);
        List<Request> ts = transactions.toArray();
        return ts != null ? ts.size() : 0;
    }

    public void alertRespondersOfClosedRequest(Request request) {
        BasicDBObject query = new BasicDBObject();
        query.append("requestId", request.getId());
        DBCursor requestResponses = responseCollection.find(query).sort(new BasicDBObject("responseTime", -1));
        List<Response> responses = requestResponses.toArray();
        requestResponses.close();
        String title = "Offer Closed";
        String body = "Your offer to " + request.getUser().getFirstName() + " for a " + request.getItemName() +
                " has been closed because they closed the request";
        //TODO: think about doing this asynchronously
        responses.forEach(r -> {
            try {
                r.setBuyerStatus(Response.BuyerStatus.CLOSED);
                r.setResponseStatus(Response.Status.CLOSED);
                responseCollection.save(r);
                JSONObject notification = new JSONObject();
                notification.put("title", title);
                notification.put("message", body);
                notification.put("type", FirebaseUtils.NotificationTypes.offer_closed.name());
                ObjectMapper mapper = new ObjectMapper();
                String responseJson = mapper.writeValueAsString(new ResponseDto(r));
                notification.put("response", responseJson);
                String requestJson = mapper.writeValueAsString(new RequestDto(request));
                notification.put("request", requestJson);
                User recipient = userCollection.findOneById(r.getResponderId());
                FirebaseUtils.sendFcmMessage(recipient, null, notification, ccsServer);
            } catch (JsonProcessingException e) {
                String msg = "Could not convert object to json string, got error: " + e.getMessage();
                LOGGER.error(msg);
            }
        });
    }

    public ResponseFlag flagResponse(User user, ResponseFlagDto dto, Response response) {
        canCreateNewFlag(response.getId());
        ResponseFlag flag = new ResponseFlag();
        flag.setResponseId(response.getId());
        flag.setReporterId(user.getId());
        flag.setReporterNotes(dto.reporterNotes);
        flag.setStatus(RequestFlag.Status.PENDING);
        flag.setReportedDate(new Date());
        sendAdminFlagNotification(response);
        WriteResult newFlag = responseFlagCollection.insert(flag);
        flag = (ResponseFlag) newFlag.getSavedObject();
        return flag;
    }

    public void canCreateNewFlag(String responseId) {
        //if the user created a flag that is pending review, they cannot create a new flag
        BasicDBObject query = new BasicDBObject();
        query.put("responseId", responseId);
        query.put("status", RequestFlag.Status.PENDING.toString());
        DBCursor requestFlags  = responseFlagCollection.find(query);
        List<RequestFlag> flags = requestFlags.toArray();
        requestFlags.close();
        if (flags.size() > 0) {
            throw new NotAllowedException("You have already flagged this response & we will review in soon. Thanks!");
        }
    }

    private void sendAdminFlagNotification(Response response) {
        DBObject findAdmins = new BasicDBObject("admin", true);
        DBCursor cursor = userCollection.find(findAdmins);
        List<User> admins = cursor.toArray();
        cursor.close();
        if (admins != null && admins.size() > 0) {
            JSONObject notification = new JSONObject();
            notification.put("title", "Response has been flagged!");
            String body = "Response [" + response.getId() + " - " + response.getDescription() + "] has been flagged! Review ASAP!";
            notification.put("message", body);
            notification.put("type", FirebaseUtils.NotificationTypes.new_user_notification.name());
            for (User admin:admins) {
                FirebaseUtils.sendFcmMessage(admin, null, notification, ccsServer);
            }
        }
    }

    public void closeResponsesFromBlockedUsers(User user1, User user2) {
        closeUserResponses(user1, user2);
        closeUserResponses(user2, user1);
    }

    private void closeUserResponses(User user1, User user2) {
        BasicDBObject query = new BasicDBObject();
        query.put("user.userId", user1.getId());
        query.put("status", Request.Status.OPEN.toString());
        DBCursor requestCursor  = requestCollection.find(query);
        List<Request> requests = requestCursor.toArray();
        requestCursor.close();
        for (Request request:requests) {
            query = new BasicDBObject();
            query.put("responderId", user2.getId());
            query.put("responseStatus", Response.Status.PENDING.toString());
            query.put("requestId", request.getId());
            DBCursor responseCursor  = responseCollection.find(query);
            List<Response> responses = responseCursor.toArray();
            responseCursor.close();
            for (Response response:responses) {
                response.setSellerStatus(Response.SellerStatus.WITHDRAWN);
                response.setResponseStatus(Response.Status.CLOSED);
                responseCollection.save(response);
            }
        }

    }
}
