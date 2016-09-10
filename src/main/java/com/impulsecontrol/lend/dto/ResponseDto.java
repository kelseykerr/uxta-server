package com.impulsecontrol.lend.dto;

import com.impulsecontrol.lend.model.Message;
import com.impulsecontrol.lend.model.Response;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kerrk on 9/3/16.
 */
public class ResponseDto {

    /**
     * set by server
     */
    public String id;

    @NotNull
    public String requestId;

    @NotNull
    public String sellerId;

    /**
     * set by server
     */
    public Date responseTime;

    @NotNull
    public Double offerPrice;

    /**
     * options are FLAT, PER_HOUR, PER_DAY
     */
    @NotNull
    public String priceType;

    public String exchangeLocation;

    public String returnLocation;

    public Date exchangeTime;

    public Date returnTime;

    /**
     * options are OPEN, CLOSED, ACCEPTED, DECLINED
     * see Response.java for descriptions about each status
     */
    public String buyerStatus;

    /**
     * options are OFFERED, ACCEPTED, WITHDRAWN
     * see Response.java for descriptions about each status
     */
    public String sellerStatus;

    /**
     *  options are PENDING, ACCEPTED, CLOSED
     *  see Response.java for descriptions about each status
     *  set by server
     */
    public String responseStatus;

    public List<Message> messages;

    public ResponseDto() {

    }

    public ResponseDto(Response r) {
        this.id = r.getId();
        this.requestId = r.getRequestId();
        this.sellerId = r.getSellerId();
        this.responseTime = r.getResponseTime();
        this.offerPrice = r.getOfferPrice();
        this.exchangeLocation = r.getExchangeLocation();
        this.returnLocation = r.getReturnLocation();
        this.priceType = r.getPriceType().toString();
        this.exchangeTime = r.getExchangeTime();
        this.returnTime = r.getReturnTime();
        this.buyerStatus = r.getBuyerStatus() != null ? r.getBuyerStatus().toString() : null;
        this.sellerStatus = r.getSellerStatus() != null ? r.getSellerStatus().toString() : null;
        this.responseStatus = r.getResponseStatus() != null ? r.getResponseStatus().toString() : null;
        this.messages = r.getMessages();
    }

    public static List<ResponseDto> transform(List<Response> responses) {
        return responses.stream()
                .map(r -> new ResponseDto(r)).collect(Collectors.toList());
    }
}
