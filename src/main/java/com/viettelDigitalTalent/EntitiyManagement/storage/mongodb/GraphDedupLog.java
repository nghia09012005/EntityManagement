package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "graph_dedup_log")
public class GraphDedupLog {

    @Id
    private String id;

    /** "User:jdoe" */
    private String fromNode;

    /** "User:jdoe@company.com" */
    private String toNode;

    /** email_prefix | fqdn_shortname */
    private String rule;

    private double confidence;

    private LocalDateTime detectedAt;
}
