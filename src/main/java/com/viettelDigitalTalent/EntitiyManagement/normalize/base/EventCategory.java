package com.viettelDigitalTalent.EntitiyManagement.normalize.base;

public enum EventCategory {
    AUTHENTICATION(3002, 3),   // IAM ( identity and access management)
    PROCESS(1007, 1),          // System Activity
    NETWORK(4001, 4),          // Network Activity
    THREAT(2001, 2);           // Findings / Security Finding

    public final int classUid;
    public final int categoryUid;

    EventCategory(int classUid, int categoryUid) {
        this.classUid    = classUid;
        this.categoryUid = categoryUid;
    }
}
