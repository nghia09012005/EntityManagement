package com.viettelDigitalTalent.EntitiyManagement.llm.core;

public interface LlmClient {
    /** Gửi prompt, trả về raw text response từ model. */
    String call(String prompt);

    String modelName();
}
