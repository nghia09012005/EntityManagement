package com.viettelDigitalTalent.EntitiyManagement.normalize.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("AUTHENTICATION")
public class AuthenticationEvent extends BaseEvent {
    private String username;
    private String ipAddress;
    private boolean success;
    private String workstation;
}
