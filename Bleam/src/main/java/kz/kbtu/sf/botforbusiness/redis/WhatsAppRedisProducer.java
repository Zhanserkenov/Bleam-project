package kz.kbtu.sf.botforbusiness.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WhatsAppRedisProducer {

    private final StringRedisTemplate redisTemplate;

    private static final String STREAM_OUT = "whatsapp.outgoing";

    public WhatsAppRedisProducer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void sendMessageToUser(String chatUserId, String message, Long userId) {
        Map<String, String> payload = new HashMap<>();
        payload.put("chatUserId", chatUserId);
        payload.put("message", message);
        payload.put("userId", String.valueOf(userId));

        try {
            MapRecord<String, String, String> record = MapRecord.create(STREAM_OUT, payload);
            RecordId id = redisTemplate.opsForStream().add(record);
            log.debug("Published to '{}' id={}", STREAM_OUT, id);
        } catch (Exception e) {
            log.error("Error publishing to redis stream {}", STREAM_OUT, e);
        }
    }
}
