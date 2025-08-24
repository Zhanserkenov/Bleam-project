package kz.kbtu.sf.botforbusiness.kafka;

import jakarta.transaction.Transactional;
import kz.kbtu.sf.botforbusiness.model.PlatformType;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import kz.kbtu.sf.botforbusiness.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TelegramKafkaListener extends BaseKafkaListener {

    private final TelegramKafkaProducer telegramKafkaProducer;

    public TelegramKafkaListener(BotKnowledgeService botKnowledgeService, SessionService sessionService, MessageService messageService, GeminiService geminiService, GPTService gptService, UserRepository userRepository, TelegramKafkaProducer telegramKafkaProducer) {
        super(botKnowledgeService, sessionService, messageService, geminiService, gptService, userRepository);
        this.telegramKafkaProducer = telegramKafkaProducer;
    }

    @Transactional
    @KafkaListener(topics = "telegram.incoming", groupId = "chatbot-group")
    public void handleTelegram(String messageJson) {
        handleMessage(messageJson, PlatformType.TELEGRAM, telegramKafkaProducer::sendMessageToUser);
    }
}
