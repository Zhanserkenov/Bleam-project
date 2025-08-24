package kz.kbtu.sf.botforbusiness.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    private SenderType sender;
    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private Session session;

    protected Message() {}

    public Message(String text, SenderType sender) {
        this.text = text;
        this.sender = sender;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId(){
        return id;
    }

    public String getText(){
        return text;
    }

    public SenderType getSender(){
        return sender;
    }

    public LocalDateTime getTimestamp(){
        return timestamp;
    }

    public Session getSession(){
        return session;
    }

    public void setSession(Session session) {
        if (this.session != null) {
            throw new IllegalStateException("Session already set");
        }
        this.session = session;
    }
}
