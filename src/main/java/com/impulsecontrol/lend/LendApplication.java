package com.impulsecontrol.lend;

import com.impulsecontrol.lend.auth.CredentialAuthFilter;
import com.impulsecontrol.lend.auth.LendAuthenticator;
import com.impulsecontrol.lend.auth.LendAuthorizer;
import com.impulsecontrol.lend.model.Category;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.resources.CategoriesResource;
import com.impulsecontrol.lend.resources.RequestsResource;
import com.impulsecontrol.lend.resources.UserResource;
import com.impulsecontrol.lend.service.RequestService;
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
                return configuration.swaggerBundleConfiguration;
            }
        });
    }

    @Override
    public void run(LendConfiguration configuration, Environment environment) throws Exception {
        Mongo mongo = new Mongo(configuration.mongohost, configuration.mongoport);

        MongoManaged mongoManaged = new MongoManaged(mongo);
        environment.lifecycle().manage(mongoManaged);
        DB db = mongo.getDB(configuration.mongodb);

        JacksonDBCollection<User, String> userCollection =
                JacksonDBCollection.wrap(db.getCollection("user"), User.class, String.class);

        JacksonDBCollection<Category, String> categoryCollection =
                JacksonDBCollection.wrap(db.getCollection("category"), Category.class, String.class);

        JacksonDBCollection<Request, String> requestCollection =
                JacksonDBCollection.wrap(db.getCollection("request"), Request.class, String.class);

        requestCollection.createIndex(new BasicDBObject("location", "2dsphere"));
        environment.healthChecks().register("mongo healthcheck", new MongoHealthCheck(mongo));
        UserService userService = new UserService();
        environment.jersey().register(new UserResource(userCollection, requestCollection, userService));
        RequestService requestService = new RequestService(categoryCollection);
        environment.jersey().register(new RequestsResource(requestCollection, requestService));
        LendAuthenticator authenticator = new LendAuthenticator(userCollection, configuration.fbAccessToken);
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
