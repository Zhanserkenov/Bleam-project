package kz.kbtu.sf.botforbusiness.model;

import jakarta.persistence.*;

@Entity
public class WhatsAppPlatform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = true)
    @JoinColumn(name = "knowledge_id", nullable = true)
    private BotKnowledge knowledge;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    private PlatformStatus status;

    public WhatsAppPlatform() {}

    public Long getId(){
        return id;
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
}
