package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LlmProcess {

    // Optional — chỉ active khi có API key tương ứng
    @Autowired(required = false) private GeminiLlmClient geminiClient;
    @Autowired(required = false) private GroqLlmClient groqClient;

    // Luôn có (mock fallback)
    @Autowired private MockLlmClient mockClient;

    @Autowired private LlmPromptEngine promptEngine;
    @Autowired private LlmOutputValidator outputValidator;

    /**
     * Thử lần lượt: Gemini → Groq → Mock
     * Mỗi client trả null khi thất bại → chuyển sang client tiếp theo.
     */
    public AlertEvent extractAlert(String freeText) {
        String prompt = promptEngine.build(freeText);

        for (LlmClient client : buildChain()) {
            log.info("[LLM] Thử {} ({} chars)", client.modelName(), freeText.length());
            try {
                String output = client.call(prompt);
                AlertEvent event = outputValidator.validate(output);
                if (event != null) {
                    log.info("[LLM] {} → thành công: alertName={} severity={}",
                            client.modelName(), event.getAlertName(), event.getSeverity());
                    return event;
                }
                log.warn("[LLM] {} → output không hợp lệ, thử tiếp.", client.modelName());
            } catch (Exception e) {
                log.warn("[LLM] {} → lỗi: {}, thử tiếp.", client.modelName(), e.getMessage());
            }
        }

        log.warn("[LLM] Tất cả client đều thất bại.");
        return null;
    }

    private List<LlmClient> buildChain() {
        List<LlmClient> chain = new ArrayList<>();
        if (geminiClient != null) chain.add(geminiClient);
        if (groqClient   != null) chain.add(groqClient);
        chain.add(mockClient);
        return chain;
    }
}
