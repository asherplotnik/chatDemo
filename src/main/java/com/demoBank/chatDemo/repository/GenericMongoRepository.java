package com.demoBank.chatDemo.repository;


import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class GenericMongoRepository {
    private final MongoTemplate mongoTemplate;

    public GenericMongoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Document findById(String collection, Object id) {
        Query q = Query.query(Criteria.where("_id").is(id));
        return mongoTemplate.findOne(q, Document.class, collection);
    }
}
