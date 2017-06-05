package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by kerrk on 7/26/16.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Request implements Serializable {

    private String id;

    @NotNull
    private User user;

    @NotNull
    private String itemName;

    @NotNull
    private GeoJsonPoint location;

    private Date postDate;

    private Date expireDate;

    private Category category;

    @Deprecated
    private Boolean rental;

    private String description;

    private Type type;

    private Status status;

    private String fulfilledByUserId;

    private Boolean inappropriate = false;

    private Boolean duplicate = false;

    public Request() {

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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public GeoJsonPoint getLocation() {
        return location;
    }

    public void setLocation(GeoJsonPoint location) {
        this.location = location;
    }

    public Date getPostDate() {
        return postDate;
    }

    public void setPostDate(Date postDate) {
        this.postDate = postDate;
    }

    public Date getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(Date expireDate) {
        this.expireDate = expireDate;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    @Deprecated
    public Boolean getRental() {
        return rental;
    }

    @Deprecated
    public void setRental(Boolean rental) {
        this.rental = rental;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getFulfilledByUserId() {
        return fulfilledByUserId;
    }

    public void setFulfilledByUserId(String fulfilledByUserId) {
        this.fulfilledByUserId = fulfilledByUserId;
    }

    public static enum Type {
        renting, buying, selling, loaning
    }

    /**
     * OPEN: the request is still open, the buyer has not accepted any offers
     * CLOSED: the request is closed either because the request expired, or the user withdrew the request
     * TRANSACTION_PENDING: an offer has been accepted, the transaction is in progress
     * PROCESSING_PAYMENT: the transaction has been completed, the responder has confirmed the price, and now money is being transferred
     * FULFILLED: the user accepted an offer from someone & it has been exchanged/returned and payment has been submitted
     */
    public static enum Status {
        OPEN, CLOSED, TRANSACTION_PENDING, PROCESSING_PAYMENT, FULFILLED
    }

    public Boolean getInappropriate() {
        return inappropriate;
    }

    public void setInappropriate(Boolean inappropriate) {
        this.inappropriate = inappropriate;
    }

    public Boolean getDuplicate() {
        return duplicate;
    }

    public void setDuplicate(Boolean duplicate) {
        this.duplicate = duplicate;
    }

    @JsonIgnore
    public boolean isRental() {
        return this.getType().equals(Type.renting) || this.getType().equals(Type.loaning);
    }

    @JsonIgnore
    public boolean isInventoryListing() {
        return this.getType().equals(Type.loaning) || this.getType().equals(Type.selling);
    }
}
