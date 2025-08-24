package kz.kbtu.sf.botforbusiness.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import kz.kbtu.sf.botforbusiness.dto.QrPayload;
import kz.kbtu.sf.botforbusiness.model.*;
import kz.kbtu.sf.botforbusiness.repository.BotKnowledgeRepository;
import kz.kbtu.sf.botforbusiness.repository.UserRepository;
import kz.kbtu.sf.botforbusiness.repository.WhatsAppRepository;
import kz.kbtu.sf.botforbusiness.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WhatsAppKafkaListener extends BaseKafkaListener {

    private final WhatsAppKafkaProducer whatsAppKafkaProducer;
    private final WhatsAppRepository whatsAppRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public WhatsAppKafkaListener(BotKnowledgeService botKnowledgeService, SessionService sessionService, MessageService messageService, GeminiService geminiService, GPTService gptService, UserRepository userRepository, WhatsAppKafkaProducer whatsAppKafkaProducer, WhatsAppRepository whatsAppRepository) {
        super(botKnowledgeService, sessionService, messageService, geminiService, gptService, userRepository);
        this.whatsAppKafkaProducer = whatsAppKafkaProducer;
        this.whatsAppRepository = whatsAppRepository;
    }

    @Transactional
    @KafkaListener(topics = "whatsapp.incoming", groupId = "chatbot-group")
    public void handleWhatsApp(String messageJson) {
        handleMessage(messageJson, PlatformType.WHATSAPP, whatsAppKafkaProducer::sendMessageToUser);
    }

    @KafkaListener(topics = "whatsapp_qr", groupId = "chatbot-group")
    public void handleQrCode(String msg) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            QrPayload payload = objectMapper.readValue(msg, QrPayload.class);

            log.info("QR has been received {}: {}", payload.getUserId(), payload.getQrCode());

            messagingTemplate.convertAndSendToUser(
                    payload.getUserId().toString(),
                    "/queue/qr",
                    payload.getQrCode()
            );

        } catch (Exception e) {
            log.error("Error processing WhatsApp QR", e);
        }
    }

    @Transactional
    @KafkaListener(topics = "whatsapp.status", groupId = "chatbot-group")
    public void handlePlatformControl(String msg) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(msg);
            Long userId = json.get("userId").asLong();
            String status = json.get("status").asText();

            if ("DISCONNECTED".equals(status)) {
                WhatsAppPlatform platform = whatsAppRepository.findByOwnerId(userId).orElseThrow(() -> new EntityNotFoundException("WhatsAppPlatform not found"));
                platform.setPlatformStatus(PlatformStatus.INACTIVE);
                whatsAppRepository.save(platform);
            }

            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/wa-status",
                status
            );

            log.info(status);

        } catch (Exception e) {
            log.error("Error processing control message: {}", msg, e);
        }
    }
}
