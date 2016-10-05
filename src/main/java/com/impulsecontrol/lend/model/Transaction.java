package com.impulsecontrol.lend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by kerrk on 9/22/16.
 */
public class Transaction implements Serializable {

    private String id;

    @NotNull
    private String requestId;

    @NotNull
    private String responseId;

    /**
     * true if the buyer/seller scanned the QR code or entered the correct code or both verified the exchange happened
     */
    private Boolean exchanged = false;

    private Date exchangeTime;

    /**
     * true if the item is being rented AND
     * the buyer/seller scanned the QR code or entered the correct code or both verified the exchange happened
     */
    private Boolean returned = false;

    private Date returnTime;

    private String exchangeCode;

    private String returnCode;

    private Date exchangeCodeExpireDate;

    private Date returnCodeExpireDate;

    /**
     * this is the price calculated by using the price in the offer after the exchange & return have been completed
     */
    private Double calculatedPrice;

    /**
     * this can only be LESS THAN the calculated price...the seller can't jack up the price upon return
     */
    private Double priceOverride;

    /**
     * the price being charged to the buyer (usually this will be either the calculatedPrice or the priceOverride)
     */
    private Double finalPrice;

    /**
     * once the transaction is complete, this shows whether the seller accepted the final price
     */
    private Boolean sellerAccepted;

    /**
     * used only if the users forgot to scan/enter codes on the initial scan
     */
    private ExchangeOverride exchangeOverride;

    /**
     * used only if the users forgot to scan/enter codes on return
     */
    private ExchangeOverride returnOverride;

    private Boolean lostOrStolen;

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public Boolean getExchanged() {
        return exchanged;
    }

    public void setExchanged(Boolean exchanged) {
        this.exchanged = exchanged;
    }

    public Date getExchangeTime() {
        return exchangeTime;
    }

    public void setExchangeTime(Date exchangeTime) {
        this.exchangeTime = exchangeTime;
    }

    public Boolean getReturned() {
        return returned;
    }

    public void setReturned(Boolean returned) {
        this.returned = returned;
    }

    public Date getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(Date returnTime) {
        this.returnTime = returnTime;
    }

    public String getExchangeCode() {
        return exchangeCode;
    }

    public void setExchangeCode(String exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    public Date getExchangeCodeExpireDate() {
        return exchangeCodeExpireDate;
    }

    public void setExchangeCodeExpireDate(Date exchangeCodeExpireDate) {
        this.exchangeCodeExpireDate = exchangeCodeExpireDate;
    }

    public Double getCalculatedPrice() {
        return calculatedPrice;
    }

    public void setCalculatedPrice(Double calculatedPrice) {
        this.calculatedPrice = calculatedPrice;
    }

    public Boolean getSellerAccepted() {
        return sellerAccepted;
    }

    public void setSellerAccepted(Boolean sellerAccepted) {
        this.sellerAccepted = sellerAccepted;
    }

    public ExchangeOverride getExchangeOverride() {
        return exchangeOverride;
    }

    public void setExchangeOverride(ExchangeOverride exchangeOverride) {
        this.exchangeOverride = exchangeOverride;
    }

    public ExchangeOverride getReturnOverride() {
        return returnOverride;
    }

    public void setReturnOverride(ExchangeOverride returnOverride) {
        this.returnOverride = returnOverride;
    }

    public Date getReturnCodeExpireDate() {
        return returnCodeExpireDate;
    }

    public void setReturnCodeExpireDate(Date returnCodeExpireDate) {
        this.returnCodeExpireDate = returnCodeExpireDate;
    }

    public String getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(String returnCode) {
        this.returnCode = returnCode;
    }

    public Double getPriceOverride() {
        return priceOverride;
    }

    public void setPriceOverride(Double priceOverride) {
        this.priceOverride = priceOverride;
    }

    public Boolean getLostOrStolen() {
        return lostOrStolen;
    }

    public void setLostOrStolen(Boolean lostOrStolen) {
        this.lostOrStolen = lostOrStolen;
    }

    public Double getFinalPrice() {
        return finalPrice;
    }

    public void setFinalPrice(Double finalPrice) {
        this.finalPrice = finalPrice;
    }

    /**
     * if the users forgot to scan/enter codes on exchange or return, they can manually enter the times
     * both users will need to accept the exchange/return occurred
     */
    public static class ExchangeOverride {
        public Date time;
        public Boolean buyerAccepted = false;
        public Boolean sellerAccepted = false;
        public Boolean declined = false;

        public ExchangeOverride() {

        }
    }





}
