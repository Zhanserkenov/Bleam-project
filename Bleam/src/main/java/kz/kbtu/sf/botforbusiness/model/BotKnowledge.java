package kz.kbtu.sf.botforbusiness.model;

import jakarta.persistence.*;

@Entity
public class BotKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Lob
    private String content;

    protected BotKnowledge() {}

    public BotKnowledge(SourceType sourceType, String content) {
        this.sourceType = sourceType;
        this.content = content;
    }

    public User getOwner() {
        return owner;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getContent() {
        return content;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setOwner(User owner) {
        if(this.owner != null){
            throw new IllegalStateException("Owner already set");
        }
        this.owner = owner;
    }
}
