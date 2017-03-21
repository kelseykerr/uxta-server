package com.iuxta.nearby;

import com.mongodb.Mongo;
import io.dropwizard.lifecycle.Managed;


public class MongoManaged implements Managed {
    private Mongo mongo;

    public MongoManaged(Mongo mongo) {
        this.mongo = mongo;
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
        mongo.close();
    }
}
