package com.impulsecontrol.lend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.impulsecontrol.lend.model.Transaction;

import java.util.Date;

/**
 * Created by kerrk on 9/22/16.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDto {
    public String id;

    public String requestId;

    public String responseId;

    public Boolean exchanged;

    public Date exchangeTime;

    public Boolean returned;

    public Date returnTime;

    public String exchangeCode;

    public String returnCode;

    public Date exchangeCodeExpireDate;

    public Date returnCodeExpireDate;

    public Double calculatedPrice;

    public Double priceOverride;

    public Double finalPrice;

    public Boolean sellerAccepted;

    public Transaction.ExchangeOverride exchangeOverride;

    public Transaction.ExchangeOverride returnOverride;

    public TransactionDto() {

    }

    public TransactionDto(Transaction transaction, Boolean seller) {
        this.id = transaction.getId();
        this.requestId = transaction.getRequestId();
        this.responseId = transaction.getResponseId();
        this.exchanged = transaction.getExchanged();
        this.exchangeTime = transaction.getExchangeTime();
        this.returned = transaction.getReturned();
        this.returnTime = transaction.getReturnTime();
        if (seller) {
            this.exchangeCode = transaction.getExchangeCode();
        } else {
            this.returnCode = transaction.getReturnCode();
        }
        this.returnCodeExpireDate = transaction.getReturnCodeExpireDate();
        this.exchangeCodeExpireDate = transaction.getExchangeCodeExpireDate();
        this.calculatedPrice = transaction.getCalculatedPrice();
        this.priceOverride = transaction.getPriceOverride();
        this.sellerAccepted = transaction.getSellerAccepted();
        this.exchangeOverride = transaction.getExchangeOverride();
        this.returnOverride = transaction.getReturnOverride();
        this.finalPrice = transaction.getFinalPrice();

    }
}
