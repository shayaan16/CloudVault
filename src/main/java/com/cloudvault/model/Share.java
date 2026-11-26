package com.cloudvault.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="vault_shares", indexes={@Index(name="idx_share_owner",columnList="owner_id"), @Index(name="idx_share_recipient",columnList="recipient_id"), @Index(name="idx_share_file",columnList="file_id")})
public class Share {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="file_id",nullable=false) public Long fileId;
    @Column(name="owner_id",nullable=false) public Long ownerId;
    @Column(name="recipient_id") public Long recipientId;
    @Column(unique=true, length=64) public String tokenHash;
    public Instant expiresAt;
    public Instant createdAt = Instant.now();
}
