package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by kerrk on 9/2/16.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Response implements Serializable {

    private String id;

    @NotNull
    private String requestId;

    @NotNull
    private String responderId;

    private Date responseTime;

    private String description;

    private Boolean isOfferToBuyOrRent;

    /**
     * true if the seller wants to allow the buyer to message them with questions
     * (we will display a "message user" button to the buyer)
     */
    private Boolean messagesEnabled;

    /**
     * should be initially set by the responder
     */
    private Double offerPrice;

    /**
     * should be initially set by the seller
     */
    private PriceType priceType;

    /**
     * should be initially set by the buyer
     */
    private String exchangeLocation;

    /**
     * should be initially set by the buyer
     */
    private String returnLocation;

    /**
     * should be initially set by the buyer
     */
    private Date exchangeTime;

    /**
     * should be initially set by the buyer
     */
    private Date returnTime;

    private BuyerStatus buyerStatus;

    private SellerStatus sellerStatus;

    private Status responseStatus;

    private List<Message> messages;

    //if this response was accepted but then the transaction was canceled,
    // save the cancel reason here so it can be displayed
    private String canceledReason;

    private Boolean inappropriate = false;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResponderId() {
        return responderId;
    }

    public void setResponderId(String responderId) {
        this.responderId = responderId;
    }

    public Date getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(Date responseTime) {
        this.responseTime = responseTime;
    }

    public Double getOfferPrice() {
        return offerPrice;
    }

    public void setOfferPrice(Double offerPrice) {
        this.offerPrice = offerPrice;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public void setPriceType(PriceType priceType) {
        this.priceType = priceType;
    }

    public String getExchangeLocation() {
        return exchangeLocation;
    }

    public void setExchangeLocation(String exchangeLocation) {
        this.exchangeLocation = exchangeLocation;
    }

    public String getReturnLocation() {
        return returnLocation;
    }

    public void setReturnLocation(String returnLocation) {
        this.returnLocation = returnLocation;
    }

    public Date getExchangeTime() {
        return exchangeTime;
    }

    public void setExchangeTime(Date exchangeTime) {
        this.exchangeTime = exchangeTime;
    }

    public Date getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(Date returnTime) {
        this.returnTime = returnTime;
    }

    public BuyerStatus getBuyerStatus() {
        return buyerStatus;
    }

    public void setBuyerStatus(BuyerStatus buyerStatus) {
        this.buyerStatus = buyerStatus;
    }

    public SellerStatus getSellerStatus() {
        return sellerStatus;
    }

    public void setSellerStatus(SellerStatus sellerStatus) {
        this.sellerStatus = sellerStatus;
    }

    public Status getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Status responseStatus) {
        this.responseStatus = responseStatus;
    }

    public List<Message> getMessages() {
        return this.messages;
    }

    public String getCanceledReason() {
        return canceledReason;
    }

    public void setCanceledReason(String canceledReason) {
        this.canceledReason = canceledReason;
    }

    public void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        messages.add(message);
    }

    public Boolean getMessagesEnabled() {
        return messagesEnabled;
    }

    public void setMessagesEnabled(Boolean messagesEnabled) {
        this.messagesEnabled = messagesEnabled;
    }

    @ObjectId
    @JsonProperty("_id")
    public String getId() {
        return id;
    }

    @ObjectId
    @JsonProperty("_id")
    public void setId(String id) {
        this.id = id;
    }

    public static enum PriceType {
        FLAT, PER_HOUR, PER_DAY
    }

    /**
     * OPEN: the request is still open, the buyer has not accepted any offers
     * CLOSED: the request is closed either because the user accepted an offer from someone else, or withdrew the request
     * ACCEPTED: the user accepted the offer from this seller
     * DECLINED: the user declined the offer from this seller
     */
    public static enum BuyerStatus {
        OPEN, CLOSED, ACCEPTED, DECLINED
    }

    /**
     * OFFERED: seller extended the offer
     * ACCEPTED: the user accepted the offer and proposed a meeting & return time/location & the seller has accepted
     * WITHDRAWN: seller withdrew the offer (couldn't agree on price, item became unavailable...)
     */
    public static enum SellerStatus {
        OFFERED, ACCEPTED, WITHDRAWN
    }


    /**
     * PENDING: the seller status is 'offered' and the buyer status is either 'open' or 'accepted'
     * ACCEPTED: both the buy and seller status is accepted, a transaction should now exist for the request
     * CLOSED: either the buyer declined the request, the buyer closed the request, or the seller withdrew the request
     */
    public static enum Status {
        PENDING, ACCEPTED, CLOSED
    }

    public Boolean getInappropriate() {
        return inappropriate;
    }

    public void setInappropriate(Boolean inappropriate) {
        this.inappropriate = inappropriate;
    }

    public Boolean getIsOfferToBuyOrRent() {
        return isOfferToBuyOrRent;
    }

    public void setIsOfferToBuyOrRent(Boolean offerToBuyOrRent) {
        isOfferToBuyOrRent = offerToBuyOrRent;
    }
}
