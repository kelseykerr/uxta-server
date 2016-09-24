package com.impulsecontrol.lend.service;

import com.impulsecontrol.lend.dto.TransactionDto;
import com.impulsecontrol.lend.exception.BadRequestException;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.Response;
import com.impulsecontrol.lend.model.Transaction;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotAuthorizedException;
import java.util.Date;
import java.util.UUID;

/**
 * Created by kerrk on 9/22/16.
 */
public class TransactionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionService.class);

    private JacksonDBCollection<Transaction, String> transactionCollection;


    public TransactionService(JacksonDBCollection<Transaction, String> transactionCollection) {
        this.transactionCollection = transactionCollection;
    }

    public void enterReturnCode(Transaction transaction, Response response, String code) {
        if (transaction.getReturnTime() != null) {
            LOGGER.error("Seller tried to enter return code for transaction [" + transaction.getId() + "] " +
                    "when the return has already occurred");
            throw new BadRequestException("Return has already occurred!");
        }
        if (transaction.getReturnCode().equals(code)) {
            Date currentDate = new Date();
            if (transaction.getReturnCodeExpireDate().before(currentDate)) {
                transaction.setReturned(true);
                transaction.setReturnTime(currentDate);
                calculatePrice(transaction, response);
            } else {
                LOGGER.error("Transaction [" + transaction.getId() + "]'s return code has expired");
                throw new NotAuthorizedException("This return code has expired. Ask the buyer to generate a new one.");
            }
        } else {
            LOGGER.error("Return code for transaction [" + transaction.getId() + "] is incorrect");
            throw new NotAuthorizedException("Return code does not match");
        }
    }

    public void enterExchangeCode(Transaction transaction, Response response, Request request, String code) {
        if (transaction.getExchangeTime() != null) {
            LOGGER.error("Buyer tried to enter exchange code for transaction [" + transaction.getId() + "] " +
                    "when the exchange has already occurred");
            throw new BadRequestException("The exchange already occurred!");
        }
        if (transaction.getExchangeCode().equals(code)) {
            if (transaction.getExchangeCodeExpireDate().before(new Date())) {
                if (!request.getRental()) {
                    calculatePrice(transaction, response);
                }
                transaction.setExchanged(true);
                transaction.setExchangeTime(new Date());
                transactionCollection.save(transaction);
            } else {
                LOGGER.error("Transaction [" + transaction.getId() + "]'s code has expired");
                throw new NotAuthorizedException("This exchange code has expired. Ask the seller to generate a new one.");
            }
        } else {
            LOGGER.error("Exchange code for transaction [" + transaction.getId() + "] is incorrect");
            throw new NotAuthorizedException("Exchange code does not match");
        }
    }

    private void calculatePrice(Transaction transaction, Response response) {
        if (response.getPriceType().equals(Response.PriceType.FLAT)) {
            transaction.setCalculatedPrice(response.getOfferPrice());
        } else {
            long secs = (new Date().getTime() - transaction.getExchangeTime().getTime()) / 1000;
            long hours = secs / 3600;
            Double price = response.getPriceType().equals(Response.PriceType.PER_HOUR) ?
                    response.getOfferPrice() * hours : response.getOfferPrice() * (hours/24);
            transaction.setCalculatedPrice(price);
        }
        transactionCollection.save(transaction);
        //TODO: send alerts to seller and buyer to confirm
    }

    public String generateCode(Transaction transaction, Request request, Response response, String userId) {
        // if the user is the seller, generate the initial exchange code
        if (response.getSellerId().equals(userId)) {
            return generateExchangeCode(transaction);
            // if the user is the buyer, generate the return code
        } else if (request.getUser().getId().equals(userId)) {
            return generateReturnCode(transaction, request.getRental());
        } else {
            LOGGER.error("user [" + userId + "] attempted to get access code for transaction [" +
                    transaction.getId() + "].");
            throw new UnauthorizedException("you do not have access to this transaction");
        }
    }

    public void createExchangeOverride(Transaction transaction, TransactionDto dto, Boolean isSeller, Boolean isRental) {
        if (!isSeller && !isRental) {
            LOGGER.error("Buyer tried to create a return override for transaction [" + transaction.getId() +
                    "] for a non-rental item.");
            throw new BadRequestException("Cannot create a return override for a non-rental item");
        }
        confirmExchangeDidNotOccur(transaction, isSeller);
        Transaction.ExchangeOverride override = new Transaction.ExchangeOverride();
        if (isSeller ? (dto.exchangeOverride == null || dto.exchangeOverride.time == null) :
                (dto.returnOverride == null || dto.returnOverride.time == null)) {
            LOGGER.error(isSeller ? "Seller" : "Buyer" + " attempted to create an override for transaction [" +
                    transaction.getId() + "] with an empty override time");
            throw new BadRequestException("You must include a time in the override request!");
        }
        override.time = isSeller ? dto.exchangeOverride.time : dto.returnOverride.time;
        if (isSeller) {
            override.sellerAccepted = true;
            transaction.setExchangeOverride(override);
        } else {
            override.buyerAccepted = true;
            transaction.setReturnOverride(override);
        }
        transactionCollection.save(transaction);
        //TODO: send notification to person who has to confirm override
    }

    public void respondToExchangeOverride(Transaction transaction, TransactionDto dto, Response response, Boolean isSeller, Boolean isRental) {
        if (!isSeller && !isRental) {
            LOGGER.error("Buyer tried to respond to a return override for transaction [" + transaction.getId() +
                    "] for a non-rental item.");
            throw new BadRequestException("Cannot update override for a non-rental item");
        }
        confirmExchangeDidNotOccur(transaction, isSeller);
        // should not happen
        if (isSeller ? (transaction.getReturnOverride() == null || transaction.getReturnOverride().time == null) :
                (transaction.getExchangeOverride() == null || transaction.getExchangeOverride().time == null)) {
            LOGGER.error(isSeller ? "Seller" : "Buyer" + " tried to accept an invalid override for transaction [" +
                    transaction.getId() + "]");
            throw new BadRequestException("Exchange override does not exist");
        }
        if (isSeller) {
            transaction.getReturnOverride().sellerAccepted = dto.returnOverride.sellerAccepted;
            transaction.setReturnTime(transaction.getReturnOverride().time);
            //TODO: send notification to seller to verify price if true...what to do if false?
        } else {
            transaction.getExchangeOverride().buyerAccepted = dto.exchangeOverride.buyerAccepted;
            transaction.setExchangeTime(transaction.getExchangeOverride().time);
            calculatePrice(transaction, response);
            if (!isRental) {
                //TODO: send notification to seller to verify price if true...what to do if false?
            } else {
                //TODO: should we send a notifcation to the seller that the user has verified or declined?
            }
        }
        transactionCollection.save(transaction);

    }

    private void confirmExchangeDidNotOccur(Transaction transaction, Boolean isSeller) {
        if ((isSeller && transaction.getExchangeTime() != null) || (!isSeller && transaction.getReturnTime() != null)) {
            LOGGER.error(isSeller ? "Seller" : "Buyer" + " tried to create an override for transaction [" +
                    transaction.getId() + "] but the exchange already occurred");
            throw new BadRequestException("Cannot create an override for an item that has already been " +
                    (isSeller ? "exchanged!" : "returned!"));
        }
    }

    private String generateExchangeCode(Transaction transaction) {
        // the initial exchange already happened, why are you requesting this?
        if (transaction.getExchangeTime() != null) {
            LOGGER.error("The initial exchange already occurred for transaction [" + transaction.getId() + "].");
            throw new BadRequestException("You already exhanged this item at [" + transaction.getExchangeTime() + "]");
        }
        return setTransactionCode(transaction, true);
    }

    private String generateReturnCode(Transaction transaction, Boolean isRental) {
        // if this item isn't a rental, the code doesn't need to be generated!
        if (!isRental) {
            LOGGER.error("Attempted to generate a return code for an item that was bought for transaction ["
                    + transaction.getId() + "]");
            throw new BadRequestException("Can't generate return code for an item that was bought!");
        }
        // can't generate return code if the item was never exchanged!!
        if (transaction.getExchangeTime() == null) {
            LOGGER.error("Attempted to generate return code for transaction [" + transaction.getId() +
                    "] when the initial exchange wasn't documented!");
            throw new BadRequestException(("Can't generate return code because the exchange was not initiated! " +
                    "Did you exchange this item?"));

        }
        return setTransactionCode(transaction, false);
    }

    private String setTransactionCode(Transaction transaction, Boolean initialExchange) {
        String code = UUID.randomUUID().toString();
        long curTimeInMs = new Date().getTime();
        // 60000 = 1 min in millis, expire in 3 mins
        Date afterAddingMins = new Date(curTimeInMs + (3 * 60000));
        if (initialExchange) {
            transaction.setExchangeCode(code);
            transaction.setExchangeCodeExpireDate(afterAddingMins);
        } else {
            transaction.setReturnCode(code);
            transaction.setReturnCodeExpireDate(afterAddingMins);
        }
        transactionCollection.save(transaction);
        return code;

    }
}
