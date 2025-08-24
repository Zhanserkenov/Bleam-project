package kz.kbtu.sf.botforbusiness.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GPTService {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String MODEL = "gpt-5-nano";
    private static final int MODEL_MAX_TOKENS = 16000;
    private static final int DEFAULT_MAX_COMPLETION_TOKENS = 1024;
    private static final int MIN_COMPLETION_TOKENS = 64;
    private static final int MAX_COMPLETION_TOKENS_CAP = 4096;
    private static final int SAFETY_MARGIN_TOKENS = 256;

    @Value("${openai.api.key}")
    private String openaiApiKey;
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    public String getGPTResponse(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        String combinedInput = (systemPrompt == null ? "" : systemPrompt) + "\n\n" + (userPrompt == null ? "" : userPrompt);
        int promptTokens = estimateTokens(combinedInput);

        int maxCompletionTokens = Math.min(
                DEFAULT_MAX_COMPLETION_TOKENS,
                Math.max(MIN_COMPLETION_TOKENS, MODEL_MAX_TOKENS - promptTokens - SAFETY_MARGIN_TOKENS)
        );
        maxCompletionTokens = Math.min(maxCompletionTokens, MAX_COMPLETION_TOKENS_CAP);

        int attempts = 0;
        final int maxAttempts = 3;
        String lastContent = "";

        while (attempts < maxAttempts) {
            Map<String, Object> systemMessage = Map.of(
                    "role", "system",
                    "content", systemPrompt
            );
            Map<String, Object> userMessage = Map.of(
                    "role", "user",
                    "content", userPrompt
            );

            Map<String, Object> body = new HashMap<>();
            body.put("model", MODEL);
            body.put("messages", List.of(systemMessage, userMessage));
            body.put("max_completion_tokens", maxCompletionTokens);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response;
            try {
                response = restTemplate.postForEntity(OPENAI_CHAT_URL, request, Map.class);
            } catch (Exception ex) {
                return "";
            }

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return "";
            }

            Map<String, Object> respBody = response.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
            Map<String, Object> usage = respBody.get("usage") instanceof Map ? (Map<String,Object>) respBody.get("usage") : null;

            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> first = choices.get(0);
                String finishReason = first.get("finish_reason") != null ? first.get("finish_reason").toString() : null;

                Map<String, Object> message = (Map<String, Object>) first.get("message");
                String content = null;
                if (message != null && message.get("content") != null) {
                    content = message.get("content").toString().trim();
                } else if (first.get("text") != null) {
                    content = first.get("text").toString().trim();
                }

                Integer completionTokens = null;
                try {
                    if (usage != null && usage.get("completion_tokens") != null) {
                        completionTokens = Integer.parseInt(usage.get("completion_tokens").toString());
                    }
                } catch (Exception ignored) {}

                if (content != null && !content.isEmpty()) {
                    return content;
                } else {
                    lastContent = "";
                    if ("length".equalsIgnoreCase(finishReason)) {
                        maxCompletionTokens = Math.min(MAX_COMPLETION_TOKENS_CAP, Math.max(maxCompletionTokens * 2, maxCompletionTokens + 256));
                        attempts++;
                        continue;
                    } else {
                        return "";
                    }
                }
            } else {
                return "";
            }
        }
        return lastContent;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 1;
        int chars = text.length();
        return Math.max(1, (int)Math.ceil(chars / 4.0));
    }
}
