package com.viettelDigitalTalent.EntitiyManagement.normalize.alert;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("ALERT")
public class AlertEvent extends BaseEvent {
    private String alertName;
    private String severity; // HIGH, MEDIUM, LOW
    private String description;

    // Các entity liên quan để thực hiện Correlation
    private String targetIp;
    private String targetUser;
    private String targetHost;
    private String targetDomain;
    private String targetFileHash;
}