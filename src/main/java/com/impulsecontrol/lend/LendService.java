package com.impulsecontrol.lend;

import com.impulsecontrol.lend.auth.LendAuthenticator;
import com.impulsecontrol.lend.auth.SecurityProvider;
import com.impulsecontrol.lend.model.Category;
import com.mongodb.DB;
import com.mongodb.Mongo;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.impulsecontrol.lend.resources.UserResource;
import com.impulsecontrol.lend.model.User;
import org.mongojack.JacksonDBCollection;

public class LendService extends Service<LendConfiguration> {

    public static void main(String[] args) throws Exception {
        new LendService().run(new String[] { "server" });
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


        environment.addHealthCheck(new MongoHealthCheck(mongo));

        environment.addResource(new UserResource(userCollection));
        environment.addProvider(new SecurityProvider<User>(new LendAuthenticator(userCollection)));

    }
}
