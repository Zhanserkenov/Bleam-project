package kz.kbtu.sf.botforbusiness.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WhatsAppKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public WhatsAppKafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessageToUser(String chatUserId, String message, Long userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("chatUserId", chatUserId);
        response.put("message", message);
        response.put("userId", userId);

        try {
            String payload = new ObjectMapper().writeValueAsString(response);
            kafkaTemplate.send("whatsapp.outgoing", chatUserId, payload);
        } catch (JsonProcessingException e) {
            log.error("Serialization Error", e);
        }
    }
}
