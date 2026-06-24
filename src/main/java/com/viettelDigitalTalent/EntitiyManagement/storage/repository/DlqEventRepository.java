package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.DlqEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DlqEventRepository extends MongoRepository<DlqEvent, String> {

    long countBySourceTopic(String sourceTopic);
}
