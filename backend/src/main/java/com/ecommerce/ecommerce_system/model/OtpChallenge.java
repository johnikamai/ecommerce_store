package com.ecommerce.ecommerce_system.model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
@Entity @Data
public class OtpChallenge {
    @Id @Column(length=320) private String challengeKey;
    private String codeHash;
    private Instant expiresAt;
    private Instant nextIssueAt;
    private int attempts;
    @Version private Long version;
}
