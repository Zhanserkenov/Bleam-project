package kz.kbtu.sf.botforbusiness.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String token;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User owner;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    protected PasswordResetToken() {}

    public PasswordResetToken(String token, User owner, LocalDateTime expiryDate) {
        this.token = token;
        this.owner = owner;
        this.expiryDate = expiryDate;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public User getOwner() {
        return owner;
    }
}
