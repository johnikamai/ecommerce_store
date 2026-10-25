package com.ecommerce.common.outbox;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="service_outbox")
public class OutboxEvent {
    @Id @Column(length=128) public String id;
    @Column(nullable=false,length=255) public String target;
    @Column(nullable=false,columnDefinition="longtext") public String payload;
    public boolean delivered;
    public int attempts;
    public Instant createdAt=Instant.now();
    public Instant nextAttemptAt=Instant.now();
    @Column(length=120) public String lastError;
}
