package kz.kbtu.sf.botforbusiness.redis;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TelegramRedisProducer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STREAM_OUT = "telegram.outgoing";

    public TelegramRedisProducer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendMessageToUser(String chatUserId, String text, Long userId) {
        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("userId", String.valueOf(userId));
        payloadMap.put("chatUserId", chatUserId);
        payloadMap.put("method", "sendMessage");

        try {
            Map<String, String> payloadObj = Map.of("text", text);
            String payloadJson = objectMapper.writeValueAsString(payloadObj);
            payloadMap.put("payload", payloadJson);

            MapRecord<String, String, String> record = MapRecord.create(STREAM_OUT, payloadMap);
            RecordId id = redisTemplate.opsForStream().add(record);

            log.debug("Published to stream '{}' with id={}", STREAM_OUT, id);
        } catch (JsonProcessingException e) {
            log.error("Serialization Error", e);
        } catch (Exception e) {
            log.error("Redis stream publish error", e);
        }
    }
}
