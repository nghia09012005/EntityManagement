package com.viettelDigitalTalent.EntitiyManagement.normalize.event;

import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProcessEvent extends BaseEvent {
    private String processName;
    private String processPath;
    private String fileHash; // Trường này dùng để làm Enrichment key
    private String commandLine;
}
