package com.impulsecontrol.lend.service;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Environment;
import com.braintreegateway.Result;
import com.braintreegateway.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulsecontrol.lend.NearbyUtils;
import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.dto.ResponseDto;
import com.impulsecontrol.lend.dto.TransactionDto;
import com.impulsecontrol.lend.exception.BadRequestException;
import com.impulsecontrol.lend.exception.CredentialExpiredException;
import com.impulsecontrol.lend.exception.InternalServerException;
import com.impulsecontrol.lend.exception.UnauthorizedException;
import com.impulsecontrol.lend.firebase.CcsServer;
import com.impulsecontrol.lend.firebase.FirebaseUtils;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.Response;
import com.impulsecontrol.lend.model.Transaction;
import com.impulsecontrol.lend.model.User;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.CredentialException;
import javax.ws.rs.NotAuthorizedException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Created by kerrk on 9/22/16.
 */
public class TransactionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionService.class);

    private JacksonDBCollection<Transaction, String> transactionCollection;

    private JacksonDBCollection<User, String> userCollection;

    private CcsServer ccsServer;


    public TransactionService(JacksonDBCollection<Transaction, String> transactionCollection,
                              JacksonDBCollection<User, String> userCollection, CcsServer ccsServer) {
        this.transactionCollection = transactionCollection;
        this.userCollection = userCollection;
        this.ccsServer = ccsServer;
    }

    public String normalizeCode(String code) {
        return code.replaceAll("-", "");
    }

    public void enterReturnCode(Transaction transaction, Response response, String code) {
        if (transaction.getReturnTime() != null) {
            LOGGER.error("Seller tried to enter return code for transaction [" + transaction.getId() + "] " +
                    "when the return has already occurred");
            throw new BadRequestException("Return has already occurred!");
        }
        if (normalizeCode(transaction.getReturnCode()).equals(normalizeCode(code))) {
            Date currentDate = new Date();
            if (transaction.getReturnCodeExpireDate().after(currentDate)) {
                transaction.setReturned(true);
                transaction.setReturnTime(currentDate);
                transaction.setReturned(true);
                calculatePrice(transaction, response);
            } else {
                LOGGER.error("Transaction [" + transaction.getId() + "]'s return code has expired");
                throw new CredentialExpiredException("This return code has expired. Ask the buyer to generate a new one.");
            }
        } else {
            LOGGER.error("Return code for transaction [" + transaction.getId() + "] is incorrect");
            throw new UnauthorizedException("Return code does not match");
        }
    }

    public void enterExchangeCode(Transaction transaction, Response response, Request request, String code) {
        if (transaction.getExchangeTime() != null) {
            LOGGER.error("Buyer tried to enter exchange code for transaction [" + transaction.getId() + "] " +
                    "when the exchange has already occurred");
            throw new BadRequestException("The exchange already occurred!");
        }
        if (normalizeCode(transaction.getExchangeCode()).equals(normalizeCode(code))) {
            if (transaction.getExchangeCodeExpireDate().after(new Date())) {
                if (!request.getRental()) {
                    calculatePrice(transaction, response);
                }
                transaction.setExchanged(true);
                transaction.setExchangeTime(new Date());
                transaction.setExchanged(true);
                transactionCollection.save(transaction);
                JSONObject notification = new JSONObject();
                notification.put("title", "Exchange Confirmed");
                notification.put("message", "exchange confirmed!");
                notification.put("type", "exchange_confirmed");
                User seller = userCollection.findOneById(response.getSellerId());
                FirebaseUtils.sendFcmMessage(seller, null, notification, ccsServer);
            } else {
                LOGGER.error("Transaction [" + transaction.getId() + "]'s code has expired");
                throw new CredentialExpiredException("This exchange code has expired. Ask the seller to generate a new one.");
            }
        } else {
            LOGGER.error("Exchange code for transaction [" + transaction.getId() + "] is incorrect");
            throw new UnauthorizedException("Exchange code does not match");
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

    public void respondToExchangeOverride(Transaction transaction, TransactionDto dto, Response response,
                                          Boolean isSeller, Boolean isRental) {
        if (!isSeller && !isRental) {
            LOGGER.error("Buyer tried to respond to a return override for transaction [" + transaction.getId() +
                    "] for a non-rental item.");
            throw new BadRequestException("Cannot update override for a non-rental item");
        }
        confirmExchangeDidNotOccur(transaction, isSeller);
        // should not happen
        if (isSeller ? (transaction.getReturnOverride() == null || transaction.getReturnOverride().time == null) :
                (transaction.getExchangeOverride() == null || transaction.getExchangeOverride().time == null)) {
            LOGGER.error((isSeller ? "Seller" : "Buyer") + " tried to accept an invalid override for transaction [" +
                    transaction.getId() + "]");
            throw new BadRequestException("Exchange override does not exist");
        }
        if (isSeller) {
            transaction.getReturnOverride().sellerAccepted = dto.returnOverride.sellerAccepted;
            if (dto.returnOverride.sellerAccepted) {
                transaction.setReturnTime(transaction.getReturnOverride().time);
                transaction.setReturned(true);
            } else {
                dto.returnOverride.declined = true;
            }
            //TODO: send notification to seller to verify price if true...what to do if false?
        } else {
            transaction.getExchangeOverride().buyerAccepted = dto.exchangeOverride.buyerAccepted;
            if (dto.exchangeOverride.buyerAccepted) {
                transaction.setExchangeTime(transaction.getExchangeOverride().time);
                transaction.setExchanged(true);
                calculatePrice(transaction, response);
            } else {
                transaction.getExchangeOverride().declined = true;
                dto.exchangeOverride.declined = true;
            }
            if (!isRental) {
                //TODO: send notification to seller to verify price if true...what to do if false?
            } else {
                //TODO: should we send a notifcation to the seller that the user has verified or declined?
            }
        }
        transactionCollection.save(transaction);

    }

    private void confirmExchangeDidNotOccur(Transaction transaction, Boolean isSeller) {
        if ((isSeller && transaction.getReturned()) || (!isSeller && transaction.getExchanged())) {
            LOGGER.error((isSeller ? "Seller" : "Buyer") + " tried to create an override for transaction [" +
                    transaction.getId() + "] but the exchange already occurred");
            throw new BadRequestException("Cannot create an override for an item that has already been " +
                    (isSeller ? "exchanged!" : "returned!"));
        }
    }

    private String generateExchangeCode(Transaction transaction) {
        // the initial exchange already happened, why are you requesting this?
        if (transaction.getExchanged()) {
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
        if (!transaction.getExchanged()) {
            LOGGER.error("Attempted to generate return code for transaction [" + transaction.getId() +
                    "] when the initial exchange wasn't documented!");
            throw new BadRequestException(("Can't generate return code because the exchange was not initiated! " +
                    "Did you exchange this item?"));
        }
        if (transaction.getReturned()) {
            LOGGER.error("The return already occurred for transaction [" + transaction.getId() + "].");
            throw new BadRequestException("You already returned this item at [" + transaction.getReturnTime() + "]");
        }
        return setTransactionCode(transaction, false);
    }

    private String setTransactionCode(Transaction transaction, Boolean initialExchange) {
        String code = NearbyUtils.getUniqueCode();
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
