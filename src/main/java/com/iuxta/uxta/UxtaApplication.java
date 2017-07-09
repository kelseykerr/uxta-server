package com.iuxta.uxta;

import com.iuxta.uxta.auth.CredentialAuthFilter;
import com.iuxta.uxta.auth.NearbyAuthenticator;
import com.iuxta.uxta.auth.NearbyAuthorizer;
import com.iuxta.uxta.firebase.CcsServer;
import com.iuxta.uxta.model.*;
import com.iuxta.uxta.resources.*;
import com.iuxta.uxta.service.*;
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
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.mongojack.JacksonDBCollection;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class UxtaApplication extends Application<UxtaConfiguration> {

    public static void main(String[] args) throws Exception {
        new UxtaApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<UxtaConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));
        bootstrap.addBundle(new SwaggerBundle<UxtaConfiguration>() {
            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(UxtaConfiguration configuration) {
                SwaggerBundleConfiguration sbc = configuration.swaggerBundleConfiguration;
                String [] schemes = new String[1];
                schemes[0] = "https";
                sbc.setSchemes(schemes);
                return sbc;
            }
        });
    }

    @Override
    public void run(UxtaConfiguration config, Environment environment) throws Exception {
        //Mongo mongo = new Mongo(config.mongohost, config.mongoport);
        MongoClientURI mongoClientURI = new MongoClientURI(config.mongoUri);
        Mongo.Holder holder = new Mongo.Holder();
        Mongo mongo = holder.connect(mongoClientURI);

        MongoManaged mongoManaged = new MongoManaged(mongo);
        environment.lifecycle().manage(mongoManaged);
        DB db = mongo.getDB(config.mongodb);

        final FilterRegistration.Dynamic cors =
                environment.servlets().addFilter("CORS", CrossOriginFilter.class);

        // Configure CORS parameters
        cors.setInitParameter("allowedOrigins", "*");
        cors.setInitParameter("allowedHeaders", "X-Requested-With,Content-Type,Accept,Origin");
        cors.setInitParameter("allowedMethods", "OPTIONS,GET,PUT,POST,DELETE,HEAD");

        // Add URL mapping
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

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

        /*JacksonDBCollection<NearbyAvailableLocations, String> locationsCollection =
                JacksonDBCollection.wrap(db.getCollection("nearbyAvailableLocations"), NearbyAvailableLocations.class, String.class);*/

        /*JacksonDBCollection<UnavailableSearches, String> unavailableSearchesCollection =
                JacksonDBCollection.wrap(db.getCollection("unavailableSearches"), UnavailableSearches.class, String.class);*/

        JacksonDBCollection<RequestFlag, String> requestFlagCollection =
                JacksonDBCollection.wrap(db.getCollection("requestFlag"), RequestFlag.class, String.class);

        JacksonDBCollection<UserFlag, String> userFlagCollection =
                JacksonDBCollection.wrap(db.getCollection("userFlag"), UserFlag.class, String.class);

        JacksonDBCollection<ResponseFlag, String> responseFlagCollection =
                JacksonDBCollection.wrap(db.getCollection("responseFlag"), ResponseFlag.class, String.class);

        JacksonDBCollection<SearchTerm, String> searchTermsCollection =
                JacksonDBCollection.wrap(db.getCollection("searchTerms"), SearchTerm.class, String.class);

        JacksonDBCollection<Community, String> communitiesCollection =
                JacksonDBCollection.wrap(db.getCollection("communities"), Community.class, String.class);


        // cloud connection server
        int fcmPort = Integer.parseInt(config.fcmPort);
        if (fcmPort != 5235 && fcmPort != 5236) {
            fcmPort = 5236;
        }
        CcsServer ccsServer = new CcsServer(config.fcmServer, fcmPort, "not sure",
                config.fcmApiKey, config.fcmSenderId);
        ccsServer.connect();

        requestCollection.createIndex(new BasicDBObject("location", "2dsphere"));
        environment.healthChecks().register("mongo healthcheck", new MongoHealthCheck(mongo));
        ResponseService responseService = new ResponseService(requestCollection, responseCollection, userCollection,
                transactionCollection, responseFlagCollection, ccsServer);
        //StripeService stripeService = new StripeService(config.stripeSecretKey, config.stripePublishableKey, userCollection, ccsServer);
        UserService userService = new UserService(responseService, userCollection, userFlagCollection, ccsServer);
        RequestFlagService requestFlagService = new RequestFlagService(requestCollection, requestFlagCollection, userCollection, ccsServer);
        environment.jersey().register(new UserResource(userCollection, requestCollection, userService, responseService));
        RequestService requestService = new RequestService(categoryCollection, requestCollection, ccsServer, userCollection, responseService, searchTermsCollection, communitiesCollection);
        environment.jersey().register(new RequestsResource(requestCollection, requestService, responseCollection, responseService));
        environment.jersey().register(new ResponsesResource(requestCollection, responseCollection, responseService, userCollection));
        environment.jersey().register(new TransactionsResource(requestCollection, responseCollection, userCollection,
                transactionCollection, ccsServer));
        //environment.jersey().register(new StripeResource(stripeService));
        environment.jersey().register(new RequestFlagResource(requestFlagService));
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
