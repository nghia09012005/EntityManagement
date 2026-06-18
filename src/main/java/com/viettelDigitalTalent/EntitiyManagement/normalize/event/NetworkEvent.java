package com.viettelDigitalTalent.EntitiyManagement.normalize.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("NETWORK")
public class NetworkEvent extends BaseEvent {
    private String srcIp;
    private String dstIp;
    private String dstDomain;
    private int dstPort;
}