package com.impulsecontrol.lend.service;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Environment;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionRequest;
import com.impulsecontrol.lend.model.User;

import java.math.BigDecimal;

/**
 * Created by kerrk on 10/16/16.
 */
public class BraintreeService {

    //TODO: put this in config files and load up and start
    private static BraintreeGateway gateway = new BraintreeGateway(
            Environment.SANDBOX,
            "mbbcxtjdmdztwv8q",
            "v6rxkbqpcdyskcp4",
            "0091ed63f6708b2a048f342c25f52da6"
    );

    public Result<Transaction> doPayment(User user, com.impulsecontrol.lend.model.Transaction transaction) {
        /*TransactionRequest request = new TransactionRequest()
                .amount(BigDecimal.valueOf(transaction.getFinalPrice()))
                .paymentMethodNonce(user.getPaymentMethodNonce())
                .options()
                .submitForSettlement(true)
                .done();

        Result<com.braintreegateway.Transaction> result = gateway.transaction().sale(request);*/
        return null;

    }

    public String getBraintreeClientToken() {
        return gateway.clientToken().generate();
    }
}
