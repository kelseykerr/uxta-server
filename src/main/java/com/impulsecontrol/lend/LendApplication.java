package com.impulsecontrol.lend;

import com.impulsecontrol.lend.auth.CredentialAuthFilter;
import com.impulsecontrol.lend.auth.LendAuthenticator;
import com.impulsecontrol.lend.auth.LendAuthorizer;
import com.impulsecontrol.lend.firebase.CcsServer;
import com.impulsecontrol.lend.model.Category;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.Response;
import com.impulsecontrol.lend.model.Transaction;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.resources.StripeResource;
import com.impulsecontrol.lend.resources.CategoriesResource;
import com.impulsecontrol.lend.resources.RequestsResource;
import com.impulsecontrol.lend.resources.ResponsesResource;
import com.impulsecontrol.lend.resources.TransactionsResource;
import com.impulsecontrol.lend.resources.UserResource;
import com.impulsecontrol.lend.service.StripeService;
import com.impulsecontrol.lend.service.RequestService;
import com.impulsecontrol.lend.service.ResponseService;
import com.impulsecontrol.lend.service.UserService;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.Mongo;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.mongojack.JacksonDBCollection;

public class LendApplication extends Application<LendConfiguration> {

    public static void main(String[] args) throws Exception {
        new LendApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<LendConfiguration> bootstrap) {
        bootstrap.addBundle(new SwaggerBundle<LendConfiguration>() {

            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(LendConfiguration configuration) {
                SwaggerBundleConfiguration sbc = configuration.swaggerBundleConfiguration;
                String [] schemes = new String[1];
                schemes[0] = "https";
                sbc.setSchemes(schemes);
                return sbc;
            }
        });
    }

    @Override
    public void run(LendConfiguration config, Environment environment) throws Exception {
        Mongo mongo = new Mongo(config.mongohost, config.mongoport);

        MongoManaged mongoManaged = new MongoManaged(mongo);
        environment.lifecycle().manage(mongoManaged);
        DB db = mongo.getDB(config.mongodb);

        JacksonDBCollection<User, String> userCollection =
                JacksonDBCollection.wrap(db.getCollection("user"), User.class, String.class);

        JacksonDBCollection<Category, String> categoryCollection =
                JacksonDBCollection.wrap(db.getCollection("category"), Category.class, String.class);

        JacksonDBCollection<Request, String> requestCollection =
                JacksonDBCollection.wrap(db.getCollection("request"), Request.class, String.class);

        JacksonDBCollection<Response, String> responseCollection =
                JacksonDBCollection.wrap(db.getCollection("response"), Response.class, String.class);

        JacksonDBCollection<Transaction, String> transactionCollection =
                JacksonDBCollection.wrap(db.getCollection("transaction"), Transaction.class, String.class);

        // cloud connection server
        CcsServer ccsServer = new CcsServer(config.fcmServer, config.fcmPort, "not sure",
                config.fcmApiKey, config.fcmSenderId);
        ccsServer.connect();

        requestCollection.createIndex(new BasicDBObject("location", "2dsphere"));
        environment.healthChecks().register("mongo healthcheck", new MongoHealthCheck(mongo));
        ResponseService responseService = new ResponseService(requestCollection, responseCollection, userCollection,
                transactionCollection, ccsServer);
        StripeService stripeService = new StripeService(config.stripeSecretKey, config.stripePublishableKey, userCollection, ccsServer);
        UserService userService = new UserService(stripeService);
        environment.jersey().register(new UserResource(userCollection, requestCollection, userService, responseService, stripeService));
        RequestService requestService = new RequestService(categoryCollection, requestCollection, ccsServer, userCollection);
        environment.jersey().register(new RequestsResource(requestCollection, requestService, responseCollection, responseService, stripeService));
        environment.jersey().register(new ResponsesResource(requestCollection, responseCollection, responseService, userCollection, stripeService));
        environment.jersey().register(new TransactionsResource(requestCollection, responseCollection, userCollection,
                transactionCollection, ccsServer, stripeService));
        environment.jersey().register(new StripeResource(stripeService));
        LendAuthenticator authenticator = new LendAuthenticator(userCollection, config.fbAccessToken);
        environment.jersey().register(new AuthDynamicFeature(new CredentialAuthFilter.Builder<User>()
                .setAuthenticator(authenticator)
                .setAuthorizer(new LendAuthorizer())
                .setRealm("SUPER SECRET STUFF")
                .buildAuthFilter()));
        environment.jersey().register(new CategoriesResource(categoryCollection));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder(User.class));
    }

}
