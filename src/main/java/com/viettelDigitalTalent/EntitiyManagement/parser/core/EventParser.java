package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;

public interface EventParser {
    // Tất cả các parser cụ thể sẽ phải implement method này
    BaseEvent parse(String rawData);
}
