package com.viettelDigitalTalent.EntitiyManagement.normalize.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * OCSF base event.
 * Required fields: class_uid, category_uid, activity_id, severity_id, time.
 * uid = event UUID (OCSF "uid").
 * Convenience bridge methods keep backward compat with parsers and services.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent.class, name = "AUTHENTICATION"),
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent.class,       name = "PROCESS"),
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent.class,          name = "ALERT"),
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent.class,       name = "NETWORK")
})
public class BaseEvent {

    // ── OCSF Required ─────────────────────────────────────────────────────────
    @JsonProperty("class_uid")    private int    classUid;
    @JsonProperty("category_uid") private int    categoryUid;
    @JsonProperty("activity_id")  private int    activityId;
    @JsonProperty("severity_id")  private int    severityId;
    /** Epoch milliseconds */
    @JsonProperty("time")         private long   time;

    // ── OCSF Optional ─────────────────────────────────────────────────────────
    @JsonProperty("uid")          private String uid;
    @JsonProperty("severity")     private String severity;
    @JsonProperty("message")      private String message;
    @JsonProperty("status")       private String status;
    @JsonProperty("status_id")    private int    statusId;

    // ── Non-OCSF extensions ───────────────────────────────────────────────────
    private String tenantId;
    private String source;
    @JsonProperty("enrichment_id") private String enrichmentId;
    @JsonProperty("is_enriched")   private boolean enriched;
    @JsonProperty("raw_data")      private Map<String, Object> rawData = new HashMap<>();

    // ── Convenience bridge (not serialized) ───────────────────────────────────

    /** Maps old eventId → OCSF uid */
    @JsonIgnore public String getEventId()          { return uid; }
    public void setEventId(String id)               { this.uid = id; }

    /** Convert LocalDateTime ↔ epoch-ms */
    @JsonIgnore
    public LocalDateTime getTimestamp() {
        return time > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
                : null;
    }
    public void setTimestamp(LocalDateTime ts) {
        this.time = ts != null
                ? ts.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : 0L;
    }

    /** Derive OCSF category string from class_uid */
    @JsonIgnore
    public String getCategory() {
        return switch (classUid) {
            case 3002 -> "AUTHENTICATION";
            case 1007 -> "PROCESS";
            case 4001 -> "NETWORK";
            case 2001 -> "THREAT";
            default   -> "UNKNOWN";
        };
    }

    /** Accepts EventCategory enum name → sets class_uid + category_uid */
    public void setCategory(String cat) {
        if (cat == null) return;
        switch (cat) {
            case "AUTHENTICATION" -> { classUid = 3002; categoryUid = 3; }
            case "PROCESS"        -> { classUid = 1007; categoryUid = 1; }
            case "NETWORK"        -> { classUid = 4001; categoryUid = 4; }
            case "THREAT"         -> { classUid = 2001; categoryUid = 2; }
        }
    }

    /** Map severity string → severity_id */
    public void setSeverityFromString(String s) {
        if (s == null) { severityId = 0; return; }
        severityId = switch (s.toUpperCase()) {
            case "INFO", "INFORMATIONAL" -> 1;
            case "LOW"                   -> 2;
            case "MEDIUM"                -> 3;
            case "HIGH"                  -> 4;
            case "CRITICAL"              -> 5;
            default                      -> 0;
        };
        this.severity = s;
    }
}