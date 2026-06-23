package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.GraphDedupLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GraphDedupLogRepository extends MongoRepository<GraphDedupLog, String> {}
