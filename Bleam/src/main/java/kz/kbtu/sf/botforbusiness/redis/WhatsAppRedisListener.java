package kz.kbtu.sf.botforbusiness.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityNotFoundException;
import kz.kbtu.sf.botforbusiness.dto.QrPayload;
import kz.kbtu.sf.botforbusiness.model.*;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import kz.kbtu.sf.botforbusiness.repository.WhatsAppRepository;
import kz.kbtu.sf.botforbusiness.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class WhatsAppRedisListener extends BaseRedisListener {

    private final WhatsAppRedisProducer whatsAppRedisProducer;
    private final WhatsAppRepository whatsAppRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String STREAM_IN = "whatsapp.incoming";
    private static final String STREAM_QR = "whatsapp_qr";
    private static final String STREAM_STATUS = "whatsapp.status";
    private static final String GROUP = "chatbot-group";

    private volatile boolean running = true;
    private Thread pollThread;

    public WhatsAppRedisListener(BotKnowledgeService botKnowledgeService,
                                 SessionService sessionService,
                                 MessageService messageService,
                                 GeminiService geminiService,
                                 GPTService gptService,
                                 UserRepository userRepository,
                                 WhatsAppRedisProducer whatsAppRedisProducer,
                                 WhatsAppRepository whatsAppRepository,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 SimpMessagingTemplate messagingTemplate) {
        super(botKnowledgeService, sessionService, messageService, geminiService, gptService, userRepository);
        this.whatsAppRedisProducer = whatsAppRedisProducer;
        this.whatsAppRepository = whatsAppRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void start() {
        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();

        try {
            ops.createGroup(STREAM_IN, ReadOffset.from("0"), GROUP);
            log.info("Group {} created for stream {}", GROUP, STREAM_IN);
        } catch (Exception e) {
            log.info("Group {} for stream {} may already exist or redis unavailable: {}", GROUP, STREAM_IN, e.getMessage());
        }
        try {
            ops.createGroup(STREAM_QR, ReadOffset.from("0"), GROUP);
            log.info("Group {} created for stream {}", GROUP, STREAM_QR);
        } catch (Exception e) {
            log.info("Group {} for stream {} may already exist or redis unavailable: {}", GROUP, STREAM_QR, e.getMessage());
        }
        try {
            ops.createGroup(STREAM_STATUS, ReadOffset.from("0"), GROUP);
            log.info("Group {} created for stream {}", GROUP, STREAM_STATUS);
        } catch (Exception e) {
            log.info("Group {} for stream {} may already exist or redis unavailable: {}", GROUP, STREAM_STATUS, e.getMessage());
        }

        pollThread = new Thread(this::pollLoop, "whatsapp-redis-listener");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void pollLoop() {
        String consumerName = "consumer-" + UUID.randomUUID();

        while (running) {
            StreamOperations<String, String, String> ops = null;
            try {
                ops = redisTemplate.opsForStream();

                List<MapRecord<String, String, String>> messages = ops.read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM_IN, ReadOffset.lastConsumed()),
                        StreamOffset.create(STREAM_QR, ReadOffset.lastConsumed()),
                        StreamOffset.create(STREAM_STATUS, ReadOffset.lastConsumed())
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, String, String> rec : messages) {
                    String stream = rec.getStream();
                    Map<String, String> body = rec.getValue();

                    try {
                        if (STREAM_QR.equals(stream)) {
                            QrPayload payload = objectMapper.convertValue(body, QrPayload.class);
                            log.info("QR has been received {}: {}", payload.getUserId(), payload.getQrCode());

                            messagingTemplate.convertAndSendToUser(
                                    payload.getUserId().toString(),
                                    "/queue/qr",
                                    payload.getQrCode()
                            );

                        } else if (STREAM_STATUS.equals(stream)) {
                            Long userId = Long.parseLong(body.get("userId"));
                            String status = body.get("status");

                            if ("DISCONNECTED".equals(status)) {
                                WhatsAppPlatform platform = whatsAppRepository.findByOwnerId(userId)
                                        .orElseThrow(() -> new EntityNotFoundException("WhatsAppPlatform not found"));
                                platform.setPlatformStatus(PlatformStatus.INACTIVE);
                                whatsAppRepository.save(platform);
                            }

                            messagingTemplate.convertAndSendToUser(
                                    String.valueOf(userId),
                                    "/queue/wa-status",
                                    status
                            );

                            log.info("Processed status: {}", status);

                        } else if (STREAM_IN.equals(stream)) {
                            Map<String, Object> normalized = Map.of(
                                    "userId", Long.parseLong(body.get("userId")),
                                    "chatUserId", body.get("chatUserId"),
                                    "message", body.get("message")
                            );

                            String messageJson = objectMapper.writeValueAsString(normalized);

                            // use buffer/aggregation from BaseRedisListener
                            handleMessage(messageJson, PlatformType.WHATSAPP, whatsAppRedisProducer::sendMessageToUser);
                        }

                        // Acknowledge processed message
                        ops.acknowledge(rec.getStream(), GROUP, rec.getId());

                    } catch (Exception e) {
                        log.error("Error processing record from stream {} id={}: {}", rec.getStream(), rec.getId(), e.getMessage(), e);
                        // Don't ack on failure; leave in PENDING for later reprocessing
                    }
                }

            } catch (IllegalStateException | RedisSystemException ex) {
                log.warn("Redis connection issue in WhatsAppRedisListener — will retry in 5s: {}", ex.getMessage());
                sleepUnchecked(5000);
                consumerName = "consumer-" + UUID.randomUUID();
            } catch (Exception e) {
                log.error("Unexpected error polling redis streams: {}", e.getMessage(), e);
                sleepUnchecked(2000);
            }
        }

        log.info("WhatsAppRedisListener poll loop stopped.");
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