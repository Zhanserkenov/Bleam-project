package kz.kbtu.sf.botforbusiness.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import kz.kbtu.sf.botforbusiness.model.*;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import kz.kbtu.sf.botforbusiness.service.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseRedisListener {

    private final BotKnowledgeService botKnowledgeService;
    private final SessionService sessionService;
    private final MessageService messageService;
    private final GeminiService geminiService;
    private final GPTService gptService;
    private final UserRepository userRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, List<String>> buffer = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    protected BaseRedisListener(BotKnowledgeService botKnowledgeService, SessionService sessionService, MessageService messageService, GeminiService geminiService, GPTService gptService, UserRepository userRepository) {
        this.botKnowledgeService = botKnowledgeService;
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.geminiService = geminiService;
        this.gptService = gptService;
        this.userRepository = userRepository;
    }

    protected void handleMessage(String messageJson, PlatformType platform, RedisResponseSender sender) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(messageJson);
            Long userId = json.get("userId").asLong();
            String chatUserId = json.get("chatUserId").asText();
            String message = json.get("message").asText();

            String key = userId + ":" + chatUserId;

            buffer.computeIfAbsent(key, k -> new ArrayList<>()).add(message);

            ScheduledFuture<?> prev = tasks.get(key);
            if (prev != null) {
                prev.cancel(false);
            }

            int delay = 1 + new Random().nextInt(11);
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                sendAggregatedReply(key, platform, sender, userId, chatUserId);
            }, delay, TimeUnit.SECONDS);

            tasks.put(key, future);

        } catch (Exception e) {
            log.error("❌ Error buffering Kafka message", e);
        }
    }

    private void sendAggregatedReply(String key, PlatformType platform, RedisResponseSender sender, Long userId, String chatUserId) {
        try {
            List<String> msgs = buffer.remove(key);
            tasks.remove(key);

            if (msgs == null || msgs.isEmpty()) {
                log.warn("sendAggregatedReply: msgs is null or empty for key={}", key);
                return;
            }

            String combined = msgs.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(". "));

            String knowledge = botKnowledgeService
                    .getKnowledgeContentByUserId(userId)
                    .filter(s -> !s.isBlank())
                    .orElse("Ты — дружелюбный помощник. Отвечай кратко, по делу и в живом человеческом стиле.");

            Session session = sessionService.getOrCreateSession(userId, chatUserId, platform);

            for (String m : msgs) {
                messageService.saveMessage(session, m, SenderType.USER);
            }

            String history = messageService.buildChatHistory(session.getId());

            String systemPrompt = knowledge + " " +
                    "Если пользователь здоровается — поприветствуй его уместно. " +
                    "Если нет — продолжай разговор по теме. " +
                    "Не добавляй префиксы ('Bot:', 'Ответ:'). " +
                    "Пиши только по сути, избегая лишней воды.";

            String userPrompt =
                    "--- История переписки ---\n" +
                            history + "\n\n" +
                            "Пользователь сейчас написал: \"" + combined + "\"";

            User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

            String reply;
            if (user.getAiModel() == AiModelType.GPT) {
                reply = gptService.getGPTResponse(systemPrompt, userPrompt);
            } else {
                reply = geminiService.getGeminiResponse(systemPrompt + "\n\n" + userPrompt);
            }

            if (reply == null) reply = "";
            messageService.saveMessage(session, reply, SenderType.BOT);
            sender.send(chatUserId, reply, userId);

        } catch (Exception e) {
            log.error("❌ Error sending aggregated reply", e);
        }
    }

    @FunctionalInterface
    public interface RedisResponseSender {
        void send(String chatUserId, String message, Long userId);
    }
}