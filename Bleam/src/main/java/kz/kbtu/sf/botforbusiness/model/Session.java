package kz.kbtu.sf.botforbusiness.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner;
    private String chatUserId;

    @Enumerated(EnumType.STRING)
    private PlatformType platformType;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    protected Session() {}

    public Session(String chatUserId, PlatformType platformType) {
        this.chatUserId = chatUserId;
        this.platformType = platformType;
        this.startedAt = LocalDateTime.now();
        this.endedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getChatUserId() {
        return chatUserId;
    }

    public PlatformType getPlatformType() {
        return platformType;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    void updateInteractionTime() {
        this.endedAt = LocalDateTime.now();
    }

    public void setOwner(User owner) {
        if(this.owner != null){
            throw new IllegalStateException("Owner already set");
        }
        this.owner = owner;
    }
}
