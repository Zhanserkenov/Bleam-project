package kz.kbtu.sf.botforbusiness.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import kz.kbtu.sf.botforbusiness.model.PlatformType;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import kz.kbtu.sf.botforbusiness.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TelegramRedisListener extends BaseRedisListener {

    private final StringRedisTemplate redisTemplate;
    private final TelegramRedisProducer telegramRedisProducer;
    private final ObjectMapper objectMapper;

    private static final String STREAM_IN = "telegram.incoming";
    private static final String GROUP = "chatbot-group";

    private volatile boolean running = true;
    private Thread pollThread;

    public TelegramRedisListener(BotKnowledgeService botKnowledgeService,
                                 SessionService sessionService,
                                 MessageService messageService,
                                 GeminiService geminiService,
                                 GPTService gptService,
                                 UserRepository userRepository,
                                 StringRedisTemplate redisTemplate,
                                 TelegramRedisProducer telegramRedisProducer,
                                 ObjectMapper objectMapper) {
        super(botKnowledgeService, sessionService, messageService, geminiService, gptService, userRepository);
        this.redisTemplate = redisTemplate;
        this.telegramRedisProducer = telegramRedisProducer;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startListening() {
        // try to create consumer group (safe to ignore if already exists)
        try {
            redisTemplate.opsForStream().createGroup(STREAM_IN, ReadOffset.from("0"), GROUP);
            log.info("Created group {} for stream {}", GROUP, STREAM_IN);
        } catch (Exception e) {
            log.info("Group {} may already exist for stream {} (or redis not available yet): {}", GROUP, STREAM_IN, e.getMessage());
        }

        pollThread = new Thread(this::pollLoop, "telegram-redis-listener");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void pollLoop() {
        String consumerName = "consumer-" + UUID.randomUUID();

        while (running) {
            StreamOperations<String, String, String> ops = null;
            try {
                ops = redisTemplate.opsForStream();

                // Block up to 2 seconds waiting for messages; returns null or empty if none
                List<MapRecord<String, String, String>> messages = ops.read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM_IN, ReadOffset.lastConsumed())
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, String, String> rec : messages) {
                    try {
                        Map<String, String> body = rec.getValue();

                        Map<String, Object> normalized = Map.of(
                                "userId", Long.parseLong(body.get("userId")),
                                "chatUserId", body.get("chatUserId"),
                                "message", body.get("message")
                        );

                        String messageJson = objectMapper.writeValueAsString(normalized);

                        // handle message (uses buffering and aggregation)
                        handleMessage(messageJson, PlatformType.TELEGRAM, telegramRedisProducer::sendMessageToUser);

                        // Acknowledge the message
                        RecordId id = rec.getId();
                        ops.acknowledge(STREAM_IN, GROUP, id);

                    } catch (Exception procEx) {
                        log.error("Error processing single record from stream {} id={}: {}", STREAM_IN, rec.getId(), procEx.getMessage(), procEx);
                        // don't ack so message remains PENDING for reprocessing
                    }
                }

            } catch (IllegalStateException | RedisSystemException ex) {
                // Lettuce / connection related exceptions, connection closed, factory stopped etc.
                log.warn("Redis connection issue in TelegramRedisListener — will retry in 5s: {}", ex.getMessage());
                sleepUnchecked(5000);
                // regenerate consumer name to avoid stale state when reconnecting
                consumerName = "consumer-" + UUID.randomUUID();
            } catch (Exception e) {
                log.error("Unexpected error polling redis stream {}: {}", STREAM_IN, e.getMessage(), e);
                sleepUnchecked(2000);
            }
        }

        log.info("TelegramRedisListener poll loop stopped.");
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
            try {
                pollThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sleepUnchecked(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
