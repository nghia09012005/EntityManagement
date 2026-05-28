package com.viettelDigitalTalent.EntitiyManagement.enrichment.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;

public interface EnrichmentProvider {
    void enrich(BaseEvent event);
}