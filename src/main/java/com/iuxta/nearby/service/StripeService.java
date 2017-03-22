package com.iuxta.nearby.service;

import com.iuxta.nearby.NearbyUtils;
import com.iuxta.nearby.dto.PaymentDto;
import com.iuxta.nearby.dto.UserDto;
import com.iuxta.nearby.exception.NotAllowedException;
import com.iuxta.nearby.firebase.CcsServer;
import com.iuxta.nearby.model.Transaction;
import com.iuxta.nearby.model.User;
import com.iuxta.nearby.exception.InternalServerException;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
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

    private RequestOptions getRequestOptions() {
        return RequestOptions.builder()
                .setApiKey(stripeSecretKey)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    public PaymentDto getPaymentDetails(User user) {
        try {
            PaymentDto paymentDto = new PaymentDto();
            paymentDto.email = user.getEmail();
            paymentDto.phone = user.getPhone();
            if (StringUtils.isNotEmpty(user.getStripeCustomerId())) {
                Customer customer = Customer.retrieve(user.getStripeCustomerId(), getRequestOptions());
                if (StringUtils.isNotEmpty(customer.getDefaultSource())) {
                    Card card = (Card) customer.getSources().retrieve(customer.getDefaultSource(), getRequestOptions());
                    paymentDto.ccType = card.getBrand();
                    paymentDto.ccExpDate = card.getExpMonth() + "/" + card.getExpYear();
                    paymentDto.ccMaskedNumber = card != null ? "************" + card.getLast4() : null;
                }
            }
            if (StringUtils.isNotEmpty(user.getStripeManagedAccountId())) {
                Account account = Account.retrieve(user.getStripeManagedAccountId(), getRequestOptions());
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
            String msg = "Could not get user's info from Stripe, got error: " + e.getMessage();
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
            Account account = Account.retrieve(seller.getStripeManagedAccountId(), getRequestOptions());
            Customer customer = Customer.retrieve(buyer.getStripeCustomerId(), getRequestOptions());
            Map<String, Object> tokenParams = new HashMap<String, Object>();
            /*tokenParams.put("customer", buyer.getStripeCustomerId());
            tokenParams.put("card", customer.getDefaultSource());
            Token token = Token.create(tokenParams, getRequestOptions());*/

            BigDecimal price = new BigDecimal(transaction.getFinalPrice());
            price = price.setScale(2, RoundingMode.HALF_UP);
            Double finalPrice =  price.doubleValue();
            // we take 5% + $0.30 - Stripe gets 3.5% +  $0.30
            Double fee = (finalPrice * .05) + 0.30;
            BigDecimal bdFee = new BigDecimal(fee);
            bdFee = bdFee.setScale(2, RoundingMode.HALF_UP);
            fee = bdFee.doubleValue();
            LOGGER.info("Charging a fee of [" + Double.toString(fee) + "] for transaction [" + transaction.getId() +
                    "] totaling [" + Double.toString(finalPrice) + "]");
            //stripe accepts values in cents represented as integers
            finalPrice *= 100;
            fee *= 100;
            Integer stripePrice = finalPrice.intValue();
            Integer stripeFee = fee.intValue();
            Map<String, Object> chargeParams = new HashMap<String, Object>();
            chargeParams.put("amount", stripePrice);
            chargeParams.put("application_fee", stripeFee);
            chargeParams.put("currency", "usd");
            chargeParams.put("customer", buyer.getStripeCustomerId());
            //chargeParams.put("source", token);
            chargeParams.put("destination", account.getId());

            Charge charge = Charge.create(chargeParams, getRequestOptions());
            transaction.setStripeChargeId(charge.getId());
        } catch (Exception e) {
            String msg = "Could not complete charge, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public void updateStripeManagedAccount(User user, UserDto userDto) {
        try {
            Account account = Account.retrieve(user.getStripeManagedAccountId(), getRequestOptions());
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
            Map<String, Object> accountParams = updateStripeAccountParams(userDto);

            Map<String, Object> tosAcceptanceParams = new HashMap<String, Object>();
            int dateAccepted = (int) (user.getTimeTosAccepted().getTime()/1000);
            LOGGER.info("date accepted: " + dateAccepted);

            tosAcceptanceParams.put("date", dateAccepted);
            tosAcceptanceParams.put("ip", user.getTosAcceptIp());

            accountParams.put("tos_acceptance", tosAcceptanceParams);
            Account act = Account.create(accountParams, getRequestOptions());
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
            throw new com.iuxta.nearby.exception.IllegalArgumentException(msg);
        }
        Map<String, Object> customerParams = new HashMap<String, Object>();
        customerParams.put("email", user.getEmail());
        customerParams.put("source", userDto.stripeCCToken);
        try {
            Customer customer = Customer.create(customerParams, getRequestOptions());
            user.setStripeCustomerId(customer.getId());
        } catch (Exception e) {
            String msg = "Could not create Stripe customer, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public void updateStripeCustomer(User user, UserDto userDto) {
        try {
            Customer customer = Customer.retrieve(user.getStripeCustomerId(), getRequestOptions());
            Map<String, Object> customerParams = new HashMap<String, Object>();
            customerParams.put("source", userDto.stripeCCToken);
            customer.update(customerParams, getRequestOptions());
        } catch (Exception e) {
            String msg = "Could not update Stripe customer account, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }

    public boolean canAcceptTransfers(User user) {
        try {
            Account account = Account.retrieve(user.getStripeManagedAccountId(), getRequestOptions());
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
            Customer customer = Customer.retrieve(user.getStripeCustomerId(), getRequestOptions());
            ExternalAccountCollection eac = customer.getSources();
            List<ExternalAccount> eas = eac != null ? eac.getData() : null;
            return eas != null && eas.size() > 0;
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
        accountParams.put("managed", true);
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
            Map<String, Object> tokenParams = new HashMap<String, Object>();
            Map<String, Object> bank_accountParams = new HashMap<String, Object>();
            bank_accountParams.put("country", "US");
            bank_accountParams.put("currency", "usd");
            //bank_accountParams.put("account_holder_name", userDto.fullName);
            bank_accountParams.put("account_holder_type", "individual");
            bank_accountParams.put("routing_number", userDto.bankRoutingNumber);
            bank_accountParams.put("account_number", userDto.bankAccountNumber);
            tokenParams.put("bank_account", bank_accountParams);

            try {
                Token t = Token.create(tokenParams, getRequestOptions());
                LOGGER.info("Stripe bank token: " + t.getId());
                bankAccount.put("external_account", t.getId());
            } catch (Exception e) {
                String msg = "Couldn't generate bank account token, got error: " + e.getMessage();
                LOGGER.error(msg);
                throw new InternalServerException(msg);
            }

            //bankAccount.put("object", "bank_account");
        } else if (userDto.stripeBankToken != null) {
            bankAccount.put("external_account", userDto.stripeBankToken);
        } else {
            return;
        }
        LOGGER.info("Attempting to update bank account for [" + account.getEmail() + "]");
        try {
            account.getExternalAccounts().create(bankAccount, getRequestOptions());
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
            LOGGER.error("StripeException: " + msg);
            throw new InternalServerException(msg);
        }
    }


    public void handleWebhookResponse(String signature, String payload) {
        LOGGER.info("webhook signature: " + signature);
        LOGGER.info("webhook payload: " + payload);

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
        try {
            Account account = Account.retrieve(user.getStripeManagedAccountId(), getRequestOptions());
            LOGGER.info("Account is managed: [" + account.getManaged() + "]");
            updateStripeBankAccount(account, userDto);
        } catch (Exception e) {
            String msg = "Could not add bank account to Stripe, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }

    }

    public void saveCreditCard(User user, UserDto userDto) {
        verifyAllParametersPresent(user);
        if (!user.getTosAccepted()) {
            LOGGER.error("Cannot add payment details for user" + NearbyUtils.getUserIdString(user) +
                    "because the terms of service has not been accepted");
            throw new NotAllowedException("You must accept the terms of service");
        }
        if (user.getStripeCustomerId() != null && StringUtils.isNotEmpty(user.getStripeCustomerId())) {
            updateStripeCustomer(user, userDto);
        } else {
            createStripeCustomer(user, userDto);
            userCollection.save(user);
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
