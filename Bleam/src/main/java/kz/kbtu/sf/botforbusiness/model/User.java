package kz.kbtu.sf.botforbusiness.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_model")
    private AiModelType aiModel;

    @Enumerated(EnumType.STRING)
    private Role role;

    protected User() {}

    public User(String email, String password) {
        this.email = email;
        this.password = password;
        this.aiModel = AiModelType.GEMINI;
        this.role = Role.PENDING;
    }

    public Long getId(){
        return id;
    }

    public String getEmail(){
        return email;
    }

    public String getPassword(){
        return password;
    }

    public AiModelType getAiModel() {
        return aiModel;
    }

    public Role getRole() {
        return role;
    }

    public void setEmail(String username) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setAiModel(AiModelType aiModel) {
        this.aiModel = aiModel;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
