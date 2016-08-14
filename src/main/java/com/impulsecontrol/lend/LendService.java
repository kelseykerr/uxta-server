package com.impulsecontrol.lend;

import com.impulsecontrol.lend.auth.LendAuthenticator;
import com.impulsecontrol.lend.auth.SecurityProvider;
import com.impulsecontrol.lend.model.Category;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.impulsecontrol.lend.resources.RequestsResource;
import com.impulsecontrol.lend.resources.UserResource;
import com.impulsecontrol.lend.service.RequestService;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.Mongo;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import org.mongojack.JacksonDBCollection;

public class LendService extends Service<LendConfiguration> {

    public static void main(String[] args) throws Exception {
        new LendService().run(args);
    }

    @Override
    public void initialize(Bootstrap<LendConfiguration> bootstrap) {
        bootstrap.setName("Lend");
    }

    @Override
    public void run(LendConfiguration configuration, Environment environment) throws Exception {
        Mongo mongo = new Mongo(configuration.mongohost, configuration.mongoport);

        MongoManaged mongoManaged = new MongoManaged(mongo);
        environment.manage(mongoManaged);
        DB db = mongo.getDB(configuration.mongodb);
        JacksonDBCollection<User, String> userCollection =
                JacksonDBCollection.wrap(db.getCollection("user"), User.class, String.class);

        JacksonDBCollection<Category, String> categoryCollection =
                JacksonDBCollection.wrap(db.getCollection("category"), Category.class, String.class);

        JacksonDBCollection<Request, String> requestCollection =
                JacksonDBCollection.wrap(db.getCollection("request"), Request.class, String.class);

        requestCollection.createIndex(new BasicDBObject("location", "2dsphere"));
        environment.addHealthCheck(new MongoHealthCheck(mongo));

        environment.addResource(new UserResource(userCollection, requestCollection));
        RequestService requestService = new RequestService();
        environment.addResource(new RequestsResource(requestCollection, requestService));
        environment.addProvider(new SecurityProvider<User>(new LendAuthenticator(userCollection)));

    }
}
