package com.impulsecontrol.lend.service;

import com.impulsecontrol.lend.NearbyUtils;
import com.impulsecontrol.lend.dto.PaymentDto;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.exception.*;
import com.impulsecontrol.lend.firebase.CcsServer;
import com.impulsecontrol.lend.model.Transaction;
import com.impulsecontrol.lend.model.User;
import com.stripe.exception.APIConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.BankAccount;
import com.stripe.model.Card;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.ExternalAccount;
import com.stripe.model.ExternalAccountCollection;
import com.stripe.model.Token;
import com.stripe.net.RequestOptions;
import org.apache.commons.lang3.StringUtils;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by kerrk on 10/16/16.
 */
public class StripeService {

    private String stripeSecretKey;
    private String stripePublishableKey;
    private CcsServer ccsServer;
    private static final Logger LOGGER = LoggerFactory.getLogger(StripeService.class);
    private JacksonDBCollection<User, String> userCollection;
    private static final Currency USD = Currency.getInstance("USD");
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;


    public StripeService(String stripeSecretKey, String stripePublishableKey,
                         JacksonDBCollection<User, String> userCollection, CcsServer ccsServer) {
        this.stripeSecretKey = stripeSecretKey;
        this.stripePublishableKey = stripePublishableKey;
        this.userCollection = userCollection;
        this.ccsServer = ccsServer;
    }

    public PaymentDto getPaymentDetails(User user) {
        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeSecretKey)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            PaymentDto paymentDto = new PaymentDto();
            paymentDto.email = user.getEmail();
            paymentDto.phone = user.getPhone();
            if (StringUtils.isNotEmpty(user.getStripeCustomerId())) {
                Customer customer = Customer.retrieve(user.getStripeCustomerId(), requestOptions);
                if (StringUtils.isNotEmpty(customer.getDefaultSource())) {
                    Card card = (Card) customer.getSources().retrieve(customer.getDefaultSource(), requestOptions);
                    paymentDto.ccMaskedNumber = card != null ? "************" + card.getLast4() : null;
                }
            }
            if (StringUtils.isNotEmpty(user.getStripeManagedAccountId())) {
                Account account = Account.retrieve(user.getStripeManagedAccountId(), requestOptions);
                ExternalAccountCollection eacs = account.getExternalAccounts();
                if (eacs != null) {
                    for (ExternalAccount eac:eacs.getData()) {
                        if (eac.getObject().equals("bank_account")) {
                            BankAccount ba = (BankAccount) eac;
                            paymentDto.routingNumber = ba.getRoutingNumber();
                            paymentDto.bankAccountLast4 = "********" + ba.getLast4();
                            break;
                        }
                    }
                }
            }
            return paymentDto;
        } catch (CardException e) {
            // Since it's a decline, CardException will be caught
            System.out.println("Status is: " + e.getCode());
            System.out.println("Message is: " + e.getMessage());
            String msg = "Card Exception, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (RateLimitException e) {
            // Too many requests made to the API too quickly
            String msg = "Too many requests made to the API too quickly, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (InvalidRequestException e) {
            // Invalid parameters were supplied to Stripe's API
            String msg = "Invalid parameters were supplied to Stripe's API, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (AuthenticationException e) {
            // Authentication with Stripe's API failed
            // (maybe you changed API keys recently)
            String msg = "Authentication with Stripe's API failed, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (APIConnectionException e) {
            // Network communication with Stripe failed
            String msg = "Network communication with Stripe failed, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (StripeException e) {
            // Display a very generic error to the user, and maybe send
            // yourself an email
            String msg = "Could not add Stripe bank account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (Exception e) {
            String msg = "Unable to get user's info from Stripe, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }


    public void doPayment(User buyer, User seller, Transaction transaction) {
        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeSecretKey)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            Account account = Account.retrieve(seller.getStripeManagedAccountId(), requestOptions);
            Customer customer = Customer.retrieve(buyer.getStripeCustomerId(), requestOptions);
            Map<String, Object> tokenParams = new HashMap<String, Object>();
            tokenParams.put("customer", buyer.getStripeCustomerId());
            tokenParams.put("card", customer.getDefaultSource());
            Token token = Token.create(tokenParams, requestOptions);

            Map<String, Object> chargeParams = new HashMap<String, Object>();
            chargeParams.put("amount", transaction.getFinalPrice());
            Double fee = (transaction.getFinalPrice() * .05) + 0.30;
            LOGGER.info("Charging a fee of [" + Double.toString(fee) + "] for transaction [" + transaction.getId() +
                    "] totaling [" + Double.toString(transaction.getFinalPrice()) + "]");
            chargeParams.put("application_fee", fee);
            chargeParams.put("currency", "usd");
            chargeParams.put("source", token);
            chargeParams.put("destination", account.getId());

            Charge charge = Charge.create(chargeParams);
            transaction.setStripeChargeId(charge.getId());
        } catch (Exception e) {
            String msg = "Could not update Stripe managed account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public void updateStripeManagedAccount(User user, UserDto userDto) {
        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeSecretKey)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            Account account = Account.retrieve(user.getStripeManagedAccountId(), requestOptions);
            Map<String, Object> accountParams = updateStripeAccountParams(userDto);
            account.update(accountParams);
        } catch (Exception e) {
            String msg = "Could not update Stripe managed account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }

    }

    public void createStripeManagedAccount(User user, UserDto userDto) {
        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeSecretKey)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            Map<String, Object> accountParams = updateStripeAccountParams(userDto);

            Map<String, Object> tosAcceptanceParams = new HashMap<String, Object>();
            int dateAccepted = (int) (new Date().getTime()/1000);
            dateAccepted--;
            LOGGER.info("date accepted: " + dateAccepted);

            tosAcceptanceParams.put("date", dateAccepted);
            tosAcceptanceParams.put("ip", userDto.tosAcceptIp);

            accountParams.put("tos_acceptance", tosAcceptanceParams);
            Account act = Account.create(accountParams, requestOptions);
            user.setStripeManagedAccountId(act.getId());
            user.setStripePublishableKey(act.getKeys().getPublishable());
            user.setStripeSecretKey(act.getKeys().getSecret());
        } catch (Exception e) {
            String msg = "Could not create Stripe managed account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public void createStripeCustomer(User user, UserDto userDto) {
        if (userDto.stripeCCToken == null) {
            String msg = "Cannot create stripe customer for [" + user.getName() +
                    "] because the credit card token was empty";
            LOGGER.error(msg);
            throw new com.impulsecontrol.lend.exception.IllegalArgumentException(msg);
        }
        Map<String, Object> customerParams = new HashMap<String, Object>();
        customerParams.put("email", user.getEmail());
        customerParams.put("source", userDto.stripeCCToken);
        try {
            Customer customer = Customer.create(customerParams);
            user.setStripeCustomerId(customer.getId());
        } catch (Exception e) {
            String msg = "Could not create Stripe customer, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public void updateStripeCustomer(User user, UserDto userDto) {
        //TODO: do this method
    }

    public boolean canAcceptTransfers(User user) {
        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeSecretKey)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            Account account = Account.retrieve(user.getStripeManagedAccountId(), requestOptions);
            return account.getTransfersEnabled();
        } catch (Exception e) {
            String msg = "Could not fetch Stripe managed account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public boolean hasCustomerAccount(User user) {
        try {
            if (StringUtils.isEmpty(user.getStripeCustomerId())) {
                return false;
            }
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeSecretKey)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            Customer customer = Customer.retrieve(user.getStripeCustomerId(), requestOptions);
            return StringUtils.isNotEmpty(customer.getDefaultSource());
        } catch (Exception e) {
            String msg = "Could not fetch Stripe customer, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    private Map<String, Object> updateStripeAccountParams(UserDto userDto) {
        Map<String, Object> accountParams = new HashMap<String, Object>();
        //right now we will assume everyone using our app is in the US
        accountParams.put("country", "US");
        accountParams.put("email", userDto.email);
        Map<String, Object> legalEntityParams = new HashMap<String, Object>();
        legalEntityParams.put("first_name", userDto.firstName);
        legalEntityParams.put("last_name", userDto.lastName);

        Map<String, Object> addressParams = new HashMap<String, Object>();
        addressParams.put("city", userDto.city);
        addressParams.put("country", "US");
        addressParams.put("line1", userDto.address);
        addressParams.put("line2", userDto.addressLine2);
        addressParams.put("postal_code", userDto.zip);
        addressParams.put("state", userDto.state);

        legalEntityParams.put("personal_address", addressParams);

        Map<String, Object> dobParams = new HashMap<String, Object>();
        String[] dobValues = userDto.dateOfBirth.split("-");
        dobParams.put("day", dobValues[2]);
        dobParams.put("month", dobValues[1]);
        dobParams.put("year", dobValues[0]);

        legalEntityParams.put("dob", dobParams);
        legalEntityParams.put("type", "individual");
        accountParams.put("legal_entity", legalEntityParams);
        return accountParams;
    }

    private void updateStripeBankAccount(Account account, UserDto userDto) {
        Map<String, Object> bankAccount = new HashMap<String, Object>();
        if (StringUtils.isNotEmpty(userDto.bankAccountNumber) && StringUtils.isNotEmpty(userDto.bankRoutingNumber)) {
            bankAccount.put("object", "bank_account");
            bankAccount.put("account_number", userDto.bankAccountNumber);
            bankAccount.put("country", "US");
            bankAccount.put("currency", "usd");
            bankAccount.put("routing_number", userDto.bankRoutingNumber);
        } else if (userDto.stripeBankToken != null) {
            bankAccount.put("external_account", userDto.stripeBankToken);
        } else {
            return;
        }
        try {
            account.getExternalAccounts().create(bankAccount);
        } catch (CardException e) {
            // Since it's a decline, CardException will be caught
            System.out.println("Status is: " + e.getCode());
            System.out.println("Message is: " + e.getMessage());
            String msg = "Card Exception, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (RateLimitException e) {
            // Too many requests made to the API too quickly
            String msg = "Too many requests made to the API too quickly, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (InvalidRequestException e) {
            // Invalid parameters were supplied to Stripe's API
            String msg = "Invalid parameters were supplied to Stripe's API, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (AuthenticationException e) {
            // Authentication with Stripe's API failed
            // (maybe you changed API keys recently)
            String msg = "Authentication with Stripe's API failed, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (APIConnectionException e) {
            // Network communication with Stripe failed
            String msg = "Network communication with Stripe failed, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (StripeException e) {
            // Display a very generic error to the user, and maybe send
            // yourself an email
            String msg = "Could not add Stripe bank account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        } catch (Exception e) {
            String msg = "Could not add Stripe bank account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    private void updateStripeCreditCard(Account account, UserDto userDto) {
        Map<String, Object> cc = new HashMap<String, Object>();
        if (userDto.stripeCCToken != null) {
            cc.put("external_account", userDto.stripeCCToken);
            try {
                account.getExternalAccounts().create(cc);
            } catch (Exception e) {
                String msg = "Could not add Stripe credit card, got error: " + e.getMessage();
                LOGGER.error(msg);
                throw new InternalServerException(msg);
            }
        }
    }


    public void handleWebhookResponse(String signature, String payload) {
        LOGGER.info("braintree webhook signature: " + signature);
        LOGGER.info("braintree webhook payload: " + payload);

        /*if (signature != null && payload != null) {
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
        }*/
    }

    public User saveOrUpdateCustomerAccount(User user, UserDto userDto) {
        verifyAllParametersPresent(user);
        if (!user.getTosAccepted()) {
            LOGGER.error("Cannot add payment destination for user" + NearbyUtils.getUserIdString(user) +
                    "because the terms of service has not been accepted");
            throw new NotAllowedException("You must accept the terms of service");
        }
        if (user.getStripeCustomerId() != null) {
            updateStripeCustomer(user, userDto);
        } else {
            createStripeCustomer(user, userDto);
        }
        userCollection.save(user);
        return user;
    }

    public User saveOrUpdateManagedAccount(User user, UserDto userDto) {
        verifyAllParametersPresent(user);
        if (!user.getTosAccepted()) {
            LOGGER.error("Cannot add payment destination for user" + NearbyUtils.getUserIdString(user) +
                    "because the terms of service has not been accepted");
            throw new NotAllowedException("You must accept the terms of service");
        }
        if (user.getStripeManagedAccountId() != null) {
            updateStripeManagedAccount(user, userDto);
        } else {
            createStripeManagedAccount(user, userDto);
        }
        userCollection.save(user);
        return user;
    }

    public void saveBankAccount(User user, UserDto userDto) {
        if (StringUtils.isEmpty(user.getStripeManagedAccountId())) {
            saveOrUpdateManagedAccount(user, userDto);
        }
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(stripeSecretKey)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        try {
            Account account = Account.retrieve(user.getStripeManagedAccountId(), requestOptions);
            updateStripeBankAccount(account, userDto);
        } catch (Exception e) {
            String msg = "Could not add bank account to Stripe, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }

    }

    public void saveCreditCard(User user, UserDto userDto) {
        if (StringUtils.isEmpty(user.getStripeManagedAccountId())) {
            saveOrUpdateManagedAccount(user, userDto);
        }
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(stripeSecretKey)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        try {
            Account account = Account.retrieve(user.getStripeManagedAccountId(), requestOptions);
            updateStripeCreditCard(account, userDto);
        } catch (Exception e) {
            String msg = "Could not add credit card to Stripe, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }

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
}
