package kz.kbtu.sf.botforbusiness.service;

import jakarta.transaction.Transactional;
import kz.kbtu.sf.botforbusiness.model.Message;
import kz.kbtu.sf.botforbusiness.model.SenderType;
import kz.kbtu.sf.botforbusiness.model.Session;
import kz.kbtu.sf.botforbusiness.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional
    public void saveMessage(Session session, String text, SenderType sender) {
        Message message = new Message(text, sender);
        message.setSession(session);

        messageRepository.save(message);
    }

    public String buildChatHistory(Long sessionId) {
        List<Message> messages = messageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId);

        StringBuilder history = new StringBuilder();

        for (Message message : messages) {
            String sender = message.getSender() == SenderType.USER ? "Пользователь" : "Бот";
            history.append(sender).append(": ").append(message.getText()).append("\n");
        }

        return history.toString();
    }

    public List<Message> getMessagesForSession(Long sessionId) {
        return messageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId);
    }
}
