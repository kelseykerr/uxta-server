package com.iuxta.nearby;

import com.iuxta.nearby.auth.CredentialAuthFilter;
import com.iuxta.nearby.auth.NearbyAuthenticator;
import com.iuxta.nearby.auth.NearbyAuthorizer;
import com.iuxta.nearby.firebase.CcsServer;
import com.iuxta.nearby.model.*;
import com.iuxta.nearby.resources.HealthResource;
import com.iuxta.nearby.resources.StripeResource;
import com.iuxta.nearby.resources.CategoriesResource;
import com.iuxta.nearby.resources.RequestsResource;
import com.iuxta.nearby.resources.ResponsesResource;
import com.iuxta.nearby.resources.TransactionsResource;
import com.iuxta.nearby.resources.UserResource;
import com.iuxta.nearby.service.StripeService;
import com.iuxta.nearby.service.RequestService;
import com.iuxta.nearby.service.ResponseService;
import com.iuxta.nearby.service.UserService;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoClientURI;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.mongojack.JacksonDBCollection;

public class NearbyApplication extends Application<NearbyConfiguration> {

    public static void main(String[] args) throws Exception {
        new NearbyApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<NearbyConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));
        bootstrap.addBundle(new SwaggerBundle<NearbyConfiguration>() {
            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(NearbyConfiguration configuration) {
                SwaggerBundleConfiguration sbc = configuration.swaggerBundleConfiguration;
                String [] schemes = new String[1];
                schemes[0] = "https";
                sbc.setSchemes(schemes);
                return sbc;
            }
        });
    }

    @Override
    public void run(NearbyConfiguration config, Environment environment) throws Exception {
        //Mongo mongo = new Mongo(config.mongohost, config.mongoport);
        MongoClientURI mongoClientURI = new MongoClientURI(config.mongoUri);
        Mongo.Holder holder = new Mongo.Holder();
        Mongo mongo = holder.connect(mongoClientURI);

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

        JacksonDBCollection<NearbyAvailableLocations, String> locationsCollection =
                JacksonDBCollection.wrap(db.getCollection("nearbyAvailableLocations"), NearbyAvailableLocations.class, String.class);

        JacksonDBCollection<UnavailableSearches, String> unavailableSearchesCollection =
                JacksonDBCollection.wrap(db.getCollection("unavailableSearches"), UnavailableSearches.class, String.class);

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
        RequestService requestService = new RequestService(categoryCollection, requestCollection, ccsServer, userCollection, responseService, locationsCollection, unavailableSearchesCollection);
        environment.jersey().register(new RequestsResource(requestCollection, requestService, responseCollection, responseService, stripeService));
        environment.jersey().register(new ResponsesResource(requestCollection, responseCollection, responseService, userCollection, stripeService));
        environment.jersey().register(new TransactionsResource(requestCollection, responseCollection, userCollection,
                transactionCollection, ccsServer, stripeService));
        environment.jersey().register(new StripeResource(stripeService));
        NearbyAuthenticator authenticator = new NearbyAuthenticator(userCollection, config.fbAccessToken, config.googleClientIds, ccsServer);
        environment.jersey().register(new AuthDynamicFeature(new CredentialAuthFilter.Builder<User>()
                .setAuthenticator(authenticator)
                .setAuthorizer(new NearbyAuthorizer())
                .setRealm("SUPER SECRET STUFF")
                .buildAuthFilter()));
        environment.jersey().register(new HealthResource());
        environment.jersey().register(new CategoriesResource(categoryCollection));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthValueFactoryProvider.Binder(User.class));
    }

}
