package com.impulsecontrol.lend.service;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Environment;
import com.braintreegateway.FundingDetails;
import com.braintreegateway.FundingRequest;
import com.braintreegateway.IndividualRequest;
import com.braintreegateway.MerchantAccount;
import com.braintreegateway.MerchantAccountRequest;
import com.braintreegateway.PaymentMethod;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.ValidationError;
import com.braintreegateway.WebhookNotification;
import com.impulsecontrol.lend.NearbyUtils;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.exception.*;
import com.impulsecontrol.lend.exception.IllegalArgumentException;
import com.impulsecontrol.lend.firebase.CcsServer;
import com.impulsecontrol.lend.firebase.FirebaseUtils;
import com.impulsecontrol.lend.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Created by kerrk on 10/16/16.
 */
public class BraintreeService {

    private String merchantId;
    private String publicKey;
    private String privateKey;
    private CcsServer ccsServer;
    private static BraintreeGateway gateway;
    private static final Logger LOGGER = LoggerFactory.getLogger(BraintreeService.class);
    private JacksonDBCollection<User, String> userCollection;
    private static final Currency USD = Currency.getInstance("USD");
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;


    public BraintreeService(String merchantId, String publicKey, String privateKey,
                            JacksonDBCollection<User, String> userCollection, CcsServer ccsServer) {
        this.merchantId = merchantId;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.gateway = new BraintreeGateway(Environment.SANDBOX, merchantId, publicKey, privateKey);
        this.userCollection = userCollection;
        this.ccsServer = ccsServer;
    }


    public Result<Transaction> doPayment(User buyer, User seller, com.impulsecontrol.lend.model.Transaction transaction) {
        if (buyer.getCustomerId() == null) {
            String msg = "Could not do payment because user [" + buyer.getId() + "] does not have a customer account.";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        } else if (seller.getMerchantId() == null) {
            String msg = "Could not do payment because user [" + seller.getId() + "] does not have a merchant account.";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        BigDecimal price = BigDecimal.valueOf(transaction.getFinalPrice());
        price = price.setScale(USD.getDefaultFractionDigits(), DEFAULT_ROUNDING);
        BigDecimal fee = BigDecimal.valueOf(transaction.getFinalPrice() * .03 + .30);
        fee = fee.setScale(USD.getDefaultFractionDigits(), DEFAULT_ROUNDING);

        TransactionRequest request = new TransactionRequest()
                .merchantAccountId(seller.getMerchantId())
                .amount(price)
                .customerId(buyer.getCustomerId())
                .serviceFeeAmount(fee)
                .options()
                .submitForSettlement(true)
                .done();

        Result<Transaction> result = gateway.transaction().sale(request);

        if (result.isSuccess()) {
            LOGGER.info("Successfully charged/paid users for transaction[ " + transaction.getId() + "].");
            return result;
        } else {
            String msg = "Unable to charge/pay users for transaction[" + transaction.getId() + "], got error: " +
                    result.getMessage();
            LOGGER.error(msg);
            LOGGER.error(request.toString());
            if (result.getErrors() != null) {
                result.getErrors().getAllValidationErrors().forEach(e -> {
                    LOGGER.error("***braintree attribute: " + e.getAttribute() + "  **error: " + e.getMessage());
                });
            }
            throw new InternalServerException(msg);
        }
    }

    public String getBraintreeClientToken() {
        return gateway.clientToken().generate();
    }

    public MerchantAccount updateMerchantAccount(MerchantAccountRequest request, String merchantId) {
        Result<MerchantAccount> result = gateway.merchantAccount().update(merchantId, request);
        return handleMerchantAccountResult(result, request);
    }

    public MerchantAccount getMerchantAccount(String merchantId) {
        if (merchantId == null) {
            return null;
        }
        return gateway.merchantAccount().find(merchantId);
    }

    public Customer getCustomerAccount(String customerId) {
        if (customerId == null) {
            return null;
        }
        return gateway.customer().find(customerId);
    }

    public PaymentMethod getDefaultPaymentMethod(String customerId) {
        Customer c = getCustomerAccount(customerId);
        if (c == null) {
            return null;
        }
        return c.getDefaultPaymentMethod();
    }

    public MerchantAccount createNewMerchantAccount(MerchantAccountRequest request) {
        request.masterMerchantAccountId(merchantId);
        Result<MerchantAccount> result = gateway.merchantAccount().create(request);
        return handleMerchantAccountResult(result, request);
    }

    public User removeMerchantDestination(User user) {
        MerchantAccount ma = getMerchantAccount(user.getMerchantId());
        if (ma == null) {
            throw new NotFoundException("Could not remove payment destination because user does not have a merchant account");
        }
        MerchantAccountRequest request = new MerchantAccountRequest()
                .funding()
                .destination(null)
                .accountNumber("")
                .routingNumber("")
                .done();

        MerchantAccount merchantAccount = removeFundingDestination(user, request);
        String status = merchantAccount != null && merchantAccount.getStatus() != null ? merchantAccount.getStatus().toString() : null;
        LOGGER.info("after removing destination for user [" + user.getName() + "], merchant status = " + status);
        user.setMerchantStatus(status);
        FundingDetails fd = merchantAccount != null ? merchantAccount.getFundingDetails() : null;
        user.setFundDestination(fd != null ? fd.getDestination() : null);
        user.setRemovedMerchantDestination(true);
        userCollection.save(user);
        if (fd != null) {
            LOGGER.info("after removing destination for user [" + user.getName() + "], funding destination = " + fd.getDestination());
        }
        return user;
    }


    private MerchantAccount removeFundingDestination(User user, MerchantAccountRequest request) {
        Result<MerchantAccount> result = gateway.merchantAccount().update(user.getMerchantId(), request);
        MerchantAccount merchantAccount = result.getTarget();
        return merchantAccount;
    }

    public Customer saveOrUpdateCustomer(CustomerRequest request, String customerId) {
        Result<Customer> result = customerId != null ? gateway.customer().update(customerId, request) :
                gateway.customer().create(request);
        if (result.isSuccess()) {
            Customer customer = result.getTarget();
            LOGGER.info("Successfully created/updated customer account request: " + request.toQueryString());
            return customer;
        } else {
            String msg = "Unable to create/update customer request, got message: " + result.getMessage();
            LOGGER.error(msg);
            LOGGER.error(request.toString());
            if (result.getErrors() != null) {
                result.getErrors().getAllValidationErrors().forEach(e -> {
                    LOGGER.error("***braintree attribute: " + e.getAttribute() + "  **error: " + e.getMessage());
                });
            }
            throw new InternalServerException(msg);
        }
    }

    public boolean validateMerchantStatus(User user) {
        boolean good = false;
        MerchantAccount ma = getMerchantAccount(user.getMerchantId());
        if (ma != null) {
            String status = ma.getStatus().toString();
            user.setMerchantStatus(status);
            userCollection.save(user);
            good = status.toLowerCase().equals("active");
        }
        return good;
    }

    public void handleWebhookResponse(String signature, String payload) {
        LOGGER.info("braintree webhook signature: " + signature);
        LOGGER.info("braintree webhook payload: " + payload);

        if (signature != null && payload != null) {
            WebhookNotification notification = gateway.webhookNotification().parse(signature, payload);
            if (notification.getKind() == WebhookNotification.Kind.SUB_MERCHANT_ACCOUNT_APPROVED ||
                    notification.getKind() == WebhookNotification.Kind.SUB_MERCHANT_ACCOUNT_DECLINED) {
                String merchantId = notification.getMerchantAccount().getId();
                DBObject searchByMerchantId = new BasicDBObject("merchantId", merchantId);
                User user = userCollection.findOne(searchByMerchantId);
                if (user == null) {
                    String msg = "Could not find user associated with merchant id [" + merchantId + "]";
                    LOGGER.error(msg);
                    throw new NotFoundException(msg);
                }
                user.setMerchantStatus(notification.getMerchantAccount().getStatus().toString());
                if (notification.getKind() == WebhookNotification.Kind.SUB_MERCHANT_ACCOUNT_DECLINED) {
                    JSONObject n = new JSONObject();
                    n.put("title", "Merchant Account Declined");
                    String errorMessage = "";
                    for (ValidationError e : notification.getErrors().getAllValidationErrors()) {
                        errorMessage += "**attribute: " + e.getAttribute() + " **error: " + e.getMessage() + "\n";

                    }
                    LOGGER.error("Merchant account declined for user [" + user.getId() + "]: " + errorMessage);
                    n.put("message", errorMessage);
                    n.put("type", "merchant_account_status");
                    user.setMerchantStatusMessage(errorMessage);
                    FirebaseUtils.sendFcmMessage(user, null, n, ccsServer);
                } else {
                    JSONObject n = new JSONObject();
                    n.put("title", "Merchant Account Approved");
                    n.put("message", "You can now create offers and earn money through Nearby!");
                    n.put("type", "merchant_account_status");
                    User recipient = userCollection.findOneById(user.getId());
                    FirebaseUtils.sendFcmMessage(recipient, null, n, ccsServer);
                }
                userCollection.save(user);
            }
        }
    }

    private MerchantAccount handleMerchantAccountResult(Result<MerchantAccount> result, MerchantAccountRequest request) {
        if (result.isSuccess()) {
            MerchantAccount ma = result.getTarget();
            LOGGER.info("Successfully created/updated a merchant account request: " + request.toQueryString());
            return ma;
        } else {
            String msg = "Unable to create/update braintree merchant account request, got message: " + result.getMessage();
            LOGGER.error(msg);
            LOGGER.error(request.toString());
            if (result.getErrors() != null) {
                result.getErrors().getAllValidationErrors().forEach(e -> {
                    LOGGER.error("***braintree attribute: " + e.getAttribute() + "  **error: " + e.getMessage());
                });
            }
            throw new InternalServerException(msg);
        }
    }

    public void setCustomerStatus(User user) {
        if (user.getCustomerId() == null) {
            user.setPaymentSetup(false);
            user.setCustomerStatus("Customer account has not been created. Please add a payment method to your account");
            return;
        }
        Customer customer = gateway.customer().find(user.getCustomerId());
        if (customer == null) {
            user.setPaymentSetup(false);
            user.setCustomerStatus("Customer account has not been created. Please add a payment method to your account");
        }
        boolean hasCreditCard = customer.getCreditCards() != null && customer.getCreditCards().size() > 0;
        boolean hasVenmo = customer.getVenmoAccounts() != null && customer.getVenmoAccounts().size() > 0;
        user.setPaymentSetup(hasCreditCard || hasVenmo);
        if (hasCreditCard || hasVenmo) {
            user.setCustomerStatus("valid");
        } else {
            user.setCustomerStatus("Please add a payment method to your account");
        }
    }

    public User saveOrUpdateMerchantAccount(User user, UserDto userDto) {
        verifyAllParametersPresent(user);
        if (userDto.fundDestination == null) {
            LOGGER.error("Cannot add merchant destination for user" + NearbyUtils.getUserIdString(user) +
                    "because destination is null");
            throw new NotAllowedException("You must select a destination for the funds");
        }
        if (!user.getTosAccepted()) {
            LOGGER.error("Cannot add merchant destination for user" + NearbyUtils.getUserIdString(user) +
                    "because the terms of service has not been accepted");
            throw new NotAllowedException("You must accept the terms of service");
        }
        MerchantAccountRequest braintreeRequest = new MerchantAccountRequest();
        IndividualRequest individualRequest = braintreeRequest.individual();
        individualRequest.firstName(user.getFirstName());
        individualRequest.lastName(user.getLastName());
        individualRequest.email(user.getEmail());
        individualRequest.phone(user.getPhone());
        individualRequest.address()
                .streetAddress(user.getAddress())
                .locality(user.getCity())
                .region(user.getState())
                .postalCode(user.getZip())
                .done();
        individualRequest.dateOfBirth(user.getDateOfBirth());
        individualRequest.done();
        braintreeRequest.tosAccepted(user.getTosAccepted());
        switch (userDto.fundDestination) {
            case "bank":
                user.setFundDestination(MerchantAccount.FundingDestination.BANK);
                break;
            case "email":
                user.setFundDestination(MerchantAccount.FundingDestination.EMAIL);
                break;
            case "mobile_phone":
                user.setFundDestination(MerchantAccount.FundingDestination.MOBILE_PHONE);
                break;
        }
        saveMerchantAccount(user, userDto, braintreeRequest);
        /*if (userDto.paymentMethodNonce != null) {
            saveCustomerAccount(user, dto);
        }*/
        setCustomerStatus(user);
        if (user.getMerchantId() != null) {
            MerchantAccount ma = getMerchantAccount(user.getMerchantId());
            if (ma != null) {
                user.setMerchantStatus(ma.getStatus().toString());
            }
        }
        user.setRemovedMerchantDestination(false);
        userCollection.save(user);
        return user;
    }

    public void verifyAllParametersPresent(User user) {
        String error = "Cannot add fund destination because ";
        List<String> errs = new ArrayList<>();
        if (user.getFirstName().isEmpty()) {
            errs.add("first name");
        }
        if (user.getLastName().isEmpty()) {
            errs.add("last name");
        }
        if (user.getEmail().isEmpty()) {
            errs.add("email");
        }
        if (user.getPhone().isEmpty()) {
            errs.add("phone");
        }
        if (user.getDateOfBirth().isEmpty()) {
            errs.add("date of birth");
        }
        if (user.getAddress().isEmpty()) {
            errs.add("street address");
        }
        if (user.getCity().isEmpty()) {
            errs.add("city");
        }
        if (user.getState().isEmpty()) {
            errs.add("state");
        }
        if (user.getZip().isEmpty()) {
            errs.add("zip");
        }
        if (errs.size() > 0) {
            String errorList = StringUtils.join(errs, ", ");
            error += (errorList + " cannot be empty");
            LOGGER.error("Cannot add destination for user" + NearbyUtils.getUserIdString(user) + error);
            throw new NotAllowedException(error);
        }
    }

    private void saveMerchantAccount(User user, UserDto dto, MerchantAccountRequest braintreeRequest) {
        FundingRequest fundingRequest = braintreeRequest.funding();
        switch (dto.fundDestination) {
            case "bank":
                fundingRequest.destination(MerchantAccount.FundingDestination.BANK);
                if (dto.bankRoutingNumber == null || dto.bankAccountNumber == null) {
                    String msg = "To make bank deposits, you must provide both your routing number and your account number";
                    LOGGER.error(dto.id + " " + msg);
                    throw new com.impulsecontrol.lend.exception.IllegalArgumentException(msg);
                }
                fundingRequest.accountNumber(dto.bankAccountNumber);
                fundingRequest.routingNumber(dto.bankRoutingNumber);
                break;
            case "email":
                fundingRequest.destination(MerchantAccount.FundingDestination.EMAIL);
                if (dto.email == null) {
                    String msg = "You selected [email] as the destination for your Nearby funds, " +
                            "but did not enter an email address!";
                    LOGGER.error(dto.id + " " + msg);
                    throw new IllegalArgumentException(msg);
                }
                fundingRequest.email(dto.email);
                break;
            case "mobile_phone":
                fundingRequest.destination(MerchantAccount.FundingDestination.MOBILE_PHONE);
                if (dto.phone == null) {
                    String msg = "You selected [mobile phone] as the destination for your Nearby funds, " +
                            "but did not enter a phone number!";
                    LOGGER.error(dto.id + " " + msg);
                    throw new IllegalArgumentException(msg);
                }
                fundingRequest.mobilePhone(dto.phone.replace("-", ""));
                break;
        }
        fundingRequest.done();
        LOGGER.info("braintree request: " + braintreeRequest.toQueryString());
        if (user.getMerchantId() != null) {
            MerchantAccount ma = updateMerchantAccount(braintreeRequest, user.getMerchantId());
            user.setMerchantStatus(ma.getStatus().toString());
        } else {
            MerchantAccount ma = createNewMerchantAccount(braintreeRequest);
            user.setMerchantId(ma.getId());
            user.setMerchantStatus(ma.getStatus().toString());
        }

    }
}
