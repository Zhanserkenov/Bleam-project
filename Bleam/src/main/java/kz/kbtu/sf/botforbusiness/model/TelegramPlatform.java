package kz.kbtu.sf.botforbusiness.model;

import jakarta.persistence.*;

@Entity
public class TelegramPlatform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = true)
    @JoinColumn(name = "knowledge_id", nullable = true)
    private BotKnowledge knowledge;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User owner;

    private String apiToken;

    @Enumerated(EnumType.STRING)
    private PlatformStatus status;

    protected TelegramPlatform() {}

    public TelegramPlatform(String apiToken) {
        this.apiToken = apiToken;
    }

    public Long getId() {
        return id;
    }

    public String getApiToken() {
        return apiToken;
    }

    public PlatformStatus getPlatformStatus() {
        return status;
    }

    public void setBotKnowledge(BotKnowledge knowledge) {
        if (this.knowledge != null){
            throw new IllegalStateException("Knowledge already set");
        }
        this.knowledge = knowledge;
    }

    public void setOwner(User owner) {
        if(this.owner != null){
            throw new IllegalStateException("Owner already set");
        }
        this.owner = owner;
    }

    public void setPlatformStatus(PlatformStatus status) {
        this.status = status;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
