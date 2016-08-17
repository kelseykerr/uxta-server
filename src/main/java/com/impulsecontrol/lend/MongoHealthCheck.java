package com.impulsecontrol.lend;

import com.codahale.metrics.health.HealthCheck;
import com.mongodb.Mongo;

public class MongoHealthCheck extends HealthCheck {
    private Mongo mongo;

    protected MongoHealthCheck(Mongo mongo) {
        this.mongo = mongo;
    }

    @Override
    protected Result check() throws Exception {
        mongo.getDatabaseNames();
        return Result.healthy();
    }
}
